package com.ffalcon.mercury.android.sdk.demo.ui.activity

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.FragmentRecycleviewBinding
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutFragmentDemoBinding
import com.ffalcon.mercury.android.sdk.demo.ui.fragment.FragmentDemo
import com.ffalcon.mercury.android.sdk.demo.ui.fragment.FragmentRecyclerViewDemo
import com.ffalcon.mercury.android.sdk.demo.ui.wedget.OnTitleSelectListener
import com.ffalcon.mercury.android.sdk.focus.IFocusable
import com.ffalcon.mercury.android.sdk.focus.releaseFocus
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.ui.activity.BaseEventActivity

class FragmentDemoActivity : BaseEventActivity() {
    // 视图绑定对象，用于访问布局中的视图
    private lateinit var binding: LayoutFragmentDemoBinding
    // 存储所有 Fragment 实例的列表
    private lateinit var fragmentList: List<Fragment>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化视图绑定并设置内容视图
        binding = LayoutFragmentDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 配置标题栏视图
        binding.phoneTitleView.apply {
            // 设置标题栏显示的标题数组
            setTitles(
                arrayOf(
                    "fragment1",
                    "fragment2",
                    "fragment+recyclerView",
                )
            )
            // 监听眼镜端 Temple 按键操作
            watchAction(this@FragmentDemoActivity, templeActionViewModel)
            // 初始时让标题栏获取焦点
            hasFocus = true
        }
        // 初始化 Fragment 列表
        initFragments()
        // 初始化事件监听
        initEvent()
    }

    /**
     * 初始化事件监听器
     * 处理标题栏选择事件，切换对应的 Fragment 并管理焦点
     */
    private fun initEvent() {
        binding.phoneTitleView.onTitleSelectListener = object : OnTitleSelectListener {
            override fun onTitleSelect(pos: Int, titleView: TextView) {
                // 获取选中的 Fragment 实例
                val fragment = fragmentList[pos]
                // 显示选中的 Fragment，隐藏其他 Fragment
                showFragment(pos)
                // 释放标题栏的焦点
                binding.phoneTitleView.releaseFocus()
                // 如果 Fragment 支持焦点操作，则请求焦点并将前一个焦点视图作为参考
                if (fragment is IFocusable) {
                    fragment.reqFocus(binding.phoneTitleView)
                }
            }
        }
    }

    /**
     * 显示指定位置的 Fragment，隐藏其他 Fragment
     * @param pos 要显示的 Fragment 在列表中的索引
     */
    private fun showFragment(pos: Int) {
        supportFragmentManager.commit(true) {
            fragmentList.forEachIndexed { index, fragment ->
                if (pos == index) {
                    // 显示目标 Fragment
                    show(fragment)
                } else {
                    // 隐藏非目标 Fragment
                    hide(fragment)
                }
            }
        }
    }

    /**
     * 初始化 Fragment 列表并添加到容器中
     * 默认显示第一个 Fragment，隐藏其余 Fragment
     */
    private fun initFragments() {
        val titleView = binding.phoneTitleView
        supportFragmentManager.commit(true) {
            // 创建 Fragment 实例列表，并设置焦点父视图
            fragmentList = arrayListOf(
                FragmentDemo.newInstance("content1").apply { focusParent = titleView },
                FragmentDemo.newInstance("content2").apply { focusParent = titleView },
                FragmentRecyclerViewDemo.newInstance("content3").apply { focusParent = titleView },
            )
            // 遍历添加 Fragment 到容器，并控制初始显示状态
            fragmentList.forEachIndexed { index, fragment ->
                add(R.id.flContainer, fragment, buildFragmentTag("tab_$index"))
                if (index != 0) {
                    // 非第一个 Fragment 初始隐藏
                    hide(fragment)
                } else {
                    // 第一个 Fragment 初始显示
                    show(fragment)
                }
            }
        }
    }

    companion object {
        /**
         * 构建 Fragment 标签字符串
         * @param suffix 后缀标识
         * @return 完整的 Fragment 标签
         */
        fun buildFragmentTag(suffix: String): String {
            return "frag_$suffix"
        }
    }
}