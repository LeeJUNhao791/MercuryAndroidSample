package com.ffalcon.mercury.android.sdk.demo.ui.adapter

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.NonNull
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ItemTelephoneFavoriteMovedBinding
import com.ffalcon.mercury.android.sdk.demo.ui.entity.Contact
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewFocusTracker
import com.ffalcon.mercury.android.sdk.ext.BaseBindingHolder
import com.ffalcon.mercury.android.sdk.ext.SimpleBindingAdapter
import com.ffalcon.mercury.android.sdk.util.FLogger


/**
 * 移动焦点位置 RecyclerView 适配器
 * 
 * 用于展示联系人列表，支持焦点随列表项移动的导航效果：
 * - 配合 MovedFocusPosRVActivity 使用
 * - 焦点会随着滑动在列表项之间移动
 * - 使用 RecyclerViewFocusTracker 管理焦点状态
 * - 为选中项应用背景高亮、3D 效果和缩放动画
 * - 使用 focusedState Map 跟踪每个联系人的焦点状态，避免重复动画
 * 
 * @param context 上下文对象
 * @param isLeft 是否为左眼视图（用于 3D 效果）
 * @param favoriteTracker RecyclerView 焦点追踪器，管理焦点移动
 */
class MovedFocusPosAdapter(
    private val context: Context,
    private val isLeft: Boolean,
    private val favoriteTracker: RecyclerViewFocusTracker
) : SimpleBindingAdapter<ItemTelephoneFavoriteMovedBinding>() {
    /** 联系人数据列表 */
    private val mData = arrayListOf<Contact>()
    
    /** 焦点状态映射表，记录每个联系人 ID 是否处于焦点状态 */
    private var focusedState = mutableMapOf<Long, Boolean>()

    /**
     * 扩展函数：检查联系人是否处于焦点状态
     * 
     * @return 如果联系人处于焦点状态则返回 true，否则返回 false
     */
    private fun Contact.isFocused(): Boolean {
        return focusedState[id] ?: false
    }

    /**
     * 扩展函数：设置联系人的焦点状态
     * 
     * @param focused 焦点状态（true = 获得焦点，false = 失去焦点）
     */
    private fun Contact.setFocused(focused: Boolean) {
        focusedState[id] = focused
    }

    /**
     * 更新数据列表
     * 
     * 清除旧数据并添加新数据，然后通知适配器刷新整个列表
     * 
     * @param data 新的联系人数据列表
     */
    fun setData(data: List<Contact>) {
        mData.clear()
        mData.addAll(data)
        notifyDataSetChanged()
    }

    /**
     * 获取当前选中位置的联系人
     * 
     * 从焦点追踪器获取当前选中的位置，返回对应的联系人对象
     * 
     * @return 当前选中的联系人对象，如果位置无效则返回 null
     */
    fun getCurrentData(): Contact? {
        val curPos = favoriteTracker.checkedSelectPos()
        if (curPos < 0 || curPos > mData.size - 1) {
            return null
        }
        return mData[curPos]
    }

    /**
     * 绑定视图持有者数据
     * 
     * 根据位置设置联系人信息显示，并应用选中效果：
     * - 检查是否为无效联系人，如果是则隐藏视图
     * - 检查当前位置是否被选中，设置背景高亮
     * - 应用 3D 视觉效果
     * - 设置联系人姓名、头像首字母、电话号码
     * - 根据焦点状态变化触发动画（放大/缩小）
     * 
     * @param holder 视图持有者
     * @param position 数据位置
     */
    override fun onBindViewHolder(
        holder: BaseBindingHolder<ItemTelephoneFavoriteMovedBinding>,
        position: Int
    ) {
        holder.binding.apply {
            val contact = mData[position]
            if (contact == Contact.Invalid) {
                // 如果是无效联系人，隐藏视图
                root.visibility = View.INVISIBLE
                return
            } else {
                // 有效联系人，显示视图
                root.visibility = View.VISIBLE
            }

            // Set selected effect
            // 检查当前位置是否被选中
            val isSelectedPos = favoriteTracker.checkPosSelected(position)
            FLogger.d("onBindView --> pos=$position, isSelected=$isSelectedPos")
            
            // 根据选中状态设置背景
            if (isSelectedPos) {
                // 选中状态：设置高亮背景
                root.setBackgroundResource(R.drawable.ic_tele_list_hover)
            } else {
                // 未选中状态：清除背景
                root.background = null
            }

            // 应用 3D 选中效果
            make3DEffectForSide(root, isLeft, isSelectedPos)
            
            // 设置联系人信息显示
            tvName.text = contact.displayName
            tvPhoto.text = contact.displayName.first().toString()
            tvPhone.text = contact.phoneNum

            // 根据焦点状态变化触发动画
            if (isSelectedPos) {
                // 当前项被选中
                if (!contact.isFocused()) {
                    // 如果之前未处于焦点状态，执行放大动画
                    startZoomWith(layoutContent, NORMAL_SIZE.first, FOCUSED_SIZE.first)
                    startZoomHeight(layoutContent, NORMAL_SIZE.second, FOCUSED_SIZE.second)
                } else {
//                    val lp = root.layoutParams
//                    lp.width = FOCUSED_SIZE.first
//                    lp.height = FOCUSED_SIZE.second
//                    layoutContent.layoutParams = lp
                }
                // 标记为已聚焦
                contact.setFocused(true)
            } else {
                // 当前项未被选中
                if (contact.isFocused()) {
                    // 如果之前处于焦点状态，执行缩小动画
                    startZoomWith(layoutContent, FOCUSED_SIZE.first, NORMAL_SIZE.first)
                    startZoomHeight(layoutContent, FOCUSED_SIZE.second, NORMAL_SIZE.second)
                } else {
//                    val lp = layoutContent.layoutParams
//                    lp.width = NORMAL_SIZE.first
//                    lp.height = NORMAL_SIZE.second
//                    layoutContent.layoutParams = lp
                }
                // 标记为未聚焦
                contact.setFocused(false)
            }
        }
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    /**
     * 启动值动画
     * 
     * 创建并启动一个整数值动画，用于平滑过渡数值变化
     * 
     * @param from 起始值
     * @param to 目标值
     * @param listener 动画更新监听器
     * @param duration 动画持续时间（毫秒）
     */
    private fun startValAnim(
        from: Int,
        to: Int,
        listener: ValueAnimator.AnimatorUpdateListener?,
        duration: Long
    ) {
        // 如果起始值和目标值相同，无需动画
        if (from == to) {
            return
        }
        // 创建整数值动画
        val animator: ValueAnimator = ValueAnimator.ofInt(from, to)
        animator.duration = duration
        // 设置减速插值器，使动画结束时逐渐减速
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener(listener)
        animator.start()
    }

    /**
     * 启动宽度缩放动画
     * 
     * 通过动画平滑地改变视图的宽度
     * 
     * @param v 目标视图
     * @param from 起始宽度（像素）
     * @param to 目标宽度（像素）
     */
    private fun <V : View?> startZoomWith(@NonNull v: V, from: Int, to: Int) {
        startValAnim(from, to, { animation ->
            val lp = v!!.layoutParams
            val size: Int = Integer.valueOf(animation.animatedValue.toString())
            lp.width = size
            v.layoutParams = lp
        }, 200)  // 动画持续 200ms
    }

    /**
     * 启动高度缩放动画
     * 
     * 通过动画平滑地改变视图的高度
     * 
     * @param v 目标视图
     * @param from 起始高度（像素）
     * @param to 目标高度（像素）
     */
    private fun <V : View?> startZoomHeight(@NonNull v: V, from: Int, to: Int) {
        startValAnim(from, to, { animation ->
            val lp = v!!.layoutParams
            val size: Int = Integer.valueOf(animation.animatedValue.toString())
            lp.height = size
            v.layoutParams = lp
        }, 0)  // 动画持续时间为 0，立即生效
    }

    companion object {
        /** 正常状态下的尺寸（宽 400dp，高 82dp） */
        private val NORMAL_SIZE = Pair(dp2px(400f), dp2px(82f))
        
        /** 焦点状态下的尺寸（宽 426dp，高 82dp） */
        private val FOCUSED_SIZE = Pair(dp2px(426f), dp2px(82f))

        /**
         * dp 转 px
         * 
         * @param dpValue dp 值
         * @return 转换后的像素值
         */
        private fun dp2px(dpValue: Float): Int {
            val scale = Resources.getSystem().displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }
    }
}