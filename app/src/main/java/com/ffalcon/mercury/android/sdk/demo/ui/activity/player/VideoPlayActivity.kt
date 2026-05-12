package com.ffalcon.mercury.android.sdk.demo.ui.activity.player

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityVideoPlayBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseEventActivity
import kotlinx.coroutines.launch

/**
 * 视频播放活动
 *
 * 演示如何在 RayNeo AR 眼镜上播放视频：
 * - 使用 MediaPlayer 播放本地资源视频
 * - 通过 TextureView 显示视频内容
 * - 使用 MirrorView 实现双目显示（左眼/右眼同步）
 * - 支持 temple 触摸板控制：单击暂停/播放，双击退出
 *
 * 注意：继承自 BaseEventActivity（非 BaseMirrorActivity），使用 MirrorView 进行画面镜像
 */
class VideoPlayActivity : BaseEventActivity() {
    /** 视图绑定对象 */
    private lateinit var binding: ActivityVideoPlayBinding
    /** 媒体播放器实例 */
    private var mPlayer: MediaPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化视图绑定
        binding = ActivityVideoPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 TextureView 的 SurfaceTexture 监听器
        binding.textureView.surfaceTextureListener = object : SimpleSurfaceTextureListener() {
            /**
             * SurfaceTexture 可用时调用
             *
             * 在此处初始化和配置 MediaPlayer：
             * - 创建播放器并加载视频资源
             * - 设置显示表面
             * - 配置音量和循环播放
             * - 开始播放
             */
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                try {
                    // 创建 MediaPlayer 实例，加载 raw 目录下的 rayneo 视频资源
                    mPlayer =
                        MediaPlayer.create(
                            this@VideoPlayActivity,
                            R.raw.rayneo
                        )?.apply {
                            // 设置视频显示表面
                            setSurface(Surface(surface))
                            // 设置左右声道音量（0.6）
                            setVolume(0.6f, 0.6f)
                            // 启用循环播放
                            isLooping = true
                            // 开始播放视频
                            start()
                        }
                } catch (ignored: Exception) {
                    // 捕获异常但不处理（生产环境应记录日志）
                }
            }
        }

        // 配置 MirrorView 实现双目显示
        binding.textureView.let { leftTexture ->
            // 设置镜像源为左侧 TextureView
            binding.mirrorView.setSource(leftTexture)
            // 启动镜像功能，将左眼画面同步到右眼
            binding.mirrorView.startMirroring()
        }

        // 监听 temple 触摸板事件
        lifecycleScope.launch {
            // 只在 Activity 恢复状态下收集事件
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 收集 temple 触摸板的状态流
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.Click -> {
                            // 单击：切换播放/暂停状态
                            mPlayer?.apply {
                                if (isPlaying) {
                                    pause() // 正在播放则暂停
                                } else {
                                    start() // 已暂停则继续播放
                                }
                            }
                        }

                        is TempleAction.DoubleClick -> {
                            // 双击：退出页面
                            finish()
                        }

                        else -> Unit // 其他事件不处理
                    }
                }
            }
        }

    }


    /**
     * 销毁活动时调用
     *
     * 清理资源以防止内存泄漏：
     * - 停止 MirrorView 的镜像功能
     * - 释放 MediaPlayer 占用的资源
     */
    override fun onDestroy() {
        super.onDestroy()
        // 停止双目镜像，释放显示相关资源
        binding.mirrorView.stopMirroring()
        // 释放媒体播放器，避免后台继续占用音频/视频资源
        mPlayer?.release()
    }

}

/**
 * SurfaceTexture 监听器的简单实现类
 *
 * 提供空的方法实现，方便子类只重写需要关注的方法（如 onSurfaceTextureAvailable）
 */
open class SimpleSurfaceTextureListener : TextureView.SurfaceTextureListener {
    /**
     * SurfaceTexture 可用时调用
     */
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    }

    /**
     * SurfaceTexture 尺寸改变时调用
     */
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    /**
     * SurfaceTexture 销毁时调用
     *
     * @return false 表示不自动销毁 SurfaceTexture，由开发者手动管理
     */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    /**
     * SurfaceTexture 内容更新时调用
     */
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

}