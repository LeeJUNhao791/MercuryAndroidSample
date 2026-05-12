package com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion


import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityMirrorContainerViewBinding
import com.ffalcon.mercury.android.sdk.demo.databinding.ViewContainerMirrorBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseEventActivity
import kotlinx.coroutines.launch

/**
 * 镜像容器视图演示活动
 * 
 * 展示如何使用 MirrorContainer 实现自定义视图的双目显示：
 * - 继承自 BaseEventActivity（非 BaseMirrorActivity）
 * - 使用 MirrorContainer 绑定到 ViewContainerMirrorBinding
 * - 自动将左侧视图内容镜像到右侧，实现双目同步显示
 * 
 * 适用场景：需要在眼镜上显示自定义布局，并自动同步到双眼
 */
class MirrorContainerViewActivity : BaseEventActivity() {
    /** 视图绑定对象 */
    private lateinit var binding: ActivityMirrorContainerViewBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化视图绑定
        binding = ActivityMirrorContainerViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 将 MirrorContainer 绑定到 ViewContainerMirrorBinding
        // 这会自动将左侧的视图内容镜像复制到右侧，实现双目显示
        binding.mirrorContainer.bindTo(ViewContainerMirrorBinding::class.java)

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