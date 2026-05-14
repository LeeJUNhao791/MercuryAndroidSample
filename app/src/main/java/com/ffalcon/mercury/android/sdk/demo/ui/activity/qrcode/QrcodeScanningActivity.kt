package com.ffalcon.mercury.android.sdk.demo.ui.activity.qrcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.R
import com.ffalcon.mercury.android.sdk.demo.databinding.LayoutQrcodeScanningBinding
import com.ffalcon.mercury.android.sdk.focus.reqFocus
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseEventActivity
import com.ffalcon.mercury.android.sdk.ui.toast.FToast
import com.ffalcon.mercury.android.sdk.ui.util.FixPosFocusTracker
import com.ffalcon.mercury.android.sdk.ui.util.FocusHolder
import com.ffalcon.mercury.android.sdk.ui.util.FocusInfo
import com.google.mlkit.vision.barcode.common.Barcode
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.BaseCameraScan
import com.king.camera.scan.CameraScan
import com.king.mlkit.vision.barcode.analyze.BarcodeScanningAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 二维码扫描活动
 * 
 * 支持多种条码格式的扫描：QR Code、Code 128、Code 39、EAN-13 等
 * 使用 ML Kit 进行条码识别，集成相机预览和 temple 触摸板控制
 * 
 * 注意：继承自 BaseEventActivity，使用 MirroringView 实现双目同步显示
 */
class QrcodeScanningActivity : BaseEventActivity() {

    /** 视图绑定对象 */
    private lateinit var binding: LayoutQrcodeScanningBinding

    /** 固定位置焦点追踪器，用于管理返回按钮的焦点 */
    private var fixPosFocusTracker: FixPosFocusTracker? = null
    
    /** 相机扫描控制器，负责相机预览和条码分析 */
    private var cameraScan: CameraScan<List<Barcode>>? = null
    
    /** 标记是否正在显示扫描结果，避免重复显示 */
    private var isShowingResult = false

    companion object {
        /** 日志标签 */
        private const val TAG = "QrcodeScanningActivity"
        
        /** 相机权限请求码 */
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化视图绑定
        binding = LayoutQrcodeScanningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化焦点目标（返回按钮）
        initFocusTarget()
        // 初始化事件监听（temple 触摸板事件）
        initEvent()
        // 检查并请求相机权限
        checkCameraPermission()
    }

    /**
     * 初始化焦点目标
     * 
     * 为返回按钮注册焦点信息：
     * - 单击：退出当前页面
     * - 焦点变化：更新按钮背景色（紫色/黑色）
     */
    private fun initFocusTarget() {
        // 创建焦点持有者，参数 true 表示允许循环聚焦
        val focusHolder = FocusHolder(true)
        
        // 注册返回按钮的焦点信息
        focusHolder.addFocusTarget(
            FocusInfo(
                binding.btnBack,
                // 事件处理器：处理 temple 触摸板的输入事件
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> finish()  // 单击返回
                        else -> Unit
                    }
                },
                // 焦点变化处理器：更新按钮视觉效果
                focusChangeHandler = { hasFocus ->
                    triggerFocus(hasFocus, binding.btnBack)
                    triggerFocus(hasFocus, binding.btnBackRight)
                }
            )
        )
        // 设置返回按钮为当前焦点
        focusHolder.currentFocus(binding.btnBack)

        // 创建并激活固定位置焦点追踪器
        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            // 请求初始焦点
            focusObj.reqFocus()
        }
    }

    /**
     * 初始化事件监听
     * 
     * 监听 temple 触摸板的事件流，在 Activity 处于 RESUMED 状态时持续收集事件：
     * - 双击：退出当前页面（finish）
     * - 其他事件（单击、滑动等）：交给焦点追踪器处理
     */
    private fun initEvent() {
        lifecycleScope.launch {
            // 只在 Activity 恢复状态下收集事件，避免后台运行时响应触摸
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 收集 temple 触摸板的状态流
                templeActionViewModel.state.collect { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> finish()  // 双击返回
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(action)  // 其他事件交由焦点系统处理
                    }
                }
            }
        }
    }

    /**
     * 触发焦点视觉效果
     * 
     * 当视图获得或失去焦点时调用，更新视图的背景色：
     * - 获得焦点：使用紫色（purple_200）
     * - 失去焦点：使用黑色
     * 
     * @param hasFocus 是否获得焦点
     * @param view 目标视图
     */
    private fun triggerFocus(hasFocus: Boolean, view: View) {
        view.setBackgroundColor(
            getColor(if (hasFocus) R.color.purple_200 else R.color.black)
        )
    }

    /**
     * 检查相机权限
     * 
     * 在启动相机之前检查是否已获得相机权限：
     * - 如果已授权，直接启动相机扫描
     * - 如果未授权，请求相机权限
     */
    private fun checkCameraPermission() {
        // 检查是否已获得相机权限
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            // 权限已授予，启动相机扫描
            startCameraScan()
        } else {
            // 请求相机权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    /**
     * 权限请求结果回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 检查是否是相机权限请求
        if (requestCode != REQUEST_CAMERA_PERMISSION) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，启动相机扫描
            startCameraScan()
        } else {
            // 权限被拒绝，显示提示并退出
            Toast.makeText(this, "Camera permission is required for scanning", Toast.LENGTH_LONG)
                .show()
            finish()
        }
    }

    /**
     * 启动相机扫描
     */
    private fun startCameraScan() {
        try {
            // 如果相机扫描器已存在，重新启用分析
            if (cameraScan != null) {
                cameraScan?.setAnalyzeImage(true)
                return
            }

            // 创建并配置相机扫描器
            cameraScan = BaseCameraScan<List<Barcode>>(
                this,
                binding.previewView  // 相机预览视图
            ).apply {
                setPlayBeep(true)  // 启用扫描成功时的声音提示
                setVibrate(true)   // 启用扫描成功时的震动反馈
                setAutoStopAnalyze(true)  // 扫描成功后自动停止分析
                setAnalyzeImage(true)     // 开始分析图像
                setAnalyzer(createBarcodeAnalyzer())  // 设置条码分析器
                // 设置扫描结果回调
                setOnScanResultCallback { result ->
                    onScanResultCallback(result)
                }
                startCamera()  // 启动相机
            }

            // 配置双目镜像显示
            setupMirroring()

        } catch (e: Exception) {
            // 相机启动失败，记录错误并显示提示
            Log.e(TAG, "Camera start failed", e)
            Toast.makeText(
                this,
                "Camera start failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 配置双目镜像显示
     * 
     * 将左眼的 PreviewView 画面镜像到右眼的 MirroringView
     */
    private fun setupMirroring() {
        // 等待 PreviewView 加载完成并确保其子视图（TextureView）已创建
        binding.previewView.post {
            // PreviewView 在 compatible 模式下使用 TextureView 作为第一个子视图
            val textureView = binding.previewView.getChildAt(0) as? TextureView
            if (textureView != null) {
                // 设置镜像源并开始镜像
                binding.mirrorView.setSource(textureView)
                binding.mirrorView.startMirroring()
            } else {
                Log.e(TAG, "Failed to find TextureView in PreviewView")
            }
        }
    }

    /**
     * 创建条码分析器
     */
    private fun createBarcodeAnalyzer(): BarcodeScanningAnalyzer {
        return BarcodeScanningAnalyzer(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_ITF
        )
    }

    /**
     * 扫描结果回调
     */
    private fun onScanResultCallback(result: AnalyzeResult<List<Barcode>>) {
        // 如果正在显示结果，忽略新的扫描结果，避免重复显示
        if (isShowingResult) return

        // 提取第一个识别到的条码内容
        val text = result.result
            ?.firstOrNull()
            ?.let { barcode -> barcode.displayValue ?: barcode.rawValue }

        // 如果结果为空，重启扫描
        if (text.isNullOrBlank()) {
            Log.d(TAG, "Scan result is empty")
            restartScanning()
            return
        }

        Log.d(TAG, "Scan success: $text")
        // 关闭闪光灯
        cameraScan?.enableTorch(false)
        // 显示扫描结果
        showResultWithTimeout(text)
    }

    /**
     * 显示扫描结果（带超时自动隐藏）
     */
    private fun showResultWithTimeout(text: String) {
        if (isShowingResult) return

        isShowingResult = true
        lifecycleScope.launch {
            // 更新 UI 显示扫描结果
            binding.tvResult.visibility = View.VISIBLE
            binding.tvResult.text = "Scan result:\n$text"
            binding.tvResultRight.visibility = View.VISIBLE
            binding.tvResultRight.text = "Scan result:\n$text"
            FToast.show("Scan success")

            // 延迟 2 秒后隐藏结果
            delay(2000)

            // 隐藏结果视图并清空文本
            binding.tvResult.visibility = View.GONE
            binding.tvResult.text = ""
            binding.tvResultRight.visibility = View.GONE
            binding.tvResultRight.text = ""

            // 重置状态标志
            isShowingResult = false
            // 重启扫描
            restartScanning()
        }
    }

    /**
     * 重启扫描
     */
    private fun restartScanning() {
        try {
            cameraScan?.setAnalyzeImage(true)
        } catch (e: Exception) {
            Log.e(TAG, "Restart scan failed", e)
        }
    }

    /**
     * 销毁活动时的资源清理
     */
    override fun onDestroy() {
        // 停止镜像功能
        binding.mirrorView.stopMirroring()
        try {
            // 停止相机
            cameraScan?.stopCamera()
            cameraScan = null
        } catch (e: Exception) {
            Log.e(TAG, "Release camera failed", e)
        }
        super.onDestroy()
    }
}
