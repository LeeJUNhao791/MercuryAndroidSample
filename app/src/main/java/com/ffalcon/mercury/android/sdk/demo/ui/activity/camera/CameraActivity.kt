package com.ffalcon.mercury.android.sdk.demo.ui.activity.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityCameraBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.util.FLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


/**
 * 相机预览活动
 * 
 * 使用 Camera2 API 实现相机预览和拍照功能：
 * - 支持 VGA (640x480) 和 Full HD (1920x1080) 两种分辨率
 * - 双目显示（左眼/右眼）同步预览
 * - 通过 temple 触摸板单击拍照，双击退出
 * - 实时显示缩略图预览
 * 
 * 技术要点：
 * - 使用 ImageReader 捕获 YUV_420_888 格式的图像数据
 * - 将 YUV 数据转换为 NV21 格式，再压缩为 JPEG
 * - 设置帧率范围 (5-10 fps) 以优化性能
 */
class CameraActivity : BaseMirrorActivity<ActivityCameraBinding>() {
    /** 是否使用 VGA 分辨率（false 则为 1080p） */
    private var isVGA = false
    
    /** Surface 列表，存储左右眼的预览表面 */
    private val surfaceList = mutableListOf<Surface>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从 Intent 获取是否使用 VGA 分辨率的参数
        isVGA = intent.getBooleanExtra("isVGA", false)
        // 启动后台线程用于处理相机数据
        backHandlerThread.start()

        // 监听 temple 触摸板事件
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.Click -> {
                            // 单击触发拍照
                            takePhoto.set(true)
                        }

                        is TempleAction.DoubleClick -> {
                            // 双击退出页面
                            finish()
                        }

                        else -> {
                            // 其他事件不处理
                        }
                    }
                }
            }
        }

        // 设置相机预览视图的 SurfaceTexture 监听器
        mBindingPair.updateView {
            this.cameraPreview.surfaceTextureListener = object : SurfaceTextureListener {
                var mSurface: Surface? = null
                
                /**
                 * SurfaceTexture 可用时调用
                 * 
                 * 设置默认缓冲区大小并创建 Surface，当左右眼两个 Surface 都准备好后启动相机
                 */
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    Log.d("Camera onSurfaceTextureAvailable", "width=$width,height=$height")
                    // Ignore ConstraintLayout calculated size, use fixed resolution supported by camera
                    // 忽略 ConstraintLayout 计算的大小，使用相机支持的固定分辨率
                    if (isVGA) {
                        // 设置 VGA 分辨率 (640x480)
                        surface.setDefaultBufferSize(
                            640,
                            480
                        )
                    } else {
                        // 设置 Full HD 分辨率 (1920x1080)
                        surface.setDefaultBufferSize(
                            1920,
                            1080
                        )
                    }


                    // 创建 Surface 并添加到列表
                    val surface2 = Surface(cameraPreview.surfaceTexture)
                    surfaceList.add(surface2)
                    mSurface = surface2
                    // 当左右眼两个 Surface 都准备好后，延迟启动相机
                    if (surfaceList.size == 2) {
                        lifecycleScope.launch {
                            delay(100L)  // 等待 100ms 确保 Surface 完全初始化
                            setupCamera2()  // 设置并启动 Camera2
                        }
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    // Surface 大小变化时的回调（本例中不处理）
                }

                /**
                 * SurfaceTexture 销毁时调用
                 * 
                 * 释放 Surface 资源
                 */
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    mSurface?.release()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    // SurfaceTexture 更新时的回调（本例中不处理）
                }

            }
        }

        //enumerateCameraResolutions()  // 枚举相机分辨率（已注释）
        // 打印相机能力信息（用于调试）
        printCameraCapabilities()
    }

    override fun onStop() {
        super.onStop()
        // Activity 停止时关闭相机，释放资源
        closeCamera()
    }

    /** 相机设备实例 */
    private var cameraDevice: CameraDevice? = null
    
    /** 相机管理器，用于管理相机设备 */
    private lateinit var cameraManager: CameraManager
    
    /** 原子布尔值，用于线程安全的状态标记 */
    private val atomicBoolean = AtomicBoolean(false)
    
    /** 相机捕获会话，用于发送捕获请求 */
    private var cameraCaptureSession: CameraCaptureSession? = null
    
    /** 后台 Handler，用于处理相机回调 */
    private lateinit var backHandler: Handler
    
    /** 图像读取器，用于捕获图像数据 */
    private var imageReader: ImageReader? = null
    
    /** 相机协程任务，用于异步操作 */
    private var cameraJob: Job? = null
    
    /** 拍照标志，原子操作保证线程安全 */
    var takePhoto = AtomicBoolean(false)

    /**
     * 后台线程，用于处理相机相关的耗时操作
     * 
     * 在 looper 准备完成后创建 Handler
     */
    private val backHandlerThread = object : HandlerThread("background") {
        override fun onLooperPrepared() {
            super.onLooperPrepared()
            backHandler = Handler(this.looper)
        }
    }
    
    /**
     * 相机设备状态回调
     * 
     * 处理相机的打开、断开连接和错误状态：
     * - onOpened: 相机成功打开，设置 ImageReader
     * - onDisconnected: 相机断开连接，清理资源
     * - onError: 相机错误（本例中不处理）
     */
    private val stateCallback = object : CameraDevice.StateCallback() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onOpened(p0: CameraDevice) {
            // 在协程中异步设置 ImageReader
            cameraJob = lifecycleScope.launch {
                cameraDevice = p0
                delay(100L)  // 等待 100ms 确保相机完全初始化
                if (cameraDevice == p0) {
                    setUpImageReader(p0)  // 设置图像读取器
                }
            }
        }

        override fun onDisconnected(p0: CameraDevice) {
            // 相机断开连接，清理引用并取消任务
            cameraDevice = null
            cameraJob?.cancel()
        }


        override fun onError(p0: CameraDevice, p1: Int) {
            // 相机错误回调（本例中不处理）
        }

    }

    /**
     * 设置并启动 Camera2
     * 
     * 根据分辨率模式（VGA 或 1080p）选择对应的相机 ID，并打开相机。
     * - VGA 模式通常使用后置相机列表中的第二个相机（索引 1）
     * - 1080p 模式使用第一个可用相机（索引 0）
     */
    @SuppressLint("MissingPermission")
    private fun setupCamera2() {
        // 获取相机管理器服务
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // 根据是否使用 VGA 分辨率选择相机 ID
        val cameraId =
            if (isVGA) cameraManager.cameraIdList[1] else cameraManager.cameraIdList.first()

        // 打开指定 ID 的相机，状态回调由 stateCallback 处理
        cameraManager.openCamera(cameraId, stateCallback, null)
    }

    /**
     * 记录 ImageReader 首次可用时间，用于忽略初始帧
     * 
     * -1L 表示尚未初始化，初始化后记录当前时间戳。
     * 用于在相机启动初期跳过不稳定的图像数据。
     */
    var openTime = -1L

    /**
     * 设置 ImageReader 并配置相机捕获会话
     * 
     * 主要功能：
     * 1. 创建 ImageReader 实例，用于接收 YUV_420_888 格式的图像数据
     * 2. 设置图像可用监听器，处理拍照逻辑
     * 3. 构建捕获请求，将图像输出到 ImageReader 和左右眼 Surface
     * 4. 创建相机捕获会话，开始预览
     * 
     * @param camera 已打开的 CameraDevice 实例
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setUpImageReader(camera: CameraDevice) {
        // 关闭旧的 ImageReader（如果存在）
        imageReader?.close()
        
        // 根据分辨率模式创建新的 ImageReader
        // 参数：宽度, 高度, 图像格式(YUV_420_888), 最大图像数(10)
        imageReader = if (isVGA)
            ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 10)
        else
            ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 10)

        // 保存相机设备引用
        cameraDevice = camera
        // 重置时间戳
        openTime = -1L
        
        // 设置图像可用监听器，当有新图像数据时触发
        imageReader?.setOnImageAvailableListener({ reader ->
            // 首次调用时记录时间，并跳过该帧
            if (openTime == -1L) {
                openTime = System.currentTimeMillis()
                return@setOnImageAvailableListener
            }
            
            // 跳过启动后 1 秒内的图像，避免使用不稳定的初始帧
            if ((System.currentTimeMillis() - openTime) < 1000L) {
                return@setOnImageAvailableListener
            }
            
            // 获取最新的图像数据
            val image = reader.acquireLatestImage() ?: run {
                return@setOnImageAvailableListener
            }

            // 检查是否需要拍照
            if (takePhoto.get()) {
                // 重置拍照标志
                takePhoto.set(false)
                
                // 将 YUV 图像转换为 Bitmap
                val bitmap = imageToBitmap(image)
                bitmap?.let {
                    // 在主线程更新 UI，显示缩略图
                    runOnUiThread {
                        mBindingPair.updateView {
                            this.thumbnailView.setImageBitmap(it)
                        }
                    }
                } ?: Log.e("CameraActivity", "Image convert to bitmap failed! ")
            }
            
            // 释放图像资源
            image.close()

        }, backHandler) // 使用后台 Handler 处理图像回调

        // 构建预览捕获请求
        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply {
                // 添加 ImageReader 表面作为目标，用于捕获图像数据
                addTarget(imageReader!!.surface)
                
                // 添加左右眼预览表面
                for (item in surfaceList) {
                    addTarget(item)
                }
                
                // 设置自动曝光的目标帧率范围为 5-10 fps，以优化性能和功耗
                val fpsRange = Range(5, 10)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }

        // 配置输出表面
        val outputConfig = OutputConfiguration(imageReader!!.surface)
        val outputConfig2 = OutputConfiguration(surfaceList[0])
        val outputConfig3 = OutputConfiguration(surfaceList[1])
        val outputs = listOf(outputConfig, outputConfig2, outputConfig3)
        
        // 创建会话配置
        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, // 常规会话类型
            outputs, // 输出配置列表
            Executors.newSingleThreadExecutor(), // 单线程执行器处理会话回调
            object : CameraCaptureSession.StateCallback() {
                /**
                 * 会话配置成功时调用
                 * 
                 * 设置重复请求以开始持续预览
                 */
                override fun onConfigured(session: CameraCaptureSession) {
                    // 发送重复捕获请求，开始预览
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    // 保存会话引用
                    cameraCaptureSession = session
                }

                /**
                 * 会话配置失败时调用
                 * 
                 * 本例中不做特殊处理
                 */
                override fun onConfigureFailed(session: CameraCaptureSession) {
                }
            }
        )
        
        // 创建相机捕获会话
        camera.createCaptureSession(sessionConfig)
    }

    /**
     * 关闭相机并释放相关资源
     * 
     * 按顺序关闭：
     * 1. 相机捕获会话
     * 2. 相机设备
     * 3. 图像读取器 (ImageReader)
     */
    private fun closeCamera() {
        try {
            // 关闭相机捕获会话
            if (null != cameraCaptureSession) {
                cameraCaptureSession!!.close()
                cameraCaptureSession = null
            }
            
            // 关闭相机设备
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            
            // 关闭 ImageReader，释放图像缓冲区
            if (null != imageReader) {
                imageReader?.close()
                imageReader = null
            }
            
            // 重置状态标志
            atomicBoolean.set(false)
        } catch (e: Exception) {
            // 忽略异常，确保资源尽可能释放
        } finally {
            // 确保状态标志被重置
            atomicBoolean.set(false)
        }
    }

    /**
     * 将 YUV_420_888 格式的 Image 转换为 Bitmap
     * 
     * 转换步骤：
     * 1. 提取 Y、U、V 平面数据
     * 2. 重组为 NV21 格式（YUV 的一种常见排列方式）
     * 3. 使用 YuvImage 压缩为 JPEG
     * 4. 解码 JPEG 字节数组为 Bitmap
     * 
     * @param image 输入的 YUV 图像
     * @return 转换后的 Bitmap，失败则返回 null
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        
        // 获取 Y 平面数据
        val buffer: ByteBuffer = planes[0].buffer
        val ySize = buffer.remaining()

        // 获取 U 平面数据
        val uBuffer: ByteBuffer = planes[1].buffer
        val uSize = uBuffer.remaining()

        // 获取 V 平面数据
        val vBuffer: ByteBuffer = planes[2].buffer
        val vSize = vBuffer.remaining()

        // 创建 NV21 格式字节数组 (Y + V + U)
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // 复制 Y 数据
        buffer.get(nv21, 0, ySize)
        
        // 复制 V 数据（NV21 格式要求 V 在 U 之前）
        vBuffer.get(nv21, ySize, vSize)
        
        // 复制 U 数据
        uBuffer.get(nv21, ySize + vSize, uSize)

        // 创建 YuvImage 对象
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        
        // 压缩为 JPEG
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        
        // 获取 JPEG 字节数组
        val imageBytes = out.toByteArray()
        out.close()
        
        // 解码为 Bitmap
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * 枚举所有相机的支持分辨率
     * 
     * 用于调试目的，打印每个相机 ID 支持的预览尺寸和图片尺寸。
     * 此方法在当前活动中未被调用，仅作为工具函数保留。
     */
    private fun enumerateCameraResolutions() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList

        for (cameraId in cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (map != null) {
                // 获取支持的预览尺寸
                val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
                // 获取支持的图片尺寸
                val pictureSizes = map.getOutputSizes(ImageFormat.JPEG)

                Log.d("camera", "Camera ID: $cameraId")
                Log.d("camera", "Supported Preview Sizes:")
                for (size in previewSizes) {
                    Log.d("camera", "  ${size.width}x${size.height}")
                }

                Log.d("camera", "Supported Picture Sizes:")
                for (size in pictureSizes) {
                    Log.d("camera", "  ${size.width}x${size.height}")
                }
            }
        }
    }

    /**
     * 打印相机支持的所有参数和参数范围
     * 
     * 遍历所有可用相机，详细输出其硬件能力，包括：
     * - 基本信息（朝向、传感器方向、硬件级别）
     * - 分辨率支持
     * - 曝光参数（ISO、曝光时间、补偿）
     * - 对焦参数
     * - 白平衡参数
     * - 图像质量参数（场景模式、特效、RAW 支持）
     * - 闪光灯信息
     * - 帧率范围
     */
    @SuppressLint("LongLogTag")
    private fun printCameraCapabilities() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList

        Log.d("CameraCapabilities", "=== Detected ${cameraIdList.size} cameras ===")

        for (cameraId in cameraIdList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                Log.d("CameraCapabilities", "\n📷 Camera ID: $cameraId")
                Log.d("CameraCapabilities", "----------------------------------------")

                // 1. 基本相机信息
                printBasicInfo(characteristics, cameraId)

                // 2. 分辨率信息
                printResolutionInfo(characteristics)

                // 3. 曝光相关参数
                printExposureCapabilities(characteristics)

                // 4. 对焦相关参数
                printFocusCapabilities(characteristics)

                // 5. 白平衡相关参数
                printWhiteBalanceCapabilities(characteristics)

                // 6. 其他图像质量参数
                printImageQualityCapabilities(characteristics)

                // 7. 闪光灯信息
                printFlashCapabilities(characteristics)

                // 8. 帧率信息
                printFrameRateCapabilities(characteristics)

            } catch (e: Exception) {
                Log.e("CameraCapabilities", "Failed to get camera $cameraId information: ${e.message}")
            }
        }
    }

    /**
     * 打印相机基本信息
     * 
     * @param characteristics 相机特征对象
     * @param cameraId 相机 ID
     */
    private fun printBasicInfo(characteristics: CameraCharacteristics, cameraId: String) {
        // 获取相机朝向
        val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val lensFacingStr = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }
        Log.d("CameraCapabilities", "📱 Camera type: $lensFacingStr")

        // 获取传感器方向
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        Log.d("CameraCapabilities", "🔄 Sensor orientation: $sensorOrientation°")

        // 获取硬件支持级别
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val levelStr = when (hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        Log.d("CameraCapabilities", "⚙️ Hardware support level: $levelStr")
    }

    /**
     * 打印分辨率支持信息
     * 
     * 输出预览分辨率和图片分辨率列表，按像素总数降序排列，仅显示前 10 个。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printResolutionInfo(characteristics: CameraCharacteristics) {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

        Log.d("CameraCapabilities", "\n📐 Resolution support:")

        // 预览分辨率
        val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
        Log.d("CameraCapabilities", "  Preview resolution (${previewSizes.size} types):")
        previewSizes.sortedByDescending { it.width * it.height }
            .take(10) // 仅显示前 10 个
            .forEach { size ->
                Log.d(
                    "CameraCapabilities",
                    "    ${size.width} x ${size.height} (${
                        String.format(
                            "%.1f",
                            size.width * size.height / 1000000.0
                        )
                    }MP)"
                )
            }

        // 照片分辨率
        val photoSizes = map.getOutputSizes(ImageFormat.JPEG)
        Log.d("CameraCapabilities", "  Photo resolution (${photoSizes.size} types):")
        photoSizes.sortedByDescending { it.width * it.height }
            .take(10)
            .forEach { size ->
                Log.d(
                    "CameraCapabilities",
                    "    ${size.width} x ${size.height} (${
                        String.format(
                            "%.1f",
                            size.width * size.height / 1000000.0
                        )
                    }MP)"
                )
            }
    }

    /**
     * 打印曝光相关能力
     * 
     * 包括 ISO 范围、曝光时间范围、曝光补偿范围和可用的自动曝光模式。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printExposureCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n☀️ Exposure parameters:")

        // ISO 范围
        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        Log.d("CameraCapabilities", "  ISO range: ${isoRange?.lower} - ${isoRange?.upper}")

        // 曝光时间范围（纳秒）
        val exposureTimeRange =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        exposureTimeRange?.let {
            val minMs = String.format("%.3f", it.lower / 1000000.0)
            val maxMs = String.format("%.3f", it.upper / 1000000.0)
            Log.d("CameraCapabilities", "  Exposure time: $minMs ms - $maxMs ms")
        }

        // 曝光补偿范围
        val exposureCompensationRange =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val exposureCompensationStep =
            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        Log.d(
            "CameraCapabilities",
            "  Exposure compensation: ${exposureCompensationRange?.lower} - ${exposureCompensationRange?.upper} (step: $exposureCompensationStep)"
        )

        // 支持的自动曝光模式
        val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        Log.d("CameraCapabilities", "  AE modes: ${aeModes?.contentToString()}")
    }

    /**
     * 打印对焦相关能力
     * 
     * 包括可用的对焦模式、最小对焦距离和对焦距离校准类型。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printFocusCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n🎯 Focus parameters:")

        // 支持的对焦模式
        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        Log.d("CameraCapabilities", "  Focus modes: ${afModes?.contentToString()}")

        // 最小对焦距离
        val minFocusDistance =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        Log.d("CameraCapabilities", "  Minimum focus distance: $minFocusDistance")

        // 对焦距离校准类型
        val focusDistanceRange =
            characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
        val calibrationStr = when (focusDistanceRange) {
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> "APPROXIMATE"
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> "CALIBRATED"
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> "UNCALIBRATED"
            else -> "UNKNOWN"
        }
        Log.d("CameraCapabilities", "  Focus distance calibration: $calibrationStr")
    }

    /**
     * 打印白平衡相关能力
     * 
     * 包括可用的自动白平衡模式。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printWhiteBalanceCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n🎨 White balance parameters:")

        // 支持的自动白平衡模式
        val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        Log.d("CameraCapabilities", "  White balance modes: ${awbModes?.contentToString()}")
    }

    /**
     * 打印图像质量参数
     * 
     * 包括场景模式、特效模式以及是否支持 RAW 格式。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printImageQualityCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n🖼️ Image quality parameters:")

        // 支持的场景模式
        val sceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
        Log.d("CameraCapabilities", "  Scene modes: ${sceneModes?.contentToString()}")

        // 支持的特效模式
        val effectModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
        Log.d("CameraCapabilities", "  Effect modes: ${effectModes?.contentToString()}")

        // 是否支持 RAW 格式
        val rawSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
        Log.d(
            "CameraCapabilities",
            "  RAW format support: ${if (rawSizes != null && rawSizes.isNotEmpty()) "Yes" else "No"}"
        )
    }

    /**
     * 打印闪光灯信息
     * 
     * @param characteristics 相机特征对象
     */
    private fun printFlashCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n💡 Flash information:")

        val flashAvailable =
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        Log.d("CameraCapabilities", "  Flash available: $flashAvailable")
    }

    /**
     * 打印帧率信息
     * 
     * 输出支持的自动曝光目标帧率范围。
     * 
     * @param characteristics 相机特征对象
     */
    private fun printFrameRateCapabilities(characteristics: CameraCharacteristics) {
        Log.d("CameraCapabilities", "\n🎞️ Frame rate information:")

        val fpsRanges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        Log.d("CameraCapabilities", "  Supported frame rate ranges:")
        fpsRanges?.forEach { range ->
            Log.d("CameraCapabilities", "    ${range.lower} - ${range.upper} fps")
        }
    }
}