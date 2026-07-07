package com.nsct.lowirofucker

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

object ConfigManager {

    const val PREFS_GROUP = "hook_config"

    const val KEY_BUFFER_SIZE          = "buffer_size"
    const val KEY_ENABLED              = "hook_enabled"
    const val KEY_FMOD_DSP_BUFFER_LEN  = "fmod_dsp_buffer_len"
    const val KEY_FMOD_DSP_NUM_BUFFERS = "fmod_dsp_num_buffers"
    const val KEY_FMOD_ENABLED         = "fmod_enabled"

    const val DEFAULT_BUFFER_SIZE          = "192"
    const val DEFAULT_ENABLED              = true
    const val DEFAULT_FMOD_DSP_BUFFER_LEN  = "16"
    const val DEFAULT_FMOD_DSP_NUM_BUFFERS = "2"
    const val DEFAULT_FMOD_ENABLED         = false

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

    fun getRemotePrefs(service: XposedService?): SharedPreferences? =
        runCatching { service?.getRemotePreferences(PREFS_GROUP) }.getOrNull()
}
