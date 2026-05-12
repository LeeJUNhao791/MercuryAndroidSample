package com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
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
 * 融合视觉功能主页活动
 * 
 * 提供融合视觉相关功能的入口菜单，包括：
 * - 融合视觉 Activity 演示
 * - 镜像容器视图演示
 * - Fragment 演示
 * - Toast 提示演示
 * - Dialog 对话框演示
 * - PAG 动画演示
 * 
 * 使用焦点系统管理按钮导航，支持 temple 触摸板控制
 */
class FusionVisionHomeActivity : BaseMirrorActivity<ActivityFusionVisionHomeBinding>() {
    /** 固定位置焦点追踪器，用于管理按钮焦点导航 */
    private var fixPosFocusTracker: FixPosFocusTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化焦点目标（各个功能按钮）
        initFocusTarget()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
    }

    /**
     * 初始化焦点目标
     * 
     * 为六个功能按钮注册焦点信息：
     * - 融合视觉 Activity、镜像容器视图、Fragment、Toast、Dialog、PAG 动画
     * - 焦点变化时更新按钮视觉效果（背景色 + 3D 效果）
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，参数 false 表示不允许循环聚焦
        val focusHolder = FocusHolder(false)
        
        // 设置左眼视图配置
        mBindingPair.setLeft {
            // 注册六个按钮的焦点信息
            focusHolder.addFocusTarget(
                // 1. 融合视觉 Activity 按钮
                FocusInfo(
                    btnFusionActivity,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到融合视觉 Activity 演示页面
                                startActivity(
                                    Intent(
                                        this@FusionVisionHomeActivity,
                                        FusionVisionActivity::class.java
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
                                btnFusionActivity,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                ),
                // 2. 镜像容器视图按钮
                FocusInfo(
                    btnFusionView,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到镜像容器视图演示页面
                                startActivity(
                                    Intent(
                                        this@FusionVisionHomeActivity,
                                        MirrorContainerViewActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnFusionView, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                // 3. Fragment 演示按钮
                FocusInfo(
                    btnFusionFragment,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到 Fragment 演示页面
                                startActivity(
                                    Intent(
                                        this@FusionVisionHomeActivity,
                                        FragmentDemoActivity::class.java
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
                                btnFusionFragment,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                ),
                // 4. Toast 提示按钮
                FocusInfo(
                    btnToast,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击显示 Toast 提示
                                FToast.show("btnToast click")
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnToast, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                // 5. Dialog 对话框按钮
                FocusInfo(
                    btnDialog,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到 Dialog 对话框演示页面
                                startActivity(
                                    Intent(
                                        this@FusionVisionHomeActivity,
                                        DialogActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnDialog, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                // 6. PAG 动画按钮
                FocusInfo(
                    btnPag,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击跳转到 PAG 动画演示页面
                                startActivity(
                                    Intent(
                                        this@FusionVisionHomeActivity,
                                        PAGActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnPag, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
            )
            // 设置融合视觉 Activity 按钮为当前焦点
            focusHolder.currentFocus(mBindingPair.left.btnFusionActivity)
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
            // 只在 Activity 恢复状态下收集事件
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