package com.nsct.lowirofucker

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class HookModule : XposedModule() {

    companion object {
        private const val TAG = "LowiroFucker"
        private const val TARGET_PACKAGE = "moe.low.arc"
    }

    @Volatile private var bufferSize: Int = ConfigManager.DEFAULT_BUFFER_SIZE.toInt()
    @Volatile private var isEnabled: Boolean = ConfigManager.DEFAULT_ENABLED

    @Volatile private var fmodDspBufferLen: Int = ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN.toInt()
    @Volatile private var fmodDspNumBuffers: Int = ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS.toInt()
    @Volatile private var fmodEnabled: Boolean = ConfigManager.DEFAULT_FMOD_ENABLED

    private val remotePrefs: SharedPreferences by lazy {
        getRemotePreferences(ConfigManager.PREFS_GROUP)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded in process: ${param.processName}")
        loadConfig()
        runCatching {
            NativeHook.nativeUpdateConfig(fmodDspBufferLen, fmodDspNumBuffers)
            NativeHook.nativeInit()
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        loadConfig()

        if (!isEnabled && !fmodEnabled) {
            log(Log.INFO, TAG, "All hooks disabled, skipping $TARGET_PACKAGE")
            return
        }

        val cl = param.classLoader

        if (isEnabled) {
            installAudioManagerHooks(cl)
        }

        if (fmodEnabled) {
            log(Log.INFO, TAG, "FMOD native hook: dspBufLen=$fmodDspBufferLen numBufs=$fmodDspNumBuffers")
        }
    }

    private fun loadConfig() {
        runCatching {
            val prefs = remotePrefs
            bufferSize = ConfigManager.getBufferSize(prefs).toIntOrNull()
                         ?: ConfigManager.DEFAULT_BUFFER_SIZE.toInt()
            isEnabled  = ConfigManager.isHookEnabled(prefs)

            fmodDspBufferLen  = ConfigManager.getFmodDspBufferLen(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN.toInt()
            fmodDspNumBuffers = ConfigManager.getFmodDspNumBuffers(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS.toInt()
            fmodEnabled       = ConfigManager.isFmodEnabled(prefs)

            log(Log.INFO, TAG, "Config: enabled=$isEnabled buffer=$bufferSize fmod=$fmodEnabled dspBufLen=$fmodDspBufferLen numBufs=$fmodDspNumBuffers")
        }.onFailure {
            log(Log.WARN, TAG, "Failed to load config: ${it.message}")
        }
    }

    private inline fun safeHook(name: String, block: () -> Unit) {
        runCatching(block).onFailure { log(Log.WARN, TAG, "Hook failed [$name]: ${it.message}") }
    }

    private fun installAudioManagerHooks(cl: ClassLoader) {
        val cls = runCatching {
            Class.forName("android.media.AudioManager", true, cl)
        }.getOrElse { return }

        safeHook("AudioManager.getProperty") {
            val m = cls.getMethod("getProperty", String::class.java)
            hook(m).intercept { chain ->
                if (!isEnabled) return@intercept chain.proceed()
                val key = chain.getArg(0) as? String
                when (key) {
                    "android.media.property.OUTPUT_FRAMES_PER_BUFFER" -> {
                        val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                        log(Log.INFO, TAG, "[HOOK] AudioManager.getProperty(OUTPUT_FRAMES_PER_BUFFER) original=$original -> hooked=$bufferSize")
                        bufferSize.toString()
                    }
                    else -> chain.proceed()
                }
            }
        }
    }
}
