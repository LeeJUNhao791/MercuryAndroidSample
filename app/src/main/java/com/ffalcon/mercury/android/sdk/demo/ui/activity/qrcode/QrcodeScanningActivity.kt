package com.ffalcon.mercury.android.sdk.demo.ui.activity.qrcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
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
 */
class QrcodeScanningActivity : BaseMirrorActivity<LayoutQrcodeScanningBinding>() {

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
        
        // 设置双目视图配置
        mBindingPair.updateView {
            // 注册返回按钮的焦点信息
            focusHolder.addFocusTarget(
                FocusInfo(
                    btnBack,
                    // 事件处理器：处理 temple 触摸板的输入事件
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> finish()  // 单击返回
                            else -> Unit
                        }
                    },
                    // 焦点变化处理器：更新按钮视觉效果
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btnBack)
                        }
                    }
                )
            )
            // 设置返回按钮为当前焦点
            focusHolder.currentFocus(mBindingPair.left.btnBack)
        }

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
     * 
     * 处理相机权限请求的结果：
     * - 如果用户授予权限，启动相机扫描
     * - 如果用户拒绝权限，显示提示并退出页面
     * 
     * @param requestCode 请求码（与 requestPermissions 中的 code 对应）
     * @param permissions 请求的权限数组
     * @param grantResults 权限授予结果数组
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
     * 
     * 初始化并启动相机预览和条码分析功能：
     * - 配置扫描器支持多种条码格式
     * - 启用声音和震动反馈
     * - 设置自动停止分析和结果回调
     * - 启动相机并开始分析图像
     */
    private fun startCameraScan() {
        mBindingPair.updateView {
            try {
                // 如果相机扫描器已存在，重新启用分析
                if (cameraScan != null) {
                    cameraScan?.setAnalyzeImage(true)
                    return@updateView
                }

                // 创建并配置相机扫描器
                cameraScan = BaseCameraScan<List<Barcode>>(
                    this@QrcodeScanningActivity,
                    previewView  // 相机预览视图
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
            } catch (e: Exception) {
                // 相机启动失败，记录错误并显示提示
                Log.e(TAG, "Camera start failed", e)
                Toast.makeText(
                    this@QrcodeScanningActivity,
                    "Camera start failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 创建条码分析器
     * 
     * 配置支持的条码格式，包括：
     * - QR Code（二维码）
     * - Code 128、Code 39、Code 93、Codabar（一维码）
     * - EAN-13、EAN-8、UPC-A、UPC-E（商品条码）
     * - ITF（交叉二五码）
     * 
     * @return 配置好的条码扫描分析器
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
     * 
     * 处理相机扫描器返回的分析结果：
     * - 如果正在显示结果，忽略新的扫描结果
     * - 提取第一个识别到的条码内容
     * - 如果结果为空，重启扫描
     * - 如果结果有效，显示结果并在 2 秒后重启扫描
     * 
     * @param result 分析结果，包含识别到的条码列表
     */
    private fun onScanResultCallback(result: AnalyzeResult<List<Barcode>>) {
        // 如果正在显示结果，忽略新的扫描结果，避免重复显示
        if (isShowingResult) return

        // 提取第一个识别到的条码内容（优先使用 displayValue，否则使用 rawValue）
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
     * 
     * 在眼镜屏幕上显示扫描结果，2 秒后自动隐藏并重启扫描：
     * - 显示结果文本和成功提示
     * - 延迟 2 秒
     * - 隐藏结果并清空文本
     * - 重置状态标志，重启扫描
     * 
     * @param text 扫描到的条码内容
     */
    private fun showResultWithTimeout(text: String) {
        // 如果正在显示结果，避免重复显示
        if (isShowingResult) return

        isShowingResult = true
        lifecycleScope.launch {
            // 在双目显示中更新视图，显示扫描结果
            mBindingPair.updateView {
                tvResult.visibility = View.VISIBLE
                tvResult.text = "Scan result:\n$text"
                // 在眼镜上显示成功提示
                FToast.show("Scan success")
            }

            // 延迟 2 秒后隐藏结果
            delay(2000)

            // 隐藏结果视图并清空文本
            mBindingPair.updateView {
                tvResult.visibility = View.GONE
                tvResult.text = ""
            }

            // 重置状态标志
            isShowingResult = false
            // 重启扫描
            restartScanning()
        }
    }

    /**
     * 重启扫描
     * 
     * 重新启动相机图像分析，继续扫描新的条码
     * 在显示结果后或扫描失败后调用
     */
    private fun restartScanning() {
        try {
            // 重新启用图像分析
            cameraScan?.setAnalyzeImage(true)
        } catch (e: Exception) {
            // 重启扫描失败，记录错误
            Log.e(TAG, "Restart scan failed", e)
        }
    }

    /**
     * 销毁活动时的资源清理
     * 
     * 停止相机并释放相关资源，避免内存泄漏：
     * - 停止相机预览
     * - 清空相机扫描器引用
     * - 捕获异常防止崩溃
     */
    override fun onDestroy() {
        try {
            // 停止相机
            cameraScan?.stopCamera()
            // 清空引用，帮助垃圾回收
            cameraScan = null
        } catch (e: Exception) {
            // 释放相机失败，记录错误
            Log.e(TAG, "Release camera failed", e)
        }
        super.onDestroy()
    }
}
