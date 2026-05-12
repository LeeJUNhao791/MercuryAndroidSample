package com.ffalcon.mercury.android.sdk.demo.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * 音频录制模式配置工具类
 * 封装X3眼镜支持的多种录音模式配置
 * 提供统一的接口来配置不同场景下的音频采集参数
 */
object AudioRecordingModeConfig {

    // 日志标签，用于标识日志来源
    private const val TAG = "AudioRecordingConfig"

    // 特殊麦克风ID，用于指定特定的麦克风设备
    const val SPEAKER_MIC_ID = 23

    /**
     * 录音模式枚举
     * 定义X3眼镜支持的不同音频采集场景
     */
    enum class RecordingMode {
        OFF,                    // 关闭录音
        RECORD_TRANSLATION,     // 翻译模式 - 前麦克风，录制周围人声
        CAMCORDER,              // 摄像模式 - 左右镜腿麦克风，立体声
        VOICE_RECOGNITION,      // 语音识别模式 - 三颗麦克风，只收佩戴者声音
        VOICE_COMMUNICATION     // 通话模式 - 前麦和右方麦克风
    }

    /**
     * 录音配置数据类
     * 封装每种录音模式的完整配置参数
     * @param mode 录音模式
     * @param audioSource 音频源类型
     * @param sampleRate 采样率
     * @param channelConfig 声道配置
     * @param audioFormat 音频格式
     * @param setParameters 需要设置的音频参数，可为null
     * @param needSpeakerMic 是否需要指定麦克风设备
     * @param description 模式描述信息
     */
    data class RecordingConfig(
        val mode: RecordingMode,
        val audioSource: Int,
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int,
        val setParameters: String? = null,
        val needSpeakerMic: Boolean = true,
        val description: String
    )

    /**
     * 获取不同录音模式的配置
     * 根据录音模式返回对应的音频参数配置
     * @param mode 录音模式枚举值
     * @return 对应的录音配置对象
     */
    fun getRecordingConfig(mode: RecordingMode): RecordingConfig {
        return when (mode) {
            RecordingMode.OFF -> RecordingConfig(
                mode = mode,
                audioSource = MediaRecorder.AudioSource.MIC,
                sampleRate = 16000,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                setParameters = "audio_source_record=off",
                description = "关闭收音模式"
            )

            RecordingMode.RECORD_TRANSLATION -> RecordingConfig(
                mode = mode,
                audioSource = MediaRecorder.AudioSource.MIC,
                sampleRate = 16000,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                setParameters = "audio_source_record=record_origin3",
                description = "翻译模式：前麦克风，录制周围人声"
            )

            RecordingMode.CAMCORDER -> RecordingConfig(
                mode = mode,
                audioSource = MediaRecorder.AudioSource.MIC,
                sampleRate = 48000,
                channelConfig = AudioFormat.CHANNEL_IN_STEREO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                setParameters = "audio_source_record=camcorder",
                description = "摄像模式：左右镜腿麦克风，立体声"
            )

            RecordingMode.VOICE_RECOGNITION -> RecordingConfig(
                mode = mode,
                audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate = 16000,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                setParameters = "audio_source_record=voice_recognition",
                description = "语音识别模式：三麦克风，只收佩戴者声音"
            )

            RecordingMode.VOICE_COMMUNICATION -> RecordingConfig(
                mode = mode,
                audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate = 16000,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                setParameters = "audio_source_record=off",
                description = "通话模式：前麦和右方麦克风，只收佩戴者声音"
            )
        }
    }

    /**
     * 初始化AudioManager参数设置
     * 根据录音模式配置系统音频参数，如音频路由等
     * @param context Android上下文对象
     * @param mode 录音模式
     */
    fun initAudioManager(context: Context, mode: RecordingMode) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val config = getRecordingConfig(mode)

        // 如果配置中有需要设置的参数，则应用这些参数
        config.setParameters?.let { param ->
            audioManager.setParameters(param)
            Log.d(TAG, "设置音频参数: $param")
        }
    }

    /**
     * 配置AudioRecord的首选设备
     * 根据录音模式设置特定的麦克风设备作为音频输入源
     * @param context Android上下文对象
     * @param audioRecord AudioRecord实例
     * @param mode 录音模式
     * @return 是否成功配置设备
     */
    fun configureAudioDevice(context: Context, audioRecord: AudioRecord, mode: RecordingMode): Boolean {
        if (mode == RecordingMode.OFF) return true

        val config = getRecordingConfig(mode)
        // 如果该模式不需要指定麦克风，则直接返回成功
        if (!config.needSpeakerMic) return true

        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 获取所有可用的音频输入设备
        val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        // 查找匹配的麦克风设备
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && device.id == SPEAKER_MIC_ID) {
                // 设置首选麦克风设备
                audioRecord.setPreferredDevice(device)
                Log.d(TAG, "已设置首选麦克风设备 ID: ${SPEAKER_MIC_ID}")
                return true
            }
        }

        Log.w(TAG, "未找到指定的麦克风设备 ID: ${SPEAKER_MIC_ID}")
        return false
    }

    /**
     * 创建并初始化AudioRecord
     * 根据录音模式创建配置好的AudioRecord实例
     * @param context Android上下文对象
     * @param mode 录音模式
     * @param customBufferSize 自定义缓冲区大小，如果为null则使用默认值
     * @return 配置好的AudioRecord实例，如果初始化失败则返回null
     */
    @SuppressLint("MissingPermission")
    fun createAudioRecord(
        context: Context,
        mode: RecordingMode,
        customBufferSize: Int? = null
    ): AudioRecord? {
        val config = getRecordingConfig(mode)

        // 计算最小缓冲区大小
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioFormat
        )

        // 使用自定义缓冲区大小或默认的两倍最小缓冲区大小
        val bufferSize = customBufferSize ?: (minBufferSize * 2)

        Log.d(TAG, "创建AudioRecord - 模式: ${mode.name}, 缓冲区: $bufferSize bytes")

        // 创建AudioRecord实例
        val audioRecord = AudioRecord(
            config.audioSource,
            config.sampleRate,
            config.channelConfig,
            config.audioFormat,
            bufferSize
        )

        // 检查AudioRecord是否成功初始化
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord初始化失败")
            audioRecord.release()
            return null
        }

        // 配置首选麦克风设备
        if (!configureAudioDevice(context, audioRecord, mode)) {
            Log.w(TAG, "麦克风设备配置失败，但继续录音")
        }

        return audioRecord
    }

    /**
     * 释放资源
     * 停止录音并释放AudioRecord实例，恢复音频配置
     * @param context Android上下文对象
     * @param audioRecord AudioRecord实例
     * @param mode 录音模式
     */
    fun release(context: Context, audioRecord: AudioRecord?, mode: RecordingMode) {
        audioRecord?.let { record ->
            // 如果正在录音，先停止录音
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            // 释放AudioRecord资源
            record.release()
            Log.d(TAG, "AudioRecord已释放")
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setParameters("audio_source_record=off")
    }
}
