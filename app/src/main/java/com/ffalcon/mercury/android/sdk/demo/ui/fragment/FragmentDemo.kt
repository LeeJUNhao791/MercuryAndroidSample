package com.ffalcon.mercury.android.sdk.demo.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import com.ffalcon.mercury.android.sdk.core.BaseScreenHolder
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.FragmentDemoBinding
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.focus.IFocusable
import com.ffalcon.mercury.android.sdk.focus.releaseFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.touch.TempleActionViewModel
import com.ffalcon.mercury.android.sdk.ui.fragment.BaseMirrorFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * Fragment 演示示例
 * 
 * 展示如何在 RayNeo AR 眼镜上使用 Fragment：
 * - 继承自 BaseMirrorFragment，支持双目显示
 * - 实现 IFocusable 接口，参与焦点系统
 * - 通过 arguments 传递内容参数
 * - 根据焦点状态改变背景颜色
 * - 响应 temple 触摸板事件（单击显示内容，双击释放焦点）
 * 
 * 适用场景：需要在 Activity 中嵌入可复用的双目显示组件
 */
class FragmentDemo :
    BaseMirrorFragment<FragmentDemoBinding, BaseScreenHolder<FragmentDemoBinding>>(), IFocusable {
    
    /** 是否有焦点，当焦点状态改变时自动更新视图背景 */
    override var hasFocus: Boolean = false
        set(value) {
            field = value
            updateViewBkg()
        }

    /** 焦点父容器，用于焦点层级管理 */
    override var focusParent: IFocusable? = null

    /**
     * 创建 Fragment 视图
     * 
     * 初始化视图内容和事件监听：
     * - 从 arguments 获取内容字符串并显示
     * - 监听 temple 触摸板事件
     * - 双击：消耗事件并释放焦点
     * - 单击：显示当前内容文本
     * 
     * @param rootView 根视图
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreateView(rootView: View, savedInstanceState: Bundle?) {
        // 从 arguments 获取内容参数，默认为空字符串
        val content = arguments?.getString("content") ?: ""
        mBindingPair.updateView {
            tvContent.text = content
        }
        
        // 从 Activity 获取 TempleActionViewModel（共享 ViewModel）
        val templeActionViewModel =
            ViewModelProvider(requireActivity()).get<TempleActionViewModel>()
        
        // 监听 temple 触摸板事件
        lifecycleScope.launchWhenResumed {
            templeActionViewModel.state.collectLatest { action ->
                // 如果无焦点、协程非活跃或事件已消费，则跳过
                if (!hasFocus || !isActive || action.consumed) {
                    return@collectLatest
                }
                when (action) {
                    is TempleAction.DoubleClick -> {
                        // consumed the event
                        // 消耗事件，防止传递给其他组件
                        action.consumed = true
                        // 释放焦点，返回到父容器
                        releaseFocus()
                    }

                    is TempleAction.Click -> {
                        // 单击：显示当前内容文本
                        FToast.show(mBindingPair.left.tvContent.text.toString())
                    }

                    else -> Unit
                }
            }
        }
    }

    /**
     * 根据内容获取背景颜色
     * 
     * 根据内容字符串的最后一个字符选择不同的主题色：
     * - 以 "1" 结尾：使用主题色 1
     * - 以 "2" 结尾：使用主题色 2
     * - 以 "3" 结尾：使用主题色 3
     * - 其他：默认使用主题色 1
     * 
     * @param content 内容字符串
     * @return 对应的颜色资源 ID
     */
    private fun getBgColor(content: String): Int {
        if (content.endsWith("1")) {
            return com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_1
        } else if (content.endsWith("2")) {
            return com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_2
        } else if (content.endsWith("3")) {
            return com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_3
        }
        return com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_1
    }

    /**
     * 更新视图背景
     * 
     * 根据焦点状态设置不同的背景颜色：
     * - 获得焦点：使用根据内容计算的主题色
     * - 失去焦点：使用固定的 teal_700 颜色
     */
    private fun updateViewBkg() {
        mBindingPair.updateView {
            if (hasFocus) {
                // 获得焦点：使用根据内容计算的主题色
                tvContent.setBackgroundColor(tvContent.context.getColor(getBgColor(tvContent.text.toString())))
            } else {
                // 失去焦点：使用固定颜色
                tvContent.setBackgroundColor(tvContent.context.getColor(R.color.teal_700))
            }
        }
    }

    companion object {
        /**
         * 创建 Fragment 实例的工厂方法
         * 
         * @param content 要显示的内容字符串
         * @return 配置好参数的 FragmentDemo 实例
         */
        fun newInstance(content: String): FragmentDemo {
            val fragment = FragmentDemo()
            fragment.arguments = Bundle().apply {
                putString("content", content)
            }
            return fragment
        }
    }
}
