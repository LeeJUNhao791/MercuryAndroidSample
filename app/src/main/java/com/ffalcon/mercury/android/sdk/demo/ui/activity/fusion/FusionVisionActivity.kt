package com.ffalcon.mercury.android.sdk.demo.ui.activity.fusion

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityFusionActBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.launch

/**
 * 融合视觉演示活动
 * 
 * 这是一个简单的双目显示活动示例，继承自 BaseMirrorActivity
 * 主要用于展示基本的 temple 触摸板交互（双击退出）
 * 实际内容通过布局文件 ActivityFusionActBinding 定义
 */
class FusionVisionActivity : BaseMirrorActivity<ActivityFusionActBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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