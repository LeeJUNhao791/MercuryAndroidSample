# 眼镜高级功能开发（Camera/Audio/IMU/BLE/GPS）

## 使用场景
开发涉及 Camera、音频录制、IMU 传感器、BLE 连接或 GPS 推流的眼镜端功能时运行此 Prompt。

**触发词**：Camera开发、音频录制、麦克风、IMU、陀螺仪、传感器、BLE连接、GPS定位

## 高级功能清单

根据 RayNeo SDK 文档，以下功能需要特别处理：

| 功能模块 | 涉及组件 | X2/X3 差异 |
|---------|---------|-----------|
| Camera 开发 | Camera2 API, CameraManager | X3 有额外 VGA 摄像头 |
| 音频录制 | AudioManager.setParameters | X2 使用 setParameters, X3 有新 API |
| IMU 传感器 | SensorManager, TYPE_GAME_ROTATION_VECTOR | 无差异 |
| BLE 连接 | MobileState | X3 更稳定 |
| GPS 推流 | IPC SDK | 需要 IPC SDK |

---

## 1. Camera 开发规范

### Camera2 API 标准用法

```kotlin
class CameraActivity : BaseMirrorActivity<LayoutCameraBinding>() {

    private lateinit var cameraManager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        enumerateCameraResolutions()
        initCamera()
    }

    private fun enumerateCameraResolutions() {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.getOutputSizes(SurfaceTexture::class.java)?.forEach { size ->
                FLogger.d("Camera $cameraId: ${size.width}x${size.height}")
            }
        }
    }

    private fun initCamera() {
        // Camera2 标准初始化流程
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }.build()
                camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(previewRequest, null, null)
                    }
                }, null)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, null)
    }
}
```

### X3 VGA 摄像头（空间定位）

X3 设备有独立的 VGA 摄像头（Camera ID 通常为 1），专门用于空间定位。

```kotlin
private fun checkVgaCamera() {
    if (!DeviceUtil.isX3Device()) {
        FLogger.w("VGA camera only available on X3")
        return
    }

    // Camera ID 1 通常是 VGA 摄像头
    val vgaCameraId = cameraManager.cameraIdList.getOrNull(1) ?: return

    val characteristics = cameraManager.getCameraCharacteristics(vgaCameraId)
    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

    // VGA 分辨率列表
    val vgaSizes = characteristics
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(SurfaceTexture::class.java)
        ?.filter { it.width <= 640 }

    FLogger.d("VGA Camera sizes: ${vgaSizes?.map { "${it.width}x${it.height}" }}")
}
```

### Camera 权限检查

```kotlin
private fun checkCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}

private fun requestCameraPermission() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        REQUEST_CAMERA_PERMISSION
    )
}

override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty()
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        initCamera()
    }
}
```

---

## 2. 音频录制规范

### X2 MIC 模式配置

X2 使用 `AudioManager.setParameters` 配置 MIC 模式。

```kotlin
class AudioRecordingActivity : BaseMirrorActivity<LayoutAudioBinding>() {

    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun setMicMode(mode: MicMode) {
        val param = when (mode) {
            MicMode.CAMCORDER -> "audio_source_record=camcorder"  // 2颗mic，全收
            MicMode.TRANSLATION -> "audio_source_record=translation"  // 3颗mic，不收佩戴者
            MicMode.VOICE_ASSISTANT -> "audio_source_record=voiceassistant"  // 2颗mic，主要收佩戴者
            MicMode.OFF -> "audio_source_record=off"  // 关闭
        }
        audioManager.setParameters(param)
    }

    override fun onPause() {
        super.onPause()
        // 退出时必须关闭 MIC
        setMicMode(MicMode.OFF)
    }
}

enum class MicMode {
    CAMCORDER,
    TRANSLATION,
    VOICE_ASSISTANT,
    OFF
}
```

### X3 音频（参考 IPC SDK）

X3 使用新的音频 API，具体实现参考 IPC SDK 文档。

```kotlin
// X3 音频示例（伪代码）
if (DeviceUtil.isX3Device()) {
    // 使用 IPC SDK 的新音频 API
    // 参考: com.ffalcon.mercury.ipc.audio.*
} else {
    // X2: 使用传统 setParameters
    setMicMode(MicMode.TRANSLATION)
}
```

---

## 3. IMU 传感器规范

### Game Rotation Vector 传感器

推荐使用 Game Rotation Vector 传感器获取设备姿态，它不使用磁力计，不受磁场干扰。

```kotlin
class IMUActivity : BaseMirrorActivity<LayoutImuBinding>(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gameRotationVectorSensor: Sensor? = null

    // 四元数转欧拉角的结果
    private val eulerAngles = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gameRotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    override fun onResume() {
        super.onResume()
        // 推荐使用 SENSOR_DELAY_FASTEST 获取最高频率
        gameRotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        // 必须取消注册，否则后台耗电
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // 四元数: [x*sin(θ/2), y*sin(θ/2), z*sin(θ/2), cos(θ/2)]
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val w = event.values[3]

            // 转换为欧拉角
            quaternionToEuler(x, y, z, w)

            mBindingPair.updateView {
                tvPitch.text = "Pitch: %.2f°".format(Math.toDegrees(eulerAngles[0].toDouble()))
                tvRoll.text = "Roll: %.2f°".format(Math.toDegrees(eulerAngles[1].toDouble()))
                tvYaw.text = "Yaw: %.2f°".format(Math.toDegrees(eulerAngles[2].toDouble()))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 可以忽略或记录精度变化
    }

    private fun quaternionToEuler(x: Float, y: Float, z: Float, w: Float) {
        // 俯仰角 (pitch, X轴)
        val sinP = 2.0f * (w * x + y * z)
        val cosP = 1.0f - 2.0f * (x * x + y * y)
        eulerAngles[0] = Math.atan2(sinP.toDouble(), cosP.toDouble()).toFloat()

        // 横滚角 (roll, Y轴)
        val sinR = 2.0f * (w * y - z * x)
        eulerAngles[1] = if (abs(sinR) >= 1) {
            (Math.PI / 2).toFloat() * sign(sinR)
        } else {
            Math.asin(sinR.toDouble()).toFloat()
        }

        // 偏航角 (yaw, Z轴) - 无磁力计会漂移
        val sinY = 2.0f * (w * z + x * y)
        val cosY = 1.0f - 2.0f * (y * y + z * z)
        eulerAngles[2] = Math.atan2(sinY.toDouble(), cosY.toDouble()).toFloat()
    }
}
```

### 采样率选择

| 常量 | 频率 | 推荐场景 |
|------|------|---------|
| `SENSOR_DELAY_FASTEST` | ~60Hz | IMU、陀螺仪 |
| `SENSOR_DELAY_GAME` | ~50Hz | 游戏 |
| `SENSOR_DELAY_NORMAL` | ~16Hz | 默认 |
| `SENSOR_DELAY_UI` | ~8Hz | UI 变化 |

---

## 4. BLE 连接状态

### MobileState 监听

```kotlin
class BLEActivity : BaseMirrorActivity<LayoutBleBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectBleStatus()
    }

    private fun collectBleStatus() {
        MobileState.isMobileConnected()
            .onEach { isConnected ->
                FLogger.d("Mobile connection status: $isConnected")
                mBindingPair.updateView {
                    tvBleStatus.text = if (isConnected) "手机已连接" else "手机未连接"
                    ivStatusIcon.setImageResource(
                        if (isConnected) R.drawable.ic_connected else R.drawable.ic_disconnected
                    )
                }
            }
            .launchIn(lifecycleScope)
    }
}
```

### X2/X3 BLE 差异

```kotlin
// X3 的 BLE 连接更稳定
if (DeviceUtil.isX3Device()) {
    // X3: MobileState.isMobileConnected() 更可靠
    collectBleStatus()
} else {
    // X2: 可能需要额外的重连逻辑
    collectBleStatusWithReconnect()
}
```

---

## 5. GPS 推流（需要 IPC SDK）

### IPC SDK GPS 数据

获取手机 GPS 数据需要集成 IPC SDK（`RayNeoIPCSDK-For-Android-*.aar`）。

```kotlin
class GPSActivity : BaseMirrorActivity<LayoutGpsBinding>() {

    // GPS 数据回调
    private val gpsListener = OnResponseListener { response ->
        response?.getData()?.let { data ->
            try {
                val json = JSONObject(data)
                if (json.has("mLatitude") && json.has("mLongitude")) {
                    val latitude = json.getDouble("mLatitude")
                    val longitude = json.getDouble("mLongitude")
                    val altitude = json.getDouble("mAltitude")
                    val provider = json.getString("mProvider")
                    val timestamp = json.getLong("mTime")

                    // 注意：回调在异步线程，必须切换到主线程
                    runOnUiThread {
                        mBindingPair.updateView {
                            tvLocation.text = "Lat: %.6f, Lon: %.6f".format(latitude, longitude)
                            tvAltitude.text = "Alt: %.1f m".format(altitude)
                            tvProvider.text = "Provider: $provider"
                        }
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun subscribeGPS() {
        // 参考 IPC SDK Sample 中的 GPS 订阅方法
        // ipcClient.subscribeGPS(gpsListener)
    }

    private fun unsubscribeGPS() {
        // ipcClient.unsubscribeGPS(gpsListener)
    }

    override fun onResume() {
        super.onResume()
        subscribeGPS()
    }

    override fun onPause() {
        super.onPause()
        unsubscribeGPS()
    }
}
```

**重要提醒：**
- `onResponse` 回调在异步线程
- 必须使用 `runOnUiThread` 或 `Handler(Looper.getMainLooper())` 更新 UI
- GPS 数据是 Flow 流，需要在 `onPause` 时取消订阅

---

## 输出清单

开发完成后请确认：

- [ ] Camera 权限正确申请和检查
- [ ] X3 VGA 摄像头有设备判断保护
- [ ] MIC 录音模式正确设置，退出时关闭
- [ ] 传感器在 `onResume` 注册，`onPause` 取消注册
- [ ] BLE 连接状态监听使用 `MobileState.isMobileConnected()`
- [ ] GPS 回调正确处理线程切换
- [ ] X2/X3 设备差异使用 `DeviceUtil.isX3Device()` 判断
