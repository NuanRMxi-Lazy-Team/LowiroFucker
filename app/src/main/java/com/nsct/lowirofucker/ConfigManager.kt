package com.nsct.lowirofucker

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.delay

object ConfigManager {

    private const val TAG = "LowiroFucker"
    private const val PREFS_RETRY_COUNT = 5
    private const val PREFS_RETRY_DELAY_MS = 200L

    const val PREFS_GROUP = "hook_config"

    const val KEY_BUFFER_SIZE          = "buffer_size"
    const val KEY_ENABLED              = "hook_enabled"
    const val KEY_FMOD_DSP_BUFFER_LEN  = "fmod_dsp_buffer_len"
    const val KEY_FMOD_DSP_NUM_BUFFERS = "fmod_dsp_num_buffers"
    const val KEY_FMOD_ENABLED         = "fmod_enabled"

    const val DEFAULT_BUFFER_SIZE          = "192"
    const val DEFAULT_ENABLED              = false
    const val DEFAULT_FMOD_DSP_BUFFER_LEN  = "16"
    const val DEFAULT_FMOD_DSP_NUM_BUFFERS = "2"
    const val DEFAULT_FMOD_ENABLED         = true

    fun getBufferSize(prefs: SharedPreferences): String =
        prefs.getString(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE) ?: DEFAULT_BUFFER_SIZE

    fun isHookEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun getFmodDspBufferLen(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_DSP_BUFFER_LEN, DEFAULT_FMOD_DSP_BUFFER_LEN) ?: DEFAULT_FMOD_DSP_BUFFER_LEN

    fun getFmodDspNumBuffers(prefs: SharedPreferences): String =
        prefs.getString(KEY_FMOD_DSP_NUM_BUFFERS, DEFAULT_FMOD_DSP_NUM_BUFFERS) ?: DEFAULT_FMOD_DSP_NUM_BUFFERS

    fun isFmodEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FMOD_ENABLED, DEFAULT_FMOD_ENABLED)

    fun saveConfig(
        prefs: SharedPreferences,
        bufferSize: String,
        enabled: Boolean,
        fmodDspBufferLen: String,
        fmodDspNumBuffers: String,
        fmodEnabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_BUFFER_SIZE, bufferSize)
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_FMOD_DSP_BUFFER_LEN, fmodDspBufferLen)
            .putString(KEY_FMOD_DSP_NUM_BUFFERS, fmodDspNumBuffers)
            .putBoolean(KEY_FMOD_ENABLED, fmodEnabled)
            .apply()
    }

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 通过已连接的 Xposed Service 获取 Remote Preferences。
     * binder 刚建立时框架可能尚未就绪，失败会短暂重试；只有全部失败才返回 null。
     */
    suspend fun awaitRemotePrefs(service: XposedService?): SharedPreferences? {
        if (service == null) return null
        repeat(PREFS_RETRY_COUNT) { attempt ->
            try {
                return service.getRemotePreferences(PREFS_GROUP)
            } catch (t: Throwable) {
                Log.w(TAG, "getRemotePreferences failed (attempt ${attempt + 1}/$PREFS_RETRY_COUNT)", t)
                if (attempt < PREFS_RETRY_COUNT - 1) delay(PREFS_RETRY_DELAY_MS)
            }
        }
        return null
    }
}
