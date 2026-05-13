package com.mobvoi.wenet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutWenetBinding
import com.ffalcon.mercury.android.sdk.demo.utils.AudioRecordingModeConfig
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * WeNet 语音识别 Activity
 * 继承自 BaseMirrorActivity 以支持 AR 眼镜显示
 * 交互逻辑与 VoiceRecognitionActivity 保持一致，采用焦点追踪形式
 */
class WenetActivity : BaseMirrorActivity<LayoutWenetBinding>() {

    private val MY_PERMISSIONS_RECORD_AUDIO = 1
    private val LOG_TAG = "WENET"
    private val SAMPLE_RATE = 16000 // 采样率
    private val MAX_QUEUE_SIZE = 2500 // 100秒音频


    @Volatile
    private var isRecording = false
    private var record: AudioRecord? = null
    private var miniBufferSize = 0 // 缓冲区大小
    private val bufferQueue: BlockingQueue<ShortArray> = ArrayBlockingQueue(MAX_QUEUE_SIZE)
    
    // 固定位置焦点追踪器，管理眼镜上的焦点导航
    private var fixPosFocusTracker: FixPosFocusTracker? = null

    companion object {
        /**
         * 初始化资源文件，从 assets 复制到应用私有目录
         */
        @Throws(IOException::class)
        fun assetsInit(context: Context) {
            val assetMgr: AssetManager = context.assets
            val resourceList = listOf("final.zip", "units.txt", "ctc.ort", "decoder.ort", "encoder.ort")
            
            for (file in assetMgr.list("") ?: emptyArray()) {
                if (resourceList.contains(file)) {
                    val dst = File(context.filesDir, file)
                    if (!dst.exists() || dst.length() == 0L) {
                        Log.i("WENET", "Unzipping $file to ${dst.absolutePath}")
                        assetMgr.open(file).use { input ->
                            FileOutputStream(dst).use { output ->
                                val buffer = ByteArray(4 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "record permission is granted")
                // initRecorder() // 使用 AudioRecordingModeConfig 时延迟到开始录音时初始化
            } else {
                Toast.makeText(this, "Permissions denied to record audio", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 请求录音权限
        requestAudioPermissions()
        
        // 初始化资源
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                assetsInit(this@WenetActivity)
                // 初始化 WeNet 引擎
                Recognize.init(filesDir.path)
                
                withContext(Dispatchers.Main) {
                    mBindingPair.updateView {
                        textView.text = ""
                        button.isEnabled = true
                        button.text = "Start Record"
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Error process asset files to file path", e)
            }
        }

        // 初始化焦点目标，设置可交互的UI元素
        initFocusTarget()
        // 初始化事件监听，处理眼镜触摸板输入
        initEvent()

        mBindingPair.updateView {
            textView.text = "正在初始化引擎..."
            button.isEnabled = false
        }
    }

    private fun requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MY_PERMISSIONS_RECORD_AUDIO
            )
        }
    }

    /**
     * 初始化录音器
     * 使用 AudioRecordingModeConfig 以支持特定的语音识别模式（只收佩戴者声音）
     */
    private fun initRecorder(): Boolean {
        try {
            // 1. 初始化 AudioManager 参数，配置眼镜的收音模式
            AudioRecordingModeConfig.initAudioManager(this, AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION)
            
            // 2. 创建配置好的 AudioRecord 实例
            record = AudioRecordingModeConfig.createAudioRecord(this, AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION)
            
            if (record?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(LOG_TAG, "Audio Record can't initialize!")
                return false
            }
            
            // 获取实际的缓冲区大小
            miniBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            Log.i(LOG_TAG, "Record init okay with VOICE_RECOGNITION mode")
            return true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "initRecorder failed", e)
            return false
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
                button,
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
                        triggerFocus(hasFocus, button, mBindingPair.checkIsLeft(this))
                    }
                }
            )

            // 添加焦点目标到管理器
            focusHolder.addFocusTarget(btnRecordInfo)
            // 设置初始焦点到录音按钮
            focusHolder.currentFocus(mBindingPair.left.button)
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
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(it)
                    }
                }
            }
        }
    }

    /**
     * 切换录音状态
     */
    private fun toggleRecording() {
        if (!isRecording) {
            if (initRecorder()) {
                startRecording()
            } else {
                FToast.show("初始化录音失败")
            }
        } else {
            stopRecording()
        }
    }

    /**
     * 开始录音及识别流程
     * 重置识别状态，启动音频采集线程和ASR处理线程，并更新UI按钮状态
     */
    private fun startRecording() {
        Log.e(LOG_TAG, "startRecording")
        // 标记录音状态为进行中
        isRecording = true
        // 重置 WeNet 识别引擎状态
        Recognize.reset()
        // 启动音频数据采集线程
        startRecordThread()
        // 启动语音识别处理线程
        startAsrThread()
        // 通知底层引擎开始解码
        Recognize.startDecode()
        
        // 更新眼镜显示界面，将按钮文本更改为“停止录音”
        mBindingPair.updateView {
            button.text = "Stop Record"
        }
    }

    private fun stopRecording() {
        Log.e(LOG_TAG, "stopRecording")
        isRecording = false
        Recognize.setInputFinished()
        
        // 释放 AudioRecord 资源并恢复音频配置
        AudioRecordingModeConfig.release(this, record, AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION)
        record = null
        
        mBindingPair.updateView {
            button.text = "Start Record"
        }
    }

    private fun startRecordThread() {
        Thread {
            val audioRecord = record ?: return@Thread
            audioRecord.startRecording()
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            
            while (isRecording) {
                val bufferSizeInShorts = if (miniBufferSize > 0) miniBufferSize / 2 else 640
                val buffer = ShortArray(bufferSizeInShorts)
                val read = audioRecord.read(buffer, 0, buffer.size)
                
                if (read > 0) {
                    runOnUiThread {
                        mBindingPair.updateView {
                            voiceRectView.add(calculateDb(buffer, read))
                        }
                    }
                    
                    try {
                        val data = buffer.copyOf(read)
                        bufferQueue.put(data)
                    } catch (e: InterruptedException) {
                        Log.e(LOG_TAG, e.message ?: "Error in record thread")
                    }
                }
            }
            // 录音停止由 stopRecording 处理 release
        }.start()
    }

    /**
     * 计算音频分贝值
     * 根据音频缓冲区数据计算能量，并转换为归一化的分贝值用于UI显示
     *
     * @param buffer 音频采样数据缓冲区
     * @param size 有效采样数据长度
     * @return 归一化后的分贝值 (0.0 - 1.0)
     */
    private fun calculateDb(buffer: ShortArray, size: Int): Double {
        var energy = 0.0
        // 累加所有采样点的平方值以计算能量
        for (i in 0 until size) {
            val value = buffer[i]
            energy += (value.toInt() * value.toInt()).toDouble()
        }
        // 计算平均能量
        energy /= size.toDouble()
        // 转换为对数刻度并归一化到 0-1 范围
        energy = (10 * Math.log10(1 + energy)) / 100
        // 限制最大值为 1.0
        energy = Math.min(energy, 1.0)
        return energy
    }

    private fun startAsrThread() {
        Thread {
            // 发送所有数据
            while (isRecording || bufferQueue.isNotEmpty()) {
                try {
                    val data = bufferQueue.poll() ?: continue
                    // 1. 添加数据到 C++ 接口
                    Recognize.acceptWaveform(data)
                    // 2. 获取部分结果
                    val result = Recognize.getResult()
                    Log.e(LOG_TAG, "startAsrThread result: "  + result )
                    if (result.isNotEmpty()) {
                        runOnUiThread {
                            mBindingPair.updateView {
                                textView.text = result
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, e.message ?: "Error in ASR thread")
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        // 确保资源被释放
        AudioRecordingModeConfig.release(this, record, AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION)
        record = null
    }
}
