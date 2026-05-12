package com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ffalcon.mercury.android.sdk.core.ViewPair
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutRecyclerviewMovedFocusBinding
import com.ffalcon.mercury.android.sdk.demo.ui.adapter.MovedFocusPosAdapter
import com.ffalcon.mercury.android.sdk.demo.ui.entity.contactList
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewFocusTracker
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.touch.TempleActionViewModel
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * 移动焦点位置 RecyclerView 演示活动
 * 
 * 展示如何实现焦点随列表项移动的滚动效果：
 * - 使用 RecyclerViewFocusTracker 管理列表焦点
 * - 焦点会随着滑动在列表项之间移动，而非固定在屏幕中央
 * - ignoreDelta 参数控制滑动的灵敏度（70 像素）
 * - 单击当前聚焦的列表项显示联系人姓名
 * 
 * 适用场景：需要焦点跟随内容移动的导航体验，类似手机触摸操作
 */
class MovedFocusPosRVActivity : BaseMirrorActivity<LayoutRecyclerviewMovedFocusBinding>() {
    /** RecyclerView 焦点追踪器，管理列表焦点移动 */
    private lateinit var favoriteTracker: RecyclerViewFocusTracker
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建 RecyclerView 焦点追踪器，传入左右眼的 RecyclerView 和忽略阈值
        favoriteTracker = RecyclerViewFocusTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView),
            ignoreDelta = 70  // 忽略小于 70 像素的滑动，防止误触
        )
        // 初始化视图（设置 Adapter、LayoutManager 等）
        initView()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
        // 激活焦点，使焦点系统开始工作
        favoriteTracker.focusObj.hasFocus = true
    }

    /**
     * 初始化事件监听
     * 
     * 监听 temple 触摸板事件，处理列表焦点移动和点击：
     * - 双击：退出页面
     * - 单击：显示当前聚焦的联系人姓名
     * - 滑动：移动焦点到相邻列表项
     */
    private fun initEvent() {
        // Listen to original events to implement tracking effect
        // 监听原始事件以实现跟踪效果（本例中未使用）

        // 监听 temple 触摸板事件
        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(this@MovedFocusPosRVActivity).get<TempleActionViewModel>()
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
                            // 双击退出页面
                            finish()
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
}
