package com.ffalcon.mercury.android.sdk.demo.ui.wedget

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.WidgetTitleLayoutBinding
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.ui.wiget.BaseMirrorContainerView
import com.ffalcon.mercury.android.sdk.focus.IFocusable
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.touch.TempleActionViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive

/**
 * 标题视图组件
 * 
 * 一个自定义的双目显示标题栏组件，支持三个标题项的焦点导航：
 * - 继承自 BaseMirrorContainerView，自动同步左右眼显示
 * - 实现 IFocusable 接口，参与焦点系统
 * - 使用 FixPosFocusTracker 管理水平方向的焦点导航
 * - 支持动态设置标题文本
 * - 提供标题选择监听器回调
 * - 响应 temple 触摸板事件（滑动切换标题，双击退出）
 * 
 * 适用场景：作为页面顶部的导航栏，提供多个选项卡的切换功能
 */
class TitleView : BaseMirrorContainerView<WidgetTitleLayoutBinding>, IFocusable {
    /** 当前选中的标题位置（0-2） */
    private var selectPos = 1
    
    /** 标题文本数组 */
    private lateinit var titles: Array<String>

    /**
     * 是否有焦点
     * 
     * 当焦点状态改变时，同步更新焦点追踪器的状态，并刷新当前焦点项的视觉效果
     */
    override var hasFocus: Boolean = true
        set(value) {
            field = value
            focusTracker.apply {
                // 同步焦点状态到追踪器
                focusObj.hasFocus = field
                // 获取当前焦点项并重新应用，触发视觉效果更新
                val current = focusHolder.currentFocusItem
                focusHolder.currentFocus(current.target)
            }
        }

    /** 焦点父容器，用于焦点层级管理 */
    override var focusParent: IFocusable? = null
    
    /** 固定位置焦点追踪器，管理标题项的水平导航 */
    lateinit var focusTracker: FixPosFocusTracker

    /** 标题选择监听器，当用户点击标题项时回调 */
    var onTitleSelectListener: OnTitleSelectListener? = null


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)


    /**
     * 初始化视图
     * 
     * 配置三个标题按钮的焦点信息和事件处理：
     * - 为每个标题项注册 FocusInfo，包含事件处理器和焦点变化处理器
     * - 创建 FixPosFocusTracker，配置为水平方向、非连续导航
     * - 设置初始焦点到第一个标题项
     */
    override fun onInit() {
        // 创建焦点持有者，参数 false 表示不支持循环导航
        val focusHolder = FocusHolder(false)
        mBindingPair.setLeft {
            // 配置第一个标题项的焦点信息
            val btn1Info = FocusInfo(
                tvTitle1,
                // 事件处理器：处理 temple 触摸板的输入事件
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            // 单击：回调标题选择监听器，传递位置 0 和视图
                            onTitleSelectListener?.onTitleSelect(0, tvTitle1)
                        }

                        else -> Unit
                    }
                },
                // 焦点变化处理器：更新标题项的视觉效果
                focusChangeHandler = { hasFocus ->
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, tvTitle1, mBindingPair.checkIsLeft(this))
                    }
                }
            )
            // 注册三个标题项的焦点信息
            focusHolder.addFocusTarget(
                btn1Info,
                // 第二个标题项
                FocusInfo(
                    tvTitle2,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击：回调标题选择监听器，传递位置 1 和视图
                                onTitleSelectListener?.onTitleSelect(1, tvTitle2)
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新标题项的视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, tvTitle2, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                // 第三个标题项
                FocusInfo(
                    tvTitle3,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击：回调标题选择监听器，传递位置 2 和视图
                                onTitleSelectListener?.onTitleSelect(2, tvTitle3)
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新标题项的视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, tvTitle3, mBindingPair.checkIsLeft(this))
                        }
                    }
                )
            )
        }

        // 创建固定位置焦点追踪器
        focusTracker =
            FixPosFocusTracker(focusHolder, continuous = false, isVertical = false).apply {
                // 同步焦点状态
                focusObj.hasFocus = hasFocus
            }
        // 设置初始焦点到第一个标题项
        focusHolder.currentFocus(mBindingPair.left.tvTitle1)
    }

    /**
     * 触发焦点视觉效果
     * 
     * 根据焦点状态和组件整体焦点状态设置不同的背景样式：
     * - 获得焦点且组件有焦点：应用 3D 效果 + 设备选中背景
     * - 获得焦点但组件无焦点：使用手机顶部提示背景
     * - 失去焦点：使用黑色背景
     * 
     * @param focus 是否获得焦点
     * @param view 目标 TextView
     * @param isLeft 是否为左眼视图
     */
    private fun triggerFocus(focus: Boolean, view: TextView, isLeft: Boolean) {
        if (focus) {
            if (hasFocus) {
                // 3D effect
                // 应用 3D 视觉效果，增强眼镜显示的立体感
                make3DEffectForSide(view, isLeft, hasFocus)
                // 设置设备选中背景样式
                view.setBackgroundResource(R.drawable.shape_device_selected)
            } else {
                // 设置手机顶部提示背景样式
                view.setBackgroundResource(R.drawable.shape_phone_top_tip)
            }
        } else {
            // 失去焦点：设置黑色背景
            view.setBackgroundColor(context.getColor(R.color.black))
        }
    }

    /**
     * 设置标题文本
     * 
     * 动态更新三个标题项的显示文本
     * 
     * @param titles 标题文本数组，长度必须为 3
     */
    fun setTitles(titles: Array<String>) {
        this.titles = titles

        mBindingPair.updateView {
            tvTitle1.text = titles[0]
            tvTitle2.text = titles[1]
            tvTitle3.text = titles[2]
        }
    }

    /**
     * 监听 temple 触摸板事件
     * 
     * 在 Activity 的生命周期范围内监听 temple 触摸板事件：
     * - 过滤掉已消费的事件
     * - 双击：退出当前 Activity
     * - 其他事件（滑动、单击等）：交给焦点追踪器处理，实现标题项之间的导航
     * 
     * @param act 宿主 Activity，用于获取生命周期范围
     * @param templeActionViewModel temple 动作 ViewModel，提供事件流
     */
    fun watchAction(
        act: AppCompatActivity,
        templeActionViewModel: TempleActionViewModel,
    ) {
        val lifecycleScope = act.lifecycleScope
        // 在 Activity 恢复状态下监听事件
        lifecycleScope.launchWhenResumed {
            // 过滤掉已消费的事件
            templeActionViewModel.state.filter { !it.consumed }.collect { action ->
                // 如果组件无焦点或协程非活跃，则跳过
                if (!hasFocus || !this.isActive) {
                    return@collect
                }
                when (action) {
                    is TempleAction.DoubleClick -> {
                        // 双击退出当前 Activity
                        act.finish()
                    }

                    else -> focusTracker.handleFocusTargetEvent(action)  // 其他事件交由焦点系统处理
                }
            }
        }
    }
}

/**
 * 标题选择监听器接口
 * 
 * 当用户点击标题项时回调此接口
 */
interface OnTitleSelectListener {
    /**
     * 标题被选中时的回调
     * 
     * @param pos 选中标题的位置（0-2）
     * @param titleView 被选中的标题视图
     */
    fun onTitleSelect(pos: Int, titleView: TextView)
}
