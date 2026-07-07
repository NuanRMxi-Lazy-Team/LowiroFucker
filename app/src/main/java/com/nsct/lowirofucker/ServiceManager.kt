package com.nsct.lowirofucker

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Xposed Service 管理器。
 *
 * LSPosed 框架会在模块 App 启动时通过 [XposedServiceHelper] 注入 [XposedService] 实例，
 * 用于模块 App 与框架通信（动态请求 scope、读写 Remote Preferences 等）。
 */
object ServiceManager : XposedServiceHelper.OnServiceListener {
    @Volatile
    private var service: XposedService? = null

    val xposedService: XposedService?
        get() = service

    fun register() {
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        this.service = service
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service == service) {
            this.service = null
        }
    }
}
