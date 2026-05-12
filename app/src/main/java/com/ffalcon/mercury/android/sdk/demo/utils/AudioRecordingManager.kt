package com.ffalcon.mercury.android.sdk.demo.utils

import android.content.Context
import android.media.AudioRecord
import android.util.Log

/**
 * 简化版音频录制管理器
 * 提供统一的录音接口，支持多种录音模式
 * @param context Android上下文对象
 */
class AudioRecordingManager(private val context: Context) {

    // AudioRecord实例，用于从麦克风捕获音频
    private var audioRecord: AudioRecord? = null
    // 当前录音模式
    private var currentMode: AudioRecordingModeConfig.RecordingMode = AudioRecordingModeConfig.RecordingMode.OFF
    // 标记是否正在录音
    private var isRecording = false

    companion object {
        // 日志标签，用于标识日志来源
        private const val TAG = "AudioRecordingManager"
    }

    /**
     * 开始录音
     * 初始化音频管理器参数，创建AudioRecord并开始录音线程
     * @param mode 录音模式，决定使用哪些麦克风和音频配置
     * @param onAudioData 音频数据回调，接收Float数组和有效样本数量
     * @return 是否成功开始录音
     */
    fun startRecording(mode: AudioRecordingModeConfig.RecordingMode, onAudioData: ((FloatArray, Int) -> Unit)? = null): Boolean {
        if (isRecording) {
            Log.w(TAG, "已经在录音中")
            return false
        }

        try {
            // 初始化AudioManager参数，设置音频路由等
            AudioRecordingModeConfig.initAudioManager(context, mode)

            // 创建AudioRecord实例，配置音频参数
            audioRecord = AudioRecordingModeConfig.createAudioRecord(context, mode)

            audioRecord?.let { record ->
                // 开始录音
                record.startRecording()
                currentMode = mode
                isRecording = true

                // 启动后台线程处理音频数据
                Thread {
                    processAudioSamples(record, onAudioData)
                }.apply {
                    isDaemon = true
                    start()
                }

                Log.d(TAG, "开始录音 - 模式: ${mode.name}")
                return true
            }

            Log.e(TAG, "创建AudioRecord失败")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            return false
        }
    }

    /**
     * 停止录音
     * 停止音频录制并释放相关资源
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "当前未在录音")
            return
        }

        try {
            // 释放AudioRecord资源和恢复音频配置
            AudioRecordingModeConfig.release(context, audioRecord, currentMode)
            audioRecord = null
            isRecording = false

            Log.d(TAG, "停止录音 - 模式: ${currentMode.name}")
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
        }
    }

    /**
     * 处理音频采样数据
     * 在后台线程中持续读取音频数据并转换为Float数组
     * @param audioRecord AudioRecord实例
     * @param onAudioData 音频数据回调
     */
    private fun processAudioSamples(audioRecord: AudioRecord, onAudioData: ((FloatArray, Int) -> Unit)?) {
        val bufferSize = 512
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            // 从AudioRecord读取音频数据
            val ret = audioRecord.read(buffer, 0, buffer.size)
            if (ret > 0) {
                // 将Short数组转换为Float数组，归一化到[-1, 1]范围
                val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }

                // 调用回调函数处理音频数据
                onAudioData?.invoke(samples, ret)
            }
        }
    }

    /**
     * 获取当前录音模式
     * @return 当前的录音模式枚举值
     */
    fun getCurrentMode(): AudioRecordingModeConfig.RecordingMode = currentMode

    /**
     * 检查是否正在录音
     * @return true表示正在录音，false表示未录音
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * 释放资源
     * 停止录音并清理所有相关资源
     */
    fun release() {
        stopRecording()
    }
}
