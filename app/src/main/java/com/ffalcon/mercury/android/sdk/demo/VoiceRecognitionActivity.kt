package com.ffalcon.mercury.android.sdk.demo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutVoiceRecognitionBinding
import com.ffalcon.mercury.android.sdk.demo.utils.AudioRecordingManager
import com.ffalcon.mercury.android.sdk.demo.utils.AudioRecordingModeConfig
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private const val TAG = "sherpa-onnx"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

/**
 * 语音识别Activity
 * 使用Sherpa-ONNX库实现离线语音识别功能
 * 支持实时语音检测和识别结果展示
 */
class VoiceRecognitionActivity : BaseMirrorActivity<LayoutVoiceRecognitionBinding>() {

    // 语音活动检测器(VAD)，用于检测语音片段
    private lateinit var vad: Vad
    // 音频录制管理器，处理录音逻辑
    private lateinit var recordingManager: AudioRecordingManager
    // AudioRecord实例，用于从麦克风捕获音频
    private var audioRecord: AudioRecord? = null
    // 录音线程，在后台处理音频数据
    private var recordingThread: Thread? = null

    // 固定位置焦点追踪器，管理眼镜上的焦点导航
    private var fixPosFocusTracker: FixPosFocusTracker? = null


    // 音频源，使用麦克风作为输入
    private val audioSource = MediaRecorder.AudioSource.MIC
    // 采样率，16kHz适合语音识别
    private val sampleRateInHz = 16000
    // 声道配置，单声道
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    // 音频格式，16位PCM编码
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // 需要的权限列表，这里只需要录音权限
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    // 离线语音识别器，执行实际的语音转文字功能
    private lateinit var offlineRecognizer: OfflineRecognizer

    // 识别结果的索引计数器
    private var idx: Int = 0
    // 上一次识别的文本结果
    private var lastText: String = ""

    // 标记是否正在录音，使用@Volatile确保多线程可见性
    @Volatile
    private var isRecording: Boolean = false

    /**
     * 处理权限请求结果
     * @param requestCode 请求码
     * @param permissions 请求的权限数组
     * @param grantResults 权限授予结果数组
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        if (!permissionToRecordAccepted) {
            Log.e(TAG, "Audio record is disallowed")
            // 如果录音权限被拒绝，则结束Activity
            finish()
        }

        Log.i(TAG, "Audio record is permitted")
    }

    /**
     * Activity创建时的初始化
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求录音权限
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        // 初始化焦点目标，设置可交互的UI元素
        initFocusTarget()
        // 初始化事件监听，处理眼镜触摸板输入
        initEvent()

        // 更新双目显示界面，显示初始化状态
        mBindingPair.updateView { //设置双目显示
            tvStatus.text = "初始化模型中..."
            btnRecord.isEnabled = false
//            btnRecord.setOnClickListener { toggleRecording() }
        }

        // 创建音频录制管理器
        recordingManager = AudioRecordingManager(this)
        // 初始化语音识别模型
        initModels()
    }

    /**
     * 初始化语音识别模型
     * 在后台线程中加载VAD模型和离线识别器，避免阻塞UI
     */
    private fun initModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.e(TAG, "Start to initialize model")
            // 初始化语音活动检测模型
            initVadModel()
            Log.e(TAG, "Finished initializing VAD model")

            Log.e(TAG, "Start to initialize non-streaming recognizer")
            // 初始化离线语音识别器
            initOfflineRecognizer()
            Log.e(TAG, "Finished initializing non-streaming recognizer")

            // 切换回主线程更新UI
            withContext(Dispatchers.Main) {
                mBindingPair.updateView {
                    tvStatus.setText(R.string.speaker)
                    btnRecord.isEnabled = true
                    btnRecord.setText(R.string.start)
                    tvRecognitionResult.text = ""
                }
                Log.e(TAG, "Model initialization completed, button enabled")
            }
        }
    }

    /**
     * 初始化焦点目标
     * 为录音按钮设置焦点管理和事件处理
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，参数true表示支持循环导航
        val focusHolder = FocusHolder(true)
        // 设置左眼视图绑定
        mBindingPair.setLeft {
            // 创建录音按钮的焦点信息
            val btnRecordInfo = FocusInfo(
                btnRecord,
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            FToast.show("录音按钮点击")
                            // 切换录音状态
                            toggleRecording()
                        }
                        else -> Unit
                    }
                },
                focusChangeHandler = { hasFocus ->
                    // 当焦点状态改变时，更新UI显示效果
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, btnRecord, mBindingPair.checkIsLeft(this))
                    }
                }
            )

            // 添加焦点目标到管理器
            focusHolder.addFocusTarget(btnRecordInfo)
            // 设置初始焦点到录音按钮
            focusHolder.currentFocus(mBindingPair.left.btnRecord)
        }

        // 创建固定位置焦点追踪器并请求初始焦点
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    /**
     * 触发焦点效果
     * @param hasFocus 是否获得焦点
     * @param view 需要应用效果的视图
     * @param isLeft 是否为左眼视图
     */
    private fun triggerFocus(hasFocus: Boolean, view: android.view.View, isLeft: Boolean) {
        // 根据焦点状态设置背景颜色
        view.setBackgroundColor(getColor(if (hasFocus) R.color.purple_200 else R.color.black))
    }

    /**
     * 初始化事件监听
     * 收集眼镜触摸板的输入事件并进行处理
     */
    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            // 双击退出当前Activity
                            finish()
                        }
//                        else -> Unit
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(it)
                    }
                }
            }
        }
    }

    /**
     * 切换录音状态
     * 如果当前未在录音则开始录音，否则停止录音
     */
    private fun toggleRecording() {
        Log.e(TAG, "toggleRecording isRecording --> " + isRecording )
        if (!recordingManager.isCurrentlyRecording()) {
            // 开始录音
            startRecording()
        } else {
            // 停止录音
            stopRecording()
        }
    }

    /**
     * 开始录音
     * 配置音频录制参数并启动录音线程
     */
    private fun startRecording() {
        Log.e(TAG, "startRecording: doing!!")
        // 使用语音识别模式开始录音，设置音频数据回调
        val success = recordingManager.startRecording(
            mode = AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION,
            onAudioData = { samples, size ->
                // 处理音频样本
                processSamples(samples, size)
            }
        )

        if (success) {
            // 更新UI显示录音状态
            mBindingPair.updateView {
                btnRecord.setText(R.string.stop)
                tvRecognitionResult.text = ""
            }
            // 重置识别结果计数器和VAD状态
            lastText = ""
            idx = 0
            vad.reset()
            Log.e(TAG, "开始录音成功")
        } else {
            Log.e(TAG, "开始录音失败")
        }
    }

    /**
     * 停止录音
     * 停止音频录制并更新UI状态
     */
    private fun stopRecording() {
        // 停止录音管理器
        recordingManager.stopRecording()

        // 更新UI显示开始按钮
        mBindingPair.updateView {
            btnRecord.setText(R.string.start)
        }
        Log.e(TAG, "停止录音")
    }

    /**
     * 初始化语音活动检测(VAD)模型
     * 用于检测音频中的语音片段，区分语音和静音
     */
    private fun initVadModel() {
        val type = 0
        Log.e(TAG, "Select VAD model type ${type}")
        // 获取VAD模型配置
        val config = getVadModelConfig(type)

        // 创建VAD实例，使用应用资源管理器加载模型
        vad = Vad(
            assetManager = application.assets,
            config = config!!,
        )
    }

    /**
     * 处理音频样本
     * 将音频数据送入VAD检测，并对检测到的语音片段进行识别
     * @param samples 音频样本数组
     * @param size 有效样本数量
     */
    private fun processSamples(samples: FloatArray, size: Int) {
        // 将音频波形数据送入VAD
        vad.acceptWaveform(samples)
        // 处理所有待处理的语音片段
        while (!vad.empty()) {
            // 获取队列前端的语音片段
            val segment = vad.front()
            // 在后台线程执行第二遍识别
            lifecycleScope.launch(Dispatchers.IO) {
                // 执行离线语音识别
                val text = runSecondPass(segment.samples)
                if (text.isNotBlank()) {
                    // 切换回主线程更新UI
                    withContext(Dispatchers.Main) {
                        lastText = "${lastText}\n${idx}: ${text}"
                        idx += 1
                        mBindingPair.updateView {
                            tvRecognitionResult.text = lastText.lowercase()
                        }
                    }
                }
            }
            // 移除已处理的片段
            vad.pop()
        }
    }

    /**
     * 初始化离线语音识别器
     * 配置ASR(自动语音识别)模型参数
     */
    private fun initOfflineRecognizer() {
        // ASR模型类型，0表示使用默认模型
        val asrModelType = 0
        // ASR规则有限状态转换器，可为null
        val asrRuleFsts: String? = null
        Log.e(TAG, "Select model type ${asrModelType} for ASR")

        // 创建离线识别器配置，包含特征提取和模型配置
        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getOfflineModelConfig(type = asrModelType)!!,
        )
        // 如果提供了规则FSTs，则设置到配置中
        if (asrRuleFsts != null) {
            config.ruleFsts = asrRuleFsts
        }

        // 创建离线识别器实例
        offlineRecognizer = OfflineRecognizer(
            assetManager = application.assets,
            config = config,
        )
    }

    /**
     * 执行第二遍语音识别
     * 对给定的音频样本进行离线识别，返回识别结果文本
     * @param samples 音频样本数组
     * @return 识别出的文本结果
     */
    private fun runSecondPass(samples: FloatArray): String {
        // 创建识别流
        val stream = offlineRecognizer.createStream()
        // 接受音频波形数据
        stream.acceptWaveform(samples, sampleRateInHz)
        // 执行解码识别
        offlineRecognizer.decode(stream)
        // 获取识别结果
        val result = offlineRecognizer.getResult(stream)
        // 释放流资源
        stream.release()
        return result.text
    }

    /**
     * Activity销毁时的清理工作
     * 停止录音并释放相关资源，避免内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        // 停止录音
        stopRecording()
        // 释放录音管理器资源
        recordingManager.release()
    }
}
