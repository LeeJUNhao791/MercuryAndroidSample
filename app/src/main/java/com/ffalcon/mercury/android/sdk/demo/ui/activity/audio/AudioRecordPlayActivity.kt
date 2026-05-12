package com.ffalcon.mercury.android.sdk.demo.ui.activity.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityAudioRecordPlayBinding
import com.ffalcon.mercury.android.sdk.demo.utils.AudioRecordingManager
import com.ffalcon.mercury.android.sdk.demo.utils.AudioRecordingModeConfig
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AudioRecordPlay"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 201

/**
 * 录音与播放演示页面
 *
 * 继承自 BaseMirrorActivity，支持 RayNeo AR 眼镜双目显示
 * - 点击录音按钮：使用眼镜麦克风录音，并将 PCM 数据保存为 WAV 文件
 * - 点击播放按钮：播放已录制的 WAV 文件，验证录音清晰度
 */
class AudioRecordPlayActivity : BaseMirrorActivity<ActivityAudioRecordPlayBinding>() {

    private lateinit var recordingManager: AudioRecordingManager
    private var mediaPlayer: MediaPlayer? = null
    private var fixPosFocusTracker: FixPosFocusTracker? = null

    private var recordingThread: Thread? = null
    private var audioFilePath: String? = null

    private val audioSamples = mutableListOf<Short>()

    private val handler = Handler(Looper.getMainLooper())
    private var recordStartTime = 0L
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            if (recordingManager.isCurrentlyRecording()) {
                val elapsed = (System.currentTimeMillis() - recordStartTime) / 1000
                mBindingPair.updateView {
                    tvRecordTime.text = String.format("录音时长: %02d:%02d", elapsed / 60, elapsed % 60)
                }
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }

        initFocusTarget()
        initEvent()
        recordingManager = AudioRecordingManager(this)

        mBindingPair.updateView {
            tvStatus.text = getString(R.string.audio_record_ready)
            tvRecordTime.text = ""
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                FLogger.d(TAG, "RECORD_AUDIO permission denied, finishing")
                finish()
            }
        }
    }

    private fun initFocusTarget() {
        val focusHolder = FocusHolder(true)
        mBindingPair.setLeft {
            val focusTargets = mutableListOf<FocusInfo>()
            var firstFocusView: android.view.View? = null

            fun addFocusTarget(view: android.view.View, eventHandler: (TempleAction) -> Unit, focusChangeHandler: (Boolean) -> Unit) {
                if (view.visibility != android.view.View.VISIBLE) return
                if (firstFocusView == null) firstFocusView = view
                focusTargets += FocusInfo(view, eventHandler = eventHandler, focusChangeHandler = focusChangeHandler)
            }

            addFocusTarget(
                btnRecord,
                eventHandler = { action ->
                    if (action is TempleAction.Click) {
                        toggleRecording()
                    }
                },
                focusChangeHandler = { hasFocus ->
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, btnRecord, mBindingPair.checkIsLeft(this))
                    }
                }
            )

            addFocusTarget(
                btnPlay,
                eventHandler = { action ->
                    if (action is TempleAction.Click) {
                        togglePlayback()
                    }
                },
                focusChangeHandler = { hasFocus ->
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, btnPlay, mBindingPair.checkIsLeft(this))
                    }
                }
            )

            if (focusTargets.isNotEmpty()) {
                focusHolder.addFocusTarget(*focusTargets.toTypedArray())
                firstFocusView?.let { focusHolder.currentFocus(it) }
            }
        }

        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(action)
                    }
                }
            }
        }
    }

    private fun triggerFocus(hasFocus: Boolean, view: android.view.View, isLeft: Boolean) {
        view.setBackgroundColor(
            getColor(if (hasFocus) com.ffalcon.mercury.android.sdk.R.color.color_rayneo_theme_0 else R.color.black)
        )
    }

    private fun toggleRecording() {
        if (recordingManager.isCurrentlyRecording()) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        audioSamples.clear()

        val success = recordingManager.startRecording(
            mode = AudioRecordingModeConfig.RecordingMode.VOICE_RECOGNITION,
            onAudioData = { samples, size ->
                val shorts = ShortArray(size) { i -> (samples[i] * 32768).toInt().toShort() }
                synchronized(audioSamples) {
                    audioSamples.addAll(shorts.toList())
                }
            }
        )

        if (success) {
            recordStartTime = System.currentTimeMillis()
            handler.post(updateTimeRunnable)
            mBindingPair.updateView {
                btnRecord.text = getString(R.string.stop_record)
                btnPlay.isEnabled = false
                tvStatus.text = getString(R.string.audio_recording)
            }
            FLogger.d(TAG, "Recording started")
        } else {
            FLogger.d(TAG, "Failed to start recording")
        }
    }

    private fun stopRecording() {
        recordingManager.stopRecording()
        handler.removeCallbacks(updateTimeRunnable)

        lifecycleScope.launch(Dispatchers.IO) {
            val path = savePcmAsWav()
            audioFilePath = path
            withContext(Dispatchers.Main) {
                mBindingPair.updateView {
                    btnRecord.text = getString(R.string.start_record)
                    btnPlay.isEnabled = path != null
                    tvStatus.text = if (path != null) getString(R.string.audio_recorded) else getString(R.string.audio_record_failed)
                    tvRecordTime.text = ""
                }
            }
            FLogger.d(TAG, "Recording stopped, saved to $path")
        }
    }

    private suspend fun savePcmAsWav(): String? = withContext(Dispatchers.IO) {
        try {
            val samplesCopy: List<Short>
            synchronized(audioSamples) {
                samplesCopy = audioSamples.toList()
            }
            if (samplesCopy.isEmpty()) return@withContext null

            val file = File(cacheDir, "recording_${System.currentTimeMillis()}.wav")
            val byteData = ShortArray(samplesCopy.size).let { arr ->
                samplesCopy.forEachIndexed { i, s -> arr[i] = s }
                byteArrayOf().let {
                    val bytes = ByteArray(arr.size * 2)
                    arr.forEachIndexed { i, s ->
                        bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
                    }
                    bytes
                }
            }

            FileOutputStream(file).use { fos ->
                // RIFF header
                fos.write("RIFF".toByteArray())
                fos.write(intToByteArray(36 + byteData.size))
                fos.write("WAVE".toByteArray())
                // fmt chunk
                fos.write("fmt ".toByteArray())
                fos.write(intToByteArray(16))
                fos.write(shortToByteArray(1)) // PCM
                fos.write(shortToByteArray(1)) // mono
                fos.write(intToByteArray(16000)) // sample rate
                fos.write(intToByteArray(16000 * 1 * 2)) // byte rate
                fos.write(shortToByteArray(2)) // block align
                fos.write(shortToByteArray(16)) // bits per sample
                // data chunk
                fos.write("data".toByteArray())
                fos.write(intToByteArray(byteData.size))
                fos.write(byteData)
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV", e)
            null
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }

    private fun togglePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val path = audioFilePath ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    mBindingPair.updateView {
                        btnPlay.text = getString(R.string.start_play)
                        tvStatus.text = getString(R.string.audio_play_finished)
                    }
                    FLogger.d(TAG, "Playback finished")
                }
                prepare()
                start()
            }
            mBindingPair.updateView {
                btnPlay.text = getString(R.string.stop_play)
                tvStatus.text = getString(R.string.audio_playing)
            }
            FLogger.d(TAG, "Playback started: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        mBindingPair.updateView {
            btnPlay.text = getString(R.string.start_play)
            tvStatus.text = getString(R.string.audio_recorded)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
        if (recordingManager.isCurrentlyRecording()) {
            recordingManager.stopRecording()
        }
        recordingManager.release()
        mediaPlayer?.release()
    }
}
