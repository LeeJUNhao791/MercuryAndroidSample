package com.ffalcon.mercury.android.sdk.demo.ui.activity.api

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityApiHomeBinding
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityFusionVisionHomeBinding
import com.ffalcon.mercury.android.sdk.demo.ui.activity.DialogActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.FragmentDemoActivity
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import kotlinx.coroutines.launch

/**
 * API 功能主页活动
 * 
 * 提供 API 功能演示的入口菜单，包括：
 * - API 综合演示（GPS、BLE 等）
 * - IMU 传感器演示（加速度计、陀螺仪、磁力计）
 * 
 * 使用焦点系统管理按钮导航，支持 temple 触摸板控制
 */
class APIHomeActivity : BaseMirrorActivity<ActivityApiHomeBinding>() {
    /** 固定位置焦点追踪器，用于管理按钮焦点导航 */
    private var fixPosFocusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化焦点目标（API 和 IMU 按钮）
        initFocusTarget()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
    }

    /**
     * 初始化焦点目标
     * 
     * 为 API 和 IMU 按钮注册焦点信息：
     * - API 按钮：跳转到 API 综合演示页面
     * - IMU 按钮：跳转到 IMU 传感器演示页面
     * - 焦点变化时更新按钮视觉效果（背景色 + 3D 效果）
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，参数 false 表示不允许循环聚焦
        val focusHolder = FocusHolder(false)
        
        // 设置左眼视图配置
        mBindingPair.setLeft {
            // 注册两个按钮的焦点信息
            focusHolder.addFocusTarget(
                // API 按钮焦点配置
                FocusInfo(
                    btnApi,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到 API 综合演示页面
                                startActivity(
                                    Intent(
                                        this@APIHomeActivity,
                                        APIActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(
                                hasFocus,
                                btnApi,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                ),
                // IMU 按钮焦点配置
                FocusInfo(
                    btnImu,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到 IMU 传感器演示页面
                                startActivity(
                                    Intent(
                                        this@APIHomeActivity,
                                        IMUActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnImu, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
            )
            // 设置 API 按钮为当前焦点
            focusHolder.currentFocus(mBindingPair.left.btnApi)
        }
        
        // 创建并激活固定位置焦点追踪器
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            // focusObj.reqFocus()  // 不使用 reqFocus，直接设置 hasFocus
            // 直接设置焦点状态为 true，使焦点系统开始工作
            focusObj.hasFocus = true
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
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            finish()  // 双击返回
                        }

                        else -> fixPosFocusTracker?.handleFocusTargetEvent(it)  // 其他事件交由焦点系统处理
                    }
                }
            }
        }
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
        view.setBackgroundColor(getColor(if (hasFocus) com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0 else R.color.black))
        // 3D effect
        // 应用 3D 视觉效果，增强眼镜显示的立体感
        make3DEffectForSide(view, isLeft, hasFocus)
    }
}