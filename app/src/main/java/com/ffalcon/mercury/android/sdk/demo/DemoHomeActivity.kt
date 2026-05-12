package com.ffalcon.mercury.android.sdk.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutDemoHomeBinding
import com.ffalcon.mercury.android.sdk.demo.ui.activity.api.APIHomeActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.audio.AudioRecordPlayActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.camera.CameraHomeActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion.FusionVisionHomeActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.player.VideoPlayActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.qrcode.QrcodeScanningActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle.RecycleViewHomeActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.tp.TPEventActivity
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.launch

/**
 * 演示应用的主界面活动
 *
 * 继承自 BaseMirrorActivity，支持 RayNeo AR 眼镜的双目显示（左眼/右眼）
 * 提供多个功能入口：二维码扫描、语音识别、融合视觉、列表展示、API 演示、相机、触摸事件、视频播放
 */
class DemoHomeActivity : BaseMirrorActivity<LayoutDemoHomeBinding>() {

    /** 固定位置焦点追踪器，用于管理眼镜上的焦点导航 */
    private var fixPosFocusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化焦点目标（可交互的 UI 元素）
        initFocusTarget()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
    }

    /**
     * 初始化焦点目标
     *
     * 为每个可见的功能按钮注册焦点信息，包括：
     * - 点击事件处理：跳转到对应的功能页面
     * - 焦点变化处理：更新按钮的视觉效果（背景色、3D 效果）
     *
     * 使用 FixPosFocusTracker 管理焦点导航，首个可见按钮自动获得初始焦点
     * 注意：只在左眼绑定中注册焦点目标，SDK 焦点追踪器操作单一逻辑焦点列表，
     * 视觉更新通过 updateFocusForBothEyes 手动镜像到双眼
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，用于管理所有可聚焦的视图
        val focusHolder = FocusHolder()

        // Only register focus targets from the left binding. The SDK focus tracker
        // operates on one logical focus list; visual updates are mirrored manually
        // // 设置左眼视图配置（双目显示中，左右眼共享相同的 UI 结构）.
        mBindingPair.setLeft {
            val focusTargets = mutableListOf<FocusInfo>()
            var firstFocusView: View? = null

            fun addVisibleFocusTarget(view: View, onClick: () -> Unit) {
                // 跳过不可见的视图
                if (view.visibility != View.VISIBLE) return
                // 记录第一个可见视图作为初始焦点
                if (firstFocusView == null) firstFocusView = view

                // 创建焦点信息对象，包含事件处理和焦点变化处理
                focusTargets += FocusInfo(
                    view,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> onClick() // 单击时执行跳转
                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：当视图获得/失去焦点时更新视觉效果
                    focusChangeHandler = { hasFocus ->
                        // 更新双眼的焦点视觉效果
                        updateFocusForBothEyes(view.id, hasFocus)
                    }
                )
            }

            // 注册各个功能按钮的焦点目标
            // 1. 二维码扫描
            addVisibleFocusTarget(btnQrcodeScan) {
                startActivity(Intent(this@DemoHomeActivity, QrcodeScanningActivity::class.java))
            }

            // 2. 语音识别
            addVisibleFocusTarget(btnSpeaker) {
                startActivity(Intent(this@DemoHomeActivity, VoiceRecognitionActivity::class.java))
            }

            // 3. 录音与播放
            addVisibleFocusTarget(btnAudioRecord) {
                startActivity(Intent(this@DemoHomeActivity, AudioRecordPlayActivity::class.java))
            }

            addVisibleFocusTarget(btnFusionVision) {
                startActivity(Intent(this@DemoHomeActivity, FusionVisionHomeActivity::class.java))
            }

            addVisibleFocusTarget(btnRecycleView) {
                startActivity(Intent(this@DemoHomeActivity, RecycleViewHomeActivity::class.java))
            }

            addVisibleFocusTarget(btnApi) {
                startActivity(Intent(this@DemoHomeActivity, APIHomeActivity::class.java))
            }

            addVisibleFocusTarget(btnCamera) {
                openCameraHome()
            }

            addVisibleFocusTarget(btnEvent) {
                startActivity(Intent(this@DemoHomeActivity, TPEventActivity::class.java))
            }

            addVisibleFocusTarget(btnPlayer) {
                startActivity(Intent(this@DemoHomeActivity, VideoPlayActivity::class.java))
            }

            if (focusTargets.isNotEmpty()) {
                focusHolder.addFocusTarget(*focusTargets.toTypedArray())
                firstFocusView?.let { focusHolder.currentFocus(it) }
            }
        }

        // 创建并激活固定位置焦点追踪器
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            // 请求初始焦点，使焦点系统开始工作
            focusObj.reqFocus()
        }
    }

    /**
     * 初始化事件监听
     *
     * 监听 temple 触摸板的事件流，在 Activity 处于 RESUMED 状态时持续收集事件：
     * - 双击：退出当前页面（finish）
     * - 其他事件（单击、滑动等）：交给焦点追踪器处理，实现焦点导航
     */
    private fun initEvent() {
        lifecycleScope.launch {
            // 只在 Activity 恢复状态下收集事件，避免后台运行时响应触摸
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 收集 temple 触摸板的状态流
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }

    /**
     * 更新双眼的焦点视觉效果
     *
     * 当焦点变化时，同步更新左眼和右眼的视图样式：
     * - 根据 viewId 查找对应的视图
     * - 调用 triggerFocus 应用背景色和 3D 效果
     *
     * @param viewId 视图 ID
     * @param hasFocus 是否获得焦点
     */
    private fun updateFocusForBothEyes(viewId: Int, hasFocus: Boolean) {
        mBindingPair.updateView {
            // 从根视图查找目标视图，如果不存在则返回
            val target = root.findViewById<View>(viewId) ?: return@updateView
            // 触发焦点视觉效果（背景色 + 3D 效果）
            triggerFocus(hasFocus, target, mBindingPair.checkIsLeft(this))
        }
    }

    /**
     * 打开相机主页
     *
     * 在启动相机 Activity 之前检查相机权限：
     * - 如果未授权，请求相机权限
     * - 如果已授权，直接启动相机页面
     */
    private fun openCameraHome() {
        // 检查是否已获得相机权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 请求相机权限
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }

        FLogger.d("Camera permission already granted")
        // 权限已授予，启动相机主页
        startActivity(Intent(this, CameraHomeActivity::class.java))
    }

    /**
     * 触发焦点视觉效果
     *
     * 当视图获得或失去焦点时调用，更新视图的视觉表现：
     * - 背景色：获得焦点时使用主题色，失去焦点时使用黑色
     * - 3D 效果：为眼镜显示添加立体视觉效果，增强深度感
     *
     * @param hasFocus 是否获得焦点
     * @param view 目标视图
     * @param isLeft 是否为左眼视图（双目显示需要区分左右眼）
     */
    private fun triggerFocus(hasFocus: Boolean, view: View, isLeft: Boolean) {
        // 根据焦点状态设置背景色
        view.setBackgroundColor(
            getColor(
                if (hasFocus) {
                    // 获得焦点：使用 RayNeo 主题色
                    com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0
                } else {
                    // 失去焦点：使用黑色背景
                    R.color.black
                }
            )
        )
        // 应用 3D 视觉效果，增强眼镜显示的立体感
        make3DEffectForSide(view, isLeft, hasFocus)
    }

    /**
     * 权限请求结果回调
     *
     * 处理相机权限请求的结果：
     * - 如果用户授予权限，启动相机主页
     * - 如果用户拒绝权限，记录日志但不执行任何操作
     *
     * @param requestCode 请求码（与 requestPermissions 中的 code 对应）
     * @param permissions 请求的权限数组
     * @param grantResults 权限授予结果数组
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // 检查是否是相机权限请求（requestCode == 1）且结果非空
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            FLogger.d("Camera permission granted")
            // 权限已授予，启动相机主页
            startActivity(Intent(this, CameraHomeActivity::class.java))
        } else if (requestCode == 1) {
            // 权限被拒绝，记录日志
            FLogger.d("Camera permission denied")
        }
    }
}
