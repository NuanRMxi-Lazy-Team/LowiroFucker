package com.nsct.lowirofucker

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * App 进程侧的配置管理器。
 *
 * 写入走 XposedService.getRemotePreferences()，数据存在 LSPosed 框架数据库中，
 * Hook 进程侧通过 XposedInterface.getRemotePreferences() 读取——这是现代 API 唯一
 * 可靠的跨进程配置共享方式。
 *
 * XposedService 由 LSPosed 在模块 App 打开时注入，通过 [ServiceManager] 持有。
 */
object ConfigManager {

    const val PREFS_GROUP = "hook_config"

    const val KEY_HOOK_VALUE   = "hook_value"
    const val KEY_BUFFER_SIZE  = "buffer_size"
    const val KEY_ENABLED      = "hook_enabled"

    // FMOD 缓冲区配置
    const val KEY_FMOD_DSP_BUFFER_LEN    = "fmod_dsp_buffer_len"
    const val KEY_FMOD_DSP_NUM_BUFFERS   = "fmod_dsp_num_buffers"
    const val KEY_FMOD_STREAM_BUF_SIZE   = "fmod_stream_buf_size"
    const val KEY_FMOD_OUTPUT_RATE       = "fmod_output_rate"
    const val KEY_FMOD_OUTPUT_CHANNELS   = "fmod_output_channels"
    const val KEY_FMOD_ENABLED           = "fmod_enabled"

    const val DEFAULT_HOOK_VALUE  = "0"
    const val DEFAULT_BUFFER_SIZE = "192"
    const val DEFAULT_ENABLED     = true

    const val DEFAULT_FMOD_DSP_BUFFER_LEN  = "1024"
    const val DEFAULT_FMOD_DSP_NUM_BUFFERS = "4"
    const val DEFAULT_FMOD_STREAM_BUF_SIZE = "65536"
    const val DEFAULT_FMOD_OUTPUT_RATE     = "48000"
    const val DEFAULT_FMOD_OUTPUT_CHANNELS = "2"
    const val DEFAULT_FMOD_ENABLED         = false

    // ── 读取（App 侧，从 Remote Prefs 读） ─────────────────────────────────

    fun getHookValue(prefs: SharedPreferences): String =
        prefs.getString(KEY_HOOK_VALUE, DEFAULT_HOOK_VALUE) ?: DEFAULT_HOOK_VALUE

    fun getBufferSize(prefs: SharedPreferences): String =
        prefs.getString(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE) ?: DEFAULT_BUFFER_SIZE

    fun isHookEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    // ── FMOD 配置读取 ──────────────────────────────────────────────────────

    fun getFmodDspBufferLen(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_DSP_BUFFER_LEN, DEFAULT_FMOD_DSP_BUFFER_LEN) ?: DEFAULT_FMOD_DSP_BUFFER_LEN

    fun getFmodDspNumBuffers(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_DSP_NUM_BUFFERS, DEFAULT_FMOD_DSP_NUM_BUFFERS) ?: DEFAULT_FMOD_DSP_NUM_BUFFERS

    fun getFmodStreamBufSize(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_STREAM_BUF_SIZE, DEFAULT_FMOD_STREAM_BUF_SIZE) ?: DEFAULT_FMOD_STREAM_BUF_SIZE

    fun getFmodOutputRate(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_OUTPUT_RATE, DEFAULT_FMOD_OUTPUT_RATE) ?: DEFAULT_FMOD_OUTPUT_RATE

    fun getFmodOutputChannels(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_OUTPUT_CHANNELS, DEFAULT_FMOD_OUTPUT_CHANNELS) ?: DEFAULT_FMOD_OUTPUT_CHANNELS

    fun isFmodEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FMOD_ENABLED, DEFAULT_FMOD_ENABLED)

    // ── 写入（原子提交） ────────────────────────────────────────────────────

    fun saveConfig(
        prefs: SharedPreferences,
        hookValue: String,
        bufferSize: String,
        enabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_HOOK_VALUE, hookValue)
            .putString(KEY_BUFFER_SIZE, bufferSize)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()  // Remote Prefs 的 apply() 是同步的（底层走 Binder）
    }

    fun saveFmodConfig(
        prefs: SharedPreferences,
        dspBufferLen: String,
        dspNumBuffers: String,
        streamBufSize: String,
        outputRate: String,
        outputChannels: String,
        fmodEnabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_FMOD_DSP_BUFFER_LEN, dspBufferLen)
            .putString(KEY_FMOD_DSP_NUM_BUFFERS, dspNumBuffers)
            .putString(KEY_FMOD_STREAM_BUF_SIZE, streamBufSize)
            .putString(KEY_FMOD_OUTPUT_RATE, outputRate)
            .putString(KEY_FMOD_OUTPUT_CHANNELS, outputChannels)
            .putBoolean(KEY_FMOD_ENABLED, fmodEnabled)
            .apply()
    }

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ── 工具方法 ────────────────────────────────────────────────────────────

    /**
     * 从 [XposedService] 获取 Remote Preferences 实例。
     * 若服务不可用（模块未通过 LSPosed 加载），返回 null。
     */
    fun getRemotePrefs(service: XposedService?): SharedPreferences? =
        runCatching { service?.getRemotePreferences(PREFS_GROUP) }.getOrNull()
}
