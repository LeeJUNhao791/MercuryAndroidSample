package com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityDynamicFocusBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.ui.util.FocusViewHandle
import com.ffalcon.mercury.android.sdk.ui.util.addFocusView
import kotlinx.coroutines.launch

/**
 * 动态焦点目标演示活动
 * 
 * 展示如何在运行时动态添加和移除可聚焦的视图：
 * - 使用 addFocusView 扩展方法动态创建视图并注册到焦点系统
 * - 支持动态添加不同类型的视图（TextView、ImageView、Button）
 * - 支持动态移除已添加的焦点目标
 * - 所有动态视图自动同步到双眼显示
 * 
 * 适用场景：需要根据用户操作或数据变化动态生成 UI 元素的场景
 */
class DynamicFocusTargetActivity : BaseMirrorActivity<ActivityDynamicFocusBinding>() {
    /** 固定位置焦点追踪器，用于管理焦点导航 */
    private var fixPosFocusTracker: FixPosFocusTracker? = null
    
    /** 焦点持有者，参数 true 表示允许循环聚焦 */
    private val focusHolder = FocusHolder(true)
    
    /** 存储所有动态添加的焦点视图句柄 */
    private val focusHandles = mutableListOf<FocusViewHandle<View>>()

    /** 当前选中的动态焦点句柄 */
    private lateinit var currentDynamicFocus: FocusViewHandle<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化焦点目标（添加/移除按钮）
        initFocusTarget()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
    }

    /**
     * 初始化焦点目标
     * 
     * 为两个控制按钮注册焦点信息：
     * - 添加按钮：动态创建新的可聚焦视图
     * - 移除按钮：移除最后添加的动态视图
     */
    private fun initFocusTarget() {
        // btnAdd is already defined in layout file, use directly
        // 设置左眼视图配置
        mBindingPair.setLeft {
            // 注册两个控制按钮的焦点信息
            focusHolder.addFocusTarget(
                // 1. 添加按钮
                FocusInfo(
                    btnAdd,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 单击添加动态焦点目标
                                addDynamicFocus()
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnAdd, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                // 2. 移除按钮
                FocusInfo(
                    btnRemove,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                // 检查当前动态焦点是否在列表中
                                if (focusHandles.contains(currentDynamicFocus)) {
                                    FToast.show("remove dynamic focus target ")
                                    // 清除焦点视图并从界面移除
                                    currentDynamicFocus.clearFocusView()
                                    // 从列表中移除句柄
                                    focusHandles.remove(currentDynamicFocus)
                                    // 如果还有剩余的动态视图，更新当前焦点为最后一个
                                    if (focusHandles.isNotEmpty()) {
                                        currentDynamicFocus = focusHandles.last()
                                    }
                                }
                            }

                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnRemove, mBindingPair.checkIsLeft(this))
                        }
                    }
                )
            )
            // 设置添加按钮为当前焦点
            focusHolder.currentFocus(btnAdd)
        }

        // 创建并激活固定位置焦点追踪器
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            // 直接设置焦点状态为 true，使焦点系统开始工作
            focusObj.hasFocus = true
        }
    }

    /**
     * 动态添加焦点目标
     * 
     * 使用 addFocusView 扩展方法在运行时动态创建视图并注册到焦点系统：
     * - 根据已添加的数量创建不同类型的视图（TextView、ImageView、Button）
     * - 配置布局参数、事件处理和焦点变化回调
     * - 自动同步到双眼显示
     * 
     * 注意：使用 var handle 而不是 val，因为句柄需要在 eventHandler 中使用
     */
    @SuppressLint("SetTextI18n")
    private fun addDynamicFocus() {
        // Extension function API to dynamically add focus View
        // Use mutable reference because handle needs to be used in eventHandler
        // 使用可变引用，因为句柄需要在 eventHandler 中使用
        var handle: FocusViewHandle<View>? = null
        val size = focusHandles.size
        
        // 调用扩展方法动态添加焦点视图
        handle = mBindingPair.addFocusView(
            parent = mBindingPair.left.llParent,  // 父容器
            viewFactory = {
                //todo This is just a demo of dynamically adding different views
                // 根据已添加的数量创建不同类型的视图
                when (size) {
                    1 -> TextView(this@DynamicFocusTargetActivity).apply {
                        text = "dynamic focus target TextView"
                    }

                    2 -> ImageView(this@DynamicFocusTargetActivity).apply {
                        setBackgroundResource(R.mipmap.ic_launcher)
                    }

                    else -> Button(this@DynamicFocusTargetActivity).apply {
                        text = "dynamic focus target"
                        background = null
                    }
                }
            },
            focusHolder = focusHolder,  // 焦点持有者
            focusConfig = {
                // Set layout parameters (LinearLayout)
                // 设置布局参数（LinearLayout）
                layoutParamsFactory = { parent, view ->
                    LinearLayout.LayoutParams(
                        150.dp,  // 宽度 150dp
                        ViewGroup.LayoutParams.WRAP_CONTENT  // 高度自适应
                    ).apply {
                        setMargins(
                            20.dp,  // 左边距
                            5.dp,   // 上边距
                            20.dp,  // 右边距
                            5.dp    // 下边距
                        )
                    }
                }

                // 事件处理器：处理 temple 触摸板的输入事件
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            // 单击显示 Toast 提示
                            FToast.show("dynamic focus target click!!")
                        }

                        else -> Unit
                    }
                }

                // 焦点变化回调：更新视图的视觉效果
                onFocusChange = { view, hasFocus, isLeft ->
                    triggerFocus(hasFocus, view, isLeft)
                }
            }
        )

        // Add to list
        // 将句柄添加到列表
        focusHandles.add(handle)

        // 更新当前动态焦点为最新添加的视图
        currentDynamicFocus = handle
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
     * - 背景色：获得焦点时使用主题色，失去焦点时使用黑色（ImageView 除外）
     * - 3D 效果：为眼镜显示添加立体视觉效果，增强深度感
     * 
     * @param hasFocus 是否获得焦点
     * @param view 目标视图（可为 null）
     * @param isLeft 是否为左眼视图（双目显示需要区分左右眼）
     */
    private fun triggerFocus(hasFocus: Boolean, view: View?, isLeft: Boolean) {

        view?.let {
            // ImageView 不设置背景色，保持图片原样
            if (view !is ImageView)
                view.setBackgroundColor(
                    getColor(if (hasFocus) com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0 else R.color.black)
                )
            // 3D effect
            // 应用 3D 视觉效果，增强眼镜显示的立体感
            make3DEffectForSide(view, isLeft, hasFocus)
        }
    }

    // dp extension function
    // dp 单位转换扩展函数，将 dp 值转换为像素
    private val Int.dp: Int
        get() = (this * this@DynamicFocusTargetActivity.resources.displayMetrics.density).toInt()
}