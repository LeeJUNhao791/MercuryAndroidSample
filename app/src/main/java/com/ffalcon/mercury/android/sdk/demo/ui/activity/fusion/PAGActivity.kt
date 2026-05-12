package com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityPagBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.launch

/**
 * PAG 动画演示活动
 * 
 * 展示如何在 RayNeo AR 眼镜上播放 PAG（Portable Animated Graphics）动画：
 * - PAG 是腾讯开源的高性能动画格式，支持复杂矢量动画
 * - 使用 BaseMirrorActivity 实现双目显示
 * - 从 assets 目录加载 test.pag 动画文件
 * - 设置重复次数为 0（无限循环），并自动播放
 * 
 * 适用场景：在眼镜上显示动态图标、加载动画、交互动画等
 */
class PAGActivity: BaseMirrorActivity<ActivityPagBinding>()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 更新双目视图，配置并播放 PAG 动画
        mBindingPair.updateView {
            pagView.apply {
                // 设置 PAG 文件路径，从 assets 目录加载
                path = "assets://test.pag"
                // 设置重复次数：0 表示无限循环
                setRepeatCount(0)
                // 开始播放动画
                play()
            }
        }

        // 监听 temple 触摸板事件
        lifecycleScope.launch {
            // 只在 Activity 恢复状态下收集事件
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 收集 temple 触摸板的状态流
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            // 双击退出页面
                            finish()
                        }

                        else -> Unit  // 其他事件不处理
                    }
                }
            }
        }
    }
}