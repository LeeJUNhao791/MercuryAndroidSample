package com.ffalcon.mercury.android.sdk.demo.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ffalcon.mercury.android.sdk.core.BaseScreenHolder
import com.ffalcon.mercury.android.sdk.core.ViewPair
import com.ffalcon.mercury.android.sdk.demo.databinding.FragmentRecycleviewBinding
import com.ffalcon.mercury.android.sdk.demo.ui.adapter.MovedFocusPosAdapter
import com.ffalcon.mercury.android.sdk.demo.ui.entity.contactList
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.focus.IFocusable
import com.ffalcon.mercury.android.sdk.focus.releaseFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.touch.TempleActionViewModel
import com.ffalcon.mercury.android.sdk.ui.fragment.BaseMirrorFragment
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewFocusTracker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * Fragment RecyclerView 演示示例
 * 
 * 展示如何在 Fragment 中使用 RecyclerView 实现列表滚动：
 * - 继承自 BaseMirrorFragment，支持双目显示
 * - 实现 IFocusable 接口，参与焦点系统
 * - 使用 RecyclerViewFocusTracker 管理列表焦点移动
 * - 配合 MovedFocusPosAdapter 实现焦点跟随效果
 * - 响应 temple 触摸板事件（双击释放焦点，单击显示联系人）
 * 
 * 适用场景：需要在 Fragment 中嵌入可滚动的双目显示列表
 */
class FragmentRecyclerViewDemo :
    BaseMirrorFragment<FragmentRecycleviewBinding, BaseScreenHolder<FragmentRecycleviewBinding>>(),
    IFocusable {
    
    /** 是否有焦点，当焦点状态改变时同步更新追踪器的焦点状态 */
    override var hasFocus: Boolean = false
        set(value) {
            field = value
            favoriteTracker.focusObj.hasFocus = value
        }

    /** 焦点父容器，用于焦点层级管理 */
    override var focusParent: IFocusable? = null

    /** RecyclerView 焦点追踪器，管理列表焦点移动 */
    private lateinit var favoriteTracker: RecyclerViewFocusTracker

    /**
     * 创建 Fragment 视图
     * 
     * 初始化 RecyclerView 和事件监听：
     * - 创建 RecyclerViewFocusTracker 并配置忽略阈值
     * - 初始化视图（设置 Adapter、LayoutManager）
     * - 初始化事件监听（temple 触摸板事件）
     * 
     * @param rootView 根视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreateView(rootView: View, savedInstanceState: Bundle?) {
        // 创建 RecyclerView 焦点追踪器，传入左右眼的 RecyclerView 和忽略阈值
        favoriteTracker = RecyclerViewFocusTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView),
            ignoreDelta = 70  // 忽略小于 70 像素的滑动，防止误触
        )

        // 初始化视图
        initView()
        // 初始化事件监听
        initEvent()
    }

    /**
     * 初始化事件监听
     * 
     * 监听 temple 触摸板事件，处理列表焦点移动和点击：
     * - 双击：消耗事件并释放焦点
     * - 单击：显示当前聚焦的联系人姓名
     * - 滑动：移动焦点到相邻列表项
     */
    private fun initEvent() {
        // Listen to original events to implement tracking effect
        // 监听原始事件以实现跟踪效果（本例中未使用）

        // 监听 temple 触摸板事件
        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(requireActivity()).get<TempleActionViewModel>()
            // 使用 collectLatest 确保只处理最新的事件
            templeActionViewModel.state.collectLatest {
                // 如果焦点未激活、协程非活跃或事件已消费，则跳过
                if (!favoriteTracker.focusObj.hasFocus || !this.isActive || it.consumed) {
                    return@collectLatest
                }
                // 处理 temple 动作事件
                favoriteTracker.handleActionEvent(it) { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> {
                            // 消耗事件，防止传递给其他组件
                            action.consumed = true
                            // 释放焦点，返回到父容器
                            releaseFocus()
                        }

                        is TempleAction.Click -> {
                            // 单击：如果事件未被消费，显示当前聚焦的联系人姓名
                            if (!action.consumed) {
                                (mBindingPair.left.recyclerView.adapter as MovedFocusPosAdapter)
                                    .getCurrentData()?.apply {
                                        FToast.show(displayName)
                                    }
                            }
                        }

                        else -> {}  // 其他事件不处理
                    }
                }
            }
        }
    }

    /**
     * 初始化视图
     * 
     * 配置 RecyclerView 的各项属性：
     * - 设置 LinearLayoutManager 线性布局管理器
     * - 设置 MovedFocusPosAdapter 适配器，传入焦点追踪器
     * - 禁用 itemAnimator 避免动画干扰
     * - 设置初始选中位置为 0
     */
    private fun initView() {
        val mPair = mBindingPair
        mPair.updateView {
            // 检查当前是否为左眼视图
            val isLeft = mPair.checkIsLeft(this)
            recyclerView.apply {
                // 设置线性布局管理器
                layoutManager = LinearLayoutManager(context)
                // 设置适配器，传入上下文、是否左眼、焦点追踪器
                adapter = MovedFocusPosAdapter(context, isLeft, favoriteTracker).apply {
                    setData(contactList())  // 设置联系人数据
                }
                // 禁用 item 动画，避免影响性能
                itemAnimator = null
            }
            // 设置初始选中位置为第一项
            favoriteTracker.setCurrentSelectPos(0)
        }
    }


    companion object {
        /**
         * 创建 Fragment 实例的工厂方法
         * 
         * @param content 内容字符串（本例中未使用，保留用于扩展）
         * @return 配置好参数的 FragmentRecyclerViewDemo 实例
         */
        fun newInstance(content: String): FragmentRecyclerViewDemo {
            val fragment = FragmentRecyclerViewDemo()
            fragment.arguments = Bundle().apply {
                putString("content", content)
            }
            return fragment
        }
    }
}
