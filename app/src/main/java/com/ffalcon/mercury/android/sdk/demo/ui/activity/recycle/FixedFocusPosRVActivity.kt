package com.ffalcon.mercury.android.sdk.demo.ui.activity.recycle

import android.os.Bundle
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ffalcon.mercury.android.sdk.R
import com.ffalcon.mercury.android.sdk.core.ViewPair
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutRecyclerviewBinding
import com.ffalcon.mercury.android.sdk.demo.ui.adapter.FixedFocusPosAdapter
import com.ffalcon.mercury.android.sdk.demo.ui.entity.contactList
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewSlidingTracker
import com.ffalcon.mercury.android.sdk.ext.dp
import com.ffalcon.mercury.android.sdk.ext.setViewVisible
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.touch.TempleActionViewModel
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.activity.actionName
import com.ffalcon.mercury.android.sdk.util.FLogger
import com.ffalcon.mercury.android.sdk.util.StartSnapHelper
import kotlinx.coroutines.isActive

/**
 * 固定焦点位置 RecyclerView 演示活动
 * 
 * 展示如何实现焦点固定在屏幕中央的列表滚动效果：
 * - 使用 RecyclerViewSlidingTracker 管理列表滚动和焦点
 * - 通过 StartSnapHelper 实现列表项自动吸附到焦点位置
 * - temple 触摸板滑动时，列表滚动但焦点位置保持不变
 * - 单击当前聚焦的列表项显示联系人姓名
 * 
 * 适用场景：需要类似传统电视遥控器的焦点导航体验，焦点始终在屏幕中央
 */
class FixedFocusPosRVActivity : BaseMirrorActivity<LayoutRecyclerviewBinding>() {
    /** RecyclerView 滑动追踪器，管理列表滚动和焦点 */
    private lateinit var favoriteTracker: RecyclerViewSlidingTracker
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建 RecyclerView 滑动追踪器，传入左右眼的 RecyclerView
        favoriteTracker = RecyclerViewSlidingTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView)
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
     * 监听两类事件：
     * 1. 原始 MotionEvent 事件：用于实现跟踪效果（调试用）
     * 2. Temple 触摸板事件：处理列表滚动和点击
     */
    private fun initEvent() {
        // Listen to original events to implement tracking effect
        // 监听原始 MotionEvent 事件，用于实现跟踪效果（调试用）
        favoriteTracker.observeOriginMotionEventStream(
            motionEventDispatcher
        ) { event ->
            // 复制并修改 MotionEvent，将 x 坐标固定为 320（屏幕中央）
            MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                event.action,
                320f,  // 固定 x 坐标
                event.x,
                event.metaState
            ).apply {
                val e = this
                FLogger.d(
                    "onReceiveEvent：x = ${e.x}, y = ${e.y},action = ${e.actionName()}, deviceId = ${e.deviceId}"
                )
            }
        }

        // 监听 temple 触摸板事件
        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(this@FixedFocusPosRVActivity).get<TempleActionViewModel>()
            templeActionViewModel.state.collect {
                // 如果焦点未激活、协程非活跃或事件已消费，则跳过
                if (!favoriteTracker.focusObj.hasFocus || !this.isActive || it.consumed) {
                    return@collect
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
                                (mBindingPair.left.recyclerView.adapter as FixedFocusPosAdapter)
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
     * - 设置 FixedFocusPosAdapter 适配器，传入焦点追踪器
     * - 禁用 itemAnimator 避免动画干扰
     * - 附加 StartSnapHelper 实现自动吸附效果（偏移 41dp）
     * - 设置初始选中位置为 0
     * - 显示选中指示器（ivSelectHover）
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
                adapter = FixedFocusPosAdapter(context, isLeft, favoriteTracker).apply {
                    setData(contactList(true))  // 设置联系人数据
                }
                // 禁用 item 动画，避免影响性能
                itemAnimator = null
                // 创建并附加 SnapHelper，实现自动吸附到焦点位置（偏移 41dp）
                val snapHelper = StartSnapHelper(41.dp)
                snapHelper.attachToRecyclerView(this)
                setTag(R.id.tag_snap_helper, snapHelper)
            }
            // 为选中指示器应用 3D 效果
            make3DEffectForSide(this.ivSelectHover, isLeft, isLeft)
            // 设置初始选中位置为第一项
            favoriteTracker.setCurrentSelectPos(0)
            // 显示选中指示器
            setViewVisible(true, ivSelectHover)
        }
    }
}
