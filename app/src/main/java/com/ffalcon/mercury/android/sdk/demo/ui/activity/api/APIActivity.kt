package com.ffalcon.mercury.android.sdk.demo.ui.activity.api

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffalcon.mercury.android.sdk.api.MobileState
import com.ffalcon.mercury.android.sdk.demo.databinding.ActivityApiBinding
import com.ffalcon.mercury.android.sdk.touch.TempleAction
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.ffalcon.mercury.android.sdk.util.DeviceUtil
import com.ffalcon.mercury.android.sdk.util.FLogger
import com.ffalconxr.mercury.ipc.Launcher
import com.ffalconxr.mercury.ipc.Launcher.OnResponseListener
import com.ffalconxr.mercury.ipc.helpers.GPSIPCHelper
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * API 功能演示活动
 * 
 * 展示 RayNeo 眼镜的多种 API 功能：
 * - GPS 定位信息获取
 * - BLE 蓝牙连接状态（仅 X3 设备）
 * - 设备类型检测（X2/X3）
 * 
 * 通过 IPC（进程间通信）与眼镜固件交互获取传感器数据
 */
class APIActivity : BaseMirrorActivity<ActivityApiBinding>() {
    /** IPC 启动器实例，用于与眼镜固件通信 */
    private var mLauncher: Launcher? = null
    
    /**
     * GPS 数据响应监听器
     * 
     * 接收来自眼镜固件的 GPS 定位数据，解析 JSON 格式的位置信息：
     * - 纬度、经度、海拔
     * - 速度、方向、精度等
     * 
     * 在 UI 线程上更新显示经纬度坐标
     */
    private val response =
        OnResponseListener { response ->
            // 如果响应数据为空，直接返回
            if (response?.getData() == null) return@OnResponseListener
            try {
                // 解析 JSON 格式的 GPS 数据
                val jo = JSONObject(response.getData())
                // 检查是否包含基本的 GPS 坐标信息
                if (jo.has("mLatitude") && jo.has("mLongitude") && jo.has("mAltitude")) { //GPS data
                    // 提取位置提供者和时间戳
                    val mProvider = jo.getString("mProvider")
                    val mTime = jo.getLong("mTime")
                    val mElapsedRealtimeNanos = jo.getLong("mElapsedRealtimeNanos")
                    // 提取经纬度坐标
                    val mLatitude = jo.getDouble("mLatitude")
                    val mLongitude = jo.getDouble("mLongitude")
                    // 在主线程更新 UI，显示经纬度
                    runOnUiThread {
                        mBindingPair.updateView {
                            tvTvLocationInfo.text = "$mLatitude,$mLongitude"
                        }
                    }
                    // 提取其他 GPS 详细信息
                    val mAltitude = jo.getDouble("mAltitude")
                    val mSpeed = jo.getDouble("mSpeed")
                    val mBearing = jo.getDouble("mBearing")
                    val mHorizontalAccuracyMeters = jo.getDouble("mHorizontalAccuracyMeters")
                    val mVerticalAccuracyMeters = jo.getDouble("mVerticalAccuracyMeters")
                    val mSpeedAccuracyMetersPerSecond =
                        jo.getDouble("mSpeedAccuracyMetersPerSecond")
                    val mBearingAccuracyDegrees = jo.getDouble("mBearingAccuracyDegrees")
                    // 记录完整的 GPS 数据到日志
                    FLogger.i(

                        ("======  mProvider:" + mProvider + "  mTime:" + mTime + "  mElapsedRealtimeNanos:" + mElapsedRealtimeNanos
                                + "  mLatitude:" + mLatitude + "  mLongitude:" + mLongitude + "  mAltitude:" + mAltitude + "  mSpeed:" + mSpeed
                                + "  mBearing:" + mBearing + "  mHorizontalAccuracyMeters:" + mHorizontalAccuracyMeters + "  mVerticalAccuracyMeters:" + mVerticalAccuracyMeters
                                + "  mSpeedAccuracyMetersPerSecond:" + mSpeedAccuracyMetersPerSecond + "  mBearingAccuracyDegrees:" + mBearingAccuracyDegrees + "   ======")
                    )
                }
            } catch (e: JSONException) {
                // JSON 解析失败，打印异常堆栈
                e.printStackTrace()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化事件监听（temple 触摸板双击退出）
        initEvent()
        // 检测设备类型（X2 或 X3）
        getDevicesType()
        // 如果是 X3 设备，收集 BLE 蓝牙连接状态
        if (DeviceUtil.isX3Device()) {
            collectBleStatus()
        }
        // 初始化 GPS 功能
        initGPS()
    }

    /**
     * 初始化 GPS 功能
     * 
     * 设置 IPC 通信和 GPS 数据监听：
     * - 获取 Launcher 实例用于进程间通信
     * - 启用日志记录便于调试
     * - 注册 GPS 数据响应监听器
     * - 向眼镜固件注册 GPS 信息请求
     */
    private fun initGPS() {
        // 获取 IPC 启动器单例
        mLauncher = Launcher.getInstance(this)
        mLauncher!!.enableLog()//Enable log
        // 添加 GPS 数据响应监听器
        mLauncher!!.addOnResponseListener(response)
        // 注册 GPS 信息，开始接收来自眼镜的 GPS 数据
        GPSIPCHelper.registerGPSInfo(this)
    }

    /**
     * 检测设备类型并更新 UI
     * 
     * 根据设备型号（X2 或 X3）显示不同的信息：
     * - X3 设备：显示设备名称，并显示 BLE 蓝牙状态相关视图
     * - X2 设备：显示设备名称，隐藏 BLE 相关视图（X2 不支持此功能）
     */
    private fun getDevicesType() {
        mBindingPair.updateView {
            if (DeviceUtil.isX3Device()) {
                tvDevicesType.text = "Rayneo X3"
                // X3 支持 BLE，显示相关视图
                tvBle.visibility = View.VISIBLE
                tvBleStatus.visibility = View.VISIBLE
            } else {
                tvDevicesType.text = "Rayneo X2"
                // X2 不支持 BLE，隐藏相关视图
                tvBle.visibility = View.GONE
                tvBleStatus.visibility = View.GONE
            }

        }
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

    /**
     * 收集 BLE 蓝牙连接状态
     * 
     * 使用 Flow 持续监听手机与眼镜的蓝牙连接状态：
     * - true：已连接（connect）
     * - false：未连接（disconnect）
     * 
     * 仅在 X3 设备上调用，因为 X2 不支持此功能
     */
    private fun collectBleStatus() {
        // 监听移动设备连接状态的变化
        MobileState.isMobileConnected().onEach {
            FLogger.d("isMobileConnected:$it")
            mBindingPair.updateView {
                // 根据连接状态更新显示文本
                tvBleStatus.text = if (it) "connect" else "disconnect"
            }
        }.launchIn(lifecycleScope)  // 在生命周期作用域中启动 Flow 收集
    }

    /**
     * 销毁活动时的资源清理
     * 
     * 取消注册 GPS 信息监听器，停止接收 GPS 数据更新，避免内存泄漏
     */
    override fun onDestroy() {
        // 取消注册 GPS 信息，停止接收来自眼镜的 GPS 数据
        GPSIPCHelper.unRegisterGPSInfo(this)
        super.onDestroy()
    }
}