package com.ffalcon.mercury.android.sdk.demo.ui.activity.tp

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
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityTpEventBinding
import com.ffalcon.mercury.android.sdk.demo.ui.activity.api.APIActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.camera.CameraActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion.FusionVisionHomeActivity
import com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle.RecycleViewHomeActivity
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.launch

/**
 * 触摸板事件演示Activity
 * 展示如何处理眼镜触摸板的各种手势事件，如点击、双击、滑动等
 */
class TPEventActivity : BaseMirrorActivity<ActivityTpEventBinding>() {
    // 固定位置焦点追踪器，管理焦点导航
    private var fixPosFocusTracker: FixPosFocusTracker? = null
    /**
     * Activity创建时的初始化
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化焦点目标，设置可交互的UI元素
        initFocusTarget()
        // 初始化事件监听，处理眼镜触摸板输入
        initEvent()
    }

    /**
     * 初始化焦点目标
     * 为事件按钮设置焦点管理和事件处理
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，参数false表示不支持循环导航
        val focusHolder = FocusHolder(false)
        // 设置左眼视图绑定
        mBindingPair.setLeft {
            focusHolder.addFocusTarget(
                FocusInfo(
                    btnEvent,
                    eventHandler = { action -> handleAction(action) },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnEvent, mBindingPair.checkIsLeft(this))
                        }
                    }
                )
            )
            focusHolder.currentFocus(mBindingPair.left.btnEvent)
        }
        fixPosFocusTracker = FixPosFocusTracker(focusHolder,true).apply {
            focusObj.reqFocus()
        }
    }

    /**
     * 处理触摸板动作事件
     * 根据不同的手势类型执行相应的操作并显示提示
     * @param action 触摸板动作对象
     */
    private fun handleAction(action: TempleAction) {
        when (action) {
            is TempleAction.LongClick -> {
                // 长按事件
                FToast.show("LongClick")
            }

            is TempleAction.Click -> {
                // 单击事件
                FToast.show("Click")
            }

            is TempleAction.DoubleClick -> {
                // 双击事件，退出当前Activity
                FToast.show("DoubleClick")
                finish()
            }

            is TempleAction.TripleClick -> {
                // 三击事件
                FToast.show("TripleClick")
            }

            is TempleAction.SlideBackward -> {
                // 向后滑动事件
                FToast.show("SlideBackward")
            }

            is TempleAction.SlideForward -> {
                // 向前滑动事件
                FToast.show("SlideForward")
            }

            is TempleAction.SlideUpwards -> {
                // 向上滑动事件
                FToast.show("SlideUpwards")
            }

            is TempleAction.SlideDownwards -> {
                // 向下滑动事件
                FToast.show("SlideDownwards")
            }


            else -> Unit
        }
    }

    /**
     * 初始化事件监听
     * 收集眼镜触摸板的输入事件并交给焦点追踪器处理
     */
    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    // 将触摸板事件交给焦点追踪器处理
                    fixPosFocusTracker?.handleFocusTargetEvent(it)
                }
            }
        }
    }

    /**
     * 触发焦点效果
     * @param hasFocus 是否获得焦点
     * @param view 需要应用效果的视图
     * @param isLeft 是否为左眼视图
     */
    private fun triggerFocus(hasFocus: Boolean, view: View, isLeft: Boolean) {
        // 根据焦点状态设置背景颜色
        view.setBackgroundColor(getColor(if (hasFocus) com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0 else R.color.black))
        // 应用3D视觉效果
        make3DEffectForSide(view, isLeft, hasFocus)
    }
}