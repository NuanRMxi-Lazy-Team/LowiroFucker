package com.nsct.lowirofucker

import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Xposed Service 管理器。
 *
 * LSPosed 框架会在模块 App 启动时通过 [XposedServiceHelper] 注入 [XposedService] 实例，
 * 用于模块 App 与框架通信（动态请求 scope、读写 Remote Preferences 等）。
 *
 * binder 由框架通过 ContentProvider 异步送达，可能晚于界面首次组合，也可能在运行中死亡重建，
 * 因此必须通过 [service] 这个可观察状态来判断激活状态，禁止在组合时读取一次性快照。
 */
object ServiceManager : XposedServiceHelper.OnServiceListener {

    private const val TAG = "LowiroFucker"

    private val _service = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = _service.asStateFlow()

    @Volatile
    private var registered = false

    /**
     * 注册监听，只允许执行一次；重复调用无副作用。
     * 在 Application 启动时调用，保证 binder 无论早于还是晚于注册都能被收到。
     */
    fun register() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            registered = true
            runCatching { XposedServiceHelper.registerListener(this) }
                .onFailure {
                    registered = false
                    Log.e(TAG, "Failed to register Xposed service listener", it)
                }
        }
    }

    override fun onServiceBind(service: XposedService) {
        Log.i(TAG, "Xposed service bound: ${runCatching { service.frameworkName }.getOrNull()}")
        _service.value = service
    }

    override fun onServiceDied(service: XposedService) {
        Log.w(TAG, "Xposed service died")
        _service.compareAndSet(service, null)
    }
}
