package com.ffalcon.mercury.android.sdk.demo.ui.activity.api

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityImuBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.launch


/**
 * IMU（惯性测量单元）传感器演示活动
 * 
 * 展示 RayNeo 眼镜的三种主要传感器数据：
 * - 加速度计（Accelerometer）：测量线性加速度，单位 m/s²
 * - 陀螺仪（Gyroscope）：测量角速度，单位 rad/s
 * - 磁力计（Magnetometer）：测量磁场强度，单位 μT（微特斯拉）
 * 
 * 实时显示传感器的 X、Y、Z 三轴数据，用于姿态检测和运动追踪
 */
class IMUActivity : BaseMirrorActivity<ActivityImuBinding>(), SensorEventListener {
    // Sensor manager and sensor objects
    /** 传感器管理器，负责管理所有传感器操作 */
    private lateinit var sensorManager: SensorManager
    
    /** 加速度计传感器实例 */
    private var accelerometerSensor: Sensor? = null
    
    /** 陀螺仪传感器实例 */
    private var gyroscopeSensor: Sensor? = null
    
    /** 磁力计传感器实例 */
    private var magnetometerSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 IMU 传感器
        initIMU()
        // 初始化事件监听（temple 触摸板双击退出）
        initEvent()
    }

    /**
     * 初始化 IMU 传感器
     * 
     * 获取系统传感器服务并初始化三种传感器：
     * 1. 获取 SensorManager 系统服务
     * 2. 获取默认传感器实例（加速度计、陀螺仪、磁力计）
     * 3. 检查传感器可用性，如果不可用则显示提示信息
     */
    private fun initIMU() {
        // 1. Get SensorManager system service
        // 获取传感器管理器系统服务
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // 2. Get default sensor instances
        // 使用 TYPE_ACCELEROMETER 获取加速度计
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // 使用 TYPE_GYROSCOPE 获取陀螺仪
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // 使用 TYPE_MAGNETIC_FIELD 获取磁力计
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mBindingPair.updateView {
            // Check if device supports these sensors
            // 检查设备是否支持这些传感器，如果不支持则显示提示
            if (accelerometerSensor == null) {
                tvAccelerometer.text = "Accelerometer unavailable"
            }
            if (gyroscopeSensor == null) {
                tvGyroscope.text = "Gyroscope unavailable"
            }
            if (magnetometerSensor == null) {
                tvMagnetometer.text = "Magnetometer unavailable"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 3. Register sensor listeners
        // 参数：监听器、传感器对象、采样延迟（微秒）
        // 在 Activity 恢复时注册传感器监听器，开始接收传感器数据
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }


    override fun onPause() {
        super.onPause()
        // 4. Very important! Unregister listeners when paused to save battery
        // 非常重要！在暂停时取消注册监听器以节省电量
        // 避免在后台继续接收传感器数据，减少电池消耗
        sensorManager.unregisterListener(this)
    }

    /**
     * 初始化事件监听
     * 
     * 监听 temple 触摸板的事件流，在 Activity 处于 RESUMED 状态时持续收集事件：
     * - 双击：退出当前页面（finish）
     * - 其他事件：不处理（Unit）
     */
    private fun initEvent() {
        lifecycleScope.launch {
            // 只在 Activity 恢复状态下收集事件，避免后台运行时响应触摸
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // 收集 temple 触摸板的状态流
                templeActionViewModel.state.collect {
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            finish()  // 双击返回
                        }

                        else -> Unit  // 其他事件不处理
                    }
                }
            }
        }
    }

    // Callback when sensor data changes
    /**
     * 传感器数据变化回调
     * 
     * 当传感器数据更新时调用，根据传感器类型更新对应的 UI 显示：
     * - 加速度计：显示 X、Y、Z 轴的加速度值（m/s²）
     * - 陀螺仪：显示 X、Y、Z 轴的角速度值（rad/s）
     * - 磁力计：显示 X、Y、Z 轴的磁场强度值（μT）
     * 
     * @param event 传感器事件，包含传感器类型和数据值
     */
    override fun onSensorChanged(event: SensorEvent) {
        mBindingPair.updateView {
            // event.values is a float array containing X, Y, Z axis data
            // event.values 是一个浮点数组，包含 X、Y、Z 轴的数据
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // 格式化显示加速度计数据，保留两位小数
                    tvAccelerometer.text =
                        "Accelerometer:\nX: %.2f m/s²\nY: %.2f m/s²\nZ: %.2f m/s²".format(x, y, z)
                }

                Sensor.TYPE_GYROSCOPE -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // 格式化显示陀螺仪数据，保留两位小数
                    tvGyroscope.text =
                        "Gyroscope:\nX: %.2f rad/s\nY: %.2f rad/s\nZ: %.2f rad/s".format(x, y, z)
                }

                Sensor.TYPE_MAGNETIC_FIELD -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // 格式化显示磁力计数据，保留两位小数
                    tvMagnetometer.text =
                        "Magnetometer:\nX: %.2f μT\nY: %.2f μT\nZ: %.2f μT".format(x, y, z)
                }
            }
        }
    }

    // Callback when sensor accuracy changes (usually no need to handle)
    /**
     * 传感器精度变化回调
     * 
     * 当传感器精度发生变化时调用（通常不需要处理）
     * 可以在此处处理精度变化，例如从低精度变为高精度
     * 
     * @param sensor 发生精度变化的传感器
     * @param accuracy 新的精度等级
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can handle accuracy changes here, e.g., from low accuracy to high accuracy
        // 可以在此处处理精度变化，例如从低精度变为高精度
    }

}