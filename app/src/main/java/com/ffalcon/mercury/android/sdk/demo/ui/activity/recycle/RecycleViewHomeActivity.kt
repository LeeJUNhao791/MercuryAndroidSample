package com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityRecyclerHomeBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import kotlinx.coroutines.launch

/**
 * RecyclerView演示主界面Activity
 * 展示不同类型的RecyclerView焦点管理方式，包括固定焦点、移动焦点和动态焦点
 */
class RecycleViewHomeActivity : BaseMirrorActivity<ActivityRecyclerHomeBinding>() {
    // 固定位置焦点追踪器，管理焦点导航
    private var fixPosFocusTracker: FixPosFocusTracker? = null
    // 焦点持有者，管理所有可获取焦点的UI元素，参数false表示不支持循环导航
    private val focusHolder = FocusHolder(false)

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
     * 为三个不同类型的RecyclerView演示按钮设置焦点管理和事件处理
     */
    private fun initFocusTarget() {
        // 设置左眼视图绑定
        mBindingPair.setLeft {
            focusHolder.addFocusTarget(
                FocusInfo(
                    btn3,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 点击固定焦点位置RecyclerView按钮
                                startActivity(
                                    Intent(
                                        this@RecycleViewHomeActivity,
                                        FixedFocusPosRVActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        // 当焦点状态改变时，更新UI显示效果
                        mBindingPair.updateView {
                            triggerFocus(
                                hasFocus,
                                btn3,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                ),
                FocusInfo(
                    btn4,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 点击移动焦点位置RecyclerView按钮
                                startActivity(
                                    Intent(
                                        this@RecycleViewHomeActivity,
                                        MovedFocusPosRVActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        // 当焦点状态改变时，更新UI显示效果
                        mBindingPair.updateView {
                            triggerFocus(
                                hasFocus,
                                btn4,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                ),
                FocusInfo(
                    btnDynamic,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 点击动态焦点目标RecyclerView按钮
                                startActivity(
                                    Intent(
                                        this@RecycleViewHomeActivity,
                                        DynamicFocusTargetActivity::class.java
                                    )
                                )
                            }

                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        // 当焦点状态改变时，更新UI显示效果
                        mBindingPair.updateView {
                            triggerFocus(
                                hasFocus,
                                btnDynamic,
                                mBindingPair.checkIsLeft(this)
                            )
                        }
                    }
                )
            )
            // 设置初始焦点到btn3按钮
            focusHolder.currentFocus(mBindingPair.left.btn3)
        }
        // 创建固定位置焦点追踪器，直接设置焦点状态而不请求焦点
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            // focusObj.reqFocus()
            focusObj.hasFocus = true
        }
    }

    /**
     * 初始化事件监听
     * 收集眼镜触摸板的输入事件并进行处理
     */
    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            // 双击退出当前Activity
                            finish()
                        }

                        else -> fixPosFocusTracker?.handleFocusTargetEvent(it)
                    }
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