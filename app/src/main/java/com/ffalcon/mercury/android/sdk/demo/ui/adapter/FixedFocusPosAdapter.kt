package com.ffalcon.mercury.android.sdk.demo.ui.adapter

import android.content.Context
import android.view.View
import com.ffalcon.mercury.android.sdk.core.make3DEffectForSide
import com.ffalcon.mercury.android.sdk.demo.databinding.ItemTelephoneFavoriteBinding
import com.ffalcon.mercury.android.sdk.demo.ui.entity.Contact
import com.ffalcon.mercury.android.sdk.ui.util.RecyclerViewSlidingTracker
import com.ffalcon.mercury.android.sdk.ext.BaseBindingHolder
import com.ffalcon.mercury.android.sdk.ext.SimpleBindingAdapter


/**
 * 固定焦点位置 RecyclerView 适配器
 * 
 * 用于展示联系人列表，支持固定焦点位置的导航效果：
 * - 配合 FixedFocusPosRVActivity 使用
 * - 焦点固定在屏幕中央，列表项滚动经过焦点位置
 * - 使用 RecyclerViewSlidingTracker 管理选中状态
 * - 为选中项应用 3D 视觉效果
 * 
 * @param context 上下文对象
 * @param isLeft 是否为左眼视图（用于 3D 效果）
 * @param favoriteTracker RecyclerView 滑动追踪器，管理焦点位置
 */
class FixedFocusPosAdapter(
    private val context: Context,
    private val isLeft: Boolean,
    private val favoriteTracker: RecyclerViewSlidingTracker
) : SimpleBindingAdapter<ItemTelephoneFavoriteBinding>() {
    // 联系人数据列表
    private val mData = arrayListOf<Contact>()

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
        // 获取当前选中的位置
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
     * - 检查当前位置是否被选中
     * - 为选中项应用 3D 视觉效果
     * - 设置联系人姓名、头像首字母、电话号码
     * 
     * @param holder 视图持有者
     * @param position 数据位置
     */
    override fun onBindViewHolder(
        holder: BaseBindingHolder<ItemTelephoneFavoriteBinding>,
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
            // 检查当前位置是否被选中
            val isSelectedPos = favoriteTracker.checkPosSelected(position)
            // 应用3D选中效果
            make3DEffectForSide(root, isLeft, isSelectedPos)
            // 设置联系人信息显示
            tvName.text = contact.displayName
            tvPhoto.text = contact.displayName.first().toString()
            tvPhone.text = contact.phoneNum
        }
    }

    /**
     * 获取数据项总数
     * @return 联系人列表的大小
     */
    override fun getItemCount(): Int {
        return mData.size
    }
}