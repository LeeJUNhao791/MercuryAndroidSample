package com.ffalcon.mercury.android.sdk.demo

import android.app.Application
import com.ffalcon.mercury.android.sdk.MercurySDK

/**
 * Mercury SDK 演示应用程序类
 * 负责初始化整个应用和Mercury SDK
 */
class MercuryDemoApplication : Application() {
    companion object {
        // 全局应用上下文，方便在其他地方访问
        lateinit var appContext: Application
    }

    override fun onCreate() {
        super.onCreate()
        // 保存应用上下文引用
        appContext = this
        // 初始化Mercury SDK，这是使用SDK功能的前提
        MercurySDK.init(this)
    }
}