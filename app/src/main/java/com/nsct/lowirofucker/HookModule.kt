package com.nsct.lowirofucker

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 模块主入口，使用现代 libxposed API (102)。
 *
 * 配置读取：
 *   [XposedInterface.getRemotePreferences] 由框架（LSPosed）负责跨进程同步，
 *   在目标 App 进程中返回的是只读视图，但内容由模块 App 写入的值填充。
 *   这是现代 API 下唯一可靠的配置共享方式——不依赖文件路径、不依赖 UID 权限。
 */
class HookModule : XposedModule() {

    companion object {
        private const val TAG = "LowiroFucker"
        private const val TARGET_PACKAGE = "moe.low.arc"
        private const val PROP_KEY = "aaudio.hw_burst_min_usec"
    }

    // 配置值，从 Remote Prefs 加载后填充
    @Volatile private var hookValue: String = ConfigManager.DEFAULT_HOOK_VALUE
    @Volatile private var bufferSize: Int = ConfigManager.DEFAULT_BUFFER_SIZE.toInt()
    @Volatile private var isEnabled: Boolean = ConfigManager.DEFAULT_ENABLED

    // FMOD 配置
    @Volatile private var fmodDspBufferLen: Int = ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN.toInt()
    @Volatile private var fmodDspNumBuffers: Int = ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS.toInt()
    @Volatile private var fmodStreamBufSize: Int = ConfigManager.DEFAULT_FMOD_STREAM_BUF_SIZE.toInt()
    @Volatile private var fmodOutputRate: Int = ConfigManager.DEFAULT_FMOD_OUTPUT_RATE.toInt()
    @Volatile private var fmodOutputChannels: Int = ConfigManager.DEFAULT_FMOD_OUTPUT_CHANNELS.toInt()
    @Volatile private var fmodEnabled: Boolean = ConfigManager.DEFAULT_FMOD_ENABLED

    // 已扫描过的 ClassLoader，避免重复扫描
    private val scannedClassLoaders = mutableSetOf<Int>()

    /** 惰性加载 Remote Prefs，由框架在目标进程上下文中提供 */
    private val remotePrefs: SharedPreferences by lazy {
        getRemotePreferences(ConfigManager.PREFS_GROUP)
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded in process: ${param.processName}")
        log(Log.INFO, TAG, "Framework: $frameworkName ($frameworkVersionCode) API $apiVersion")
        loadConfig()
        // Install dlopen hook ASAP to catch libfmod.so loading
        runCatching { NativeHook.nativeInit() }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return

        // 每次包就绪时刷新配置（Remote Prefs 自动维护一致性）
        loadConfig()

        if (!isEnabled && !fmodEnabled) {
            log(Log.INFO, TAG, "All hooks disabled, skipping $TARGET_PACKAGE")
            return
        }

        log(Log.INFO, TAG, "Hooking $TARGET_PACKAGE | value=$hookValue buffer=$bufferSize fmod=$fmodEnabled")

        val cl = param.classLoader

        if (isEnabled) {
            installSystemPropertyHooks(cl)
            installAudioTrackHooks(cl)
            installAudioRecordHooks(cl)
            installAudioManagerHooks(cl)
            installReflectionHook()
        }

        if (fmodEnabled) {
            log(Log.INFO, TAG, "FMOD hook enabled: dspBufLen=$fmodDspBufferLen numBufs=$fmodDspNumBuffers streamBuf=$fmodStreamBufSize rate=$fmodOutputRate ch=$fmodOutputChannels")
            scanAndHookFmodClasses(cl)
        }
    }

    // ── 配置加载 ──────────────────────────────────────────────────────────

    private fun loadConfig() {
        runCatching {
            val prefs = remotePrefs
            hookValue  = ConfigManager.getHookValue(prefs)
            bufferSize = ConfigManager.getBufferSize(prefs).toIntOrNull()
                         ?: ConfigManager.DEFAULT_BUFFER_SIZE.toInt()
            isEnabled  = ConfigManager.isHookEnabled(prefs)

            fmodDspBufferLen  = ConfigManager.getFmodDspBufferLen(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN.toInt()
            fmodDspNumBuffers = ConfigManager.getFmodDspNumBuffers(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS.toInt()
            fmodStreamBufSize = ConfigManager.getFmodStreamBufSize(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_STREAM_BUF_SIZE.toInt()
            fmodOutputRate    = ConfigManager.getFmodOutputRate(prefs).toIntOrNull()
                                ?: ConfigManager.DEFAULT_FMOD_OUTPUT_RATE.toInt()
            fmodOutputChannels = ConfigManager.getFmodOutputChannels(prefs).toIntOrNull()
                                 ?: ConfigManager.DEFAULT_FMOD_OUTPUT_CHANNELS.toInt()
            fmodEnabled       = ConfigManager.isFmodEnabled(prefs)

            log(Log.INFO, TAG, "Config loaded: enabled=$isEnabled value=$hookValue buffer=$bufferSize")
            log(Log.INFO, TAG, "FMOD config: enabled=$fmodEnabled dspBufLen=$fmodDspBufferLen numBufs=$fmodDspNumBuffers streamBuf=$fmodStreamBufSize rate=$fmodOutputRate ch=$fmodOutputChannels")
        }.onFailure {
            log(Log.WARN, TAG, "Failed to load config, using defaults: ${it.message}")
        }
    }

    // ── Hooker 辅助：捕获内部异常，避免 Hook 崩溃影响目标 App ─────────────

    private inline fun safeHook(
        methodName: String,
        block: () -> Unit
    ) {
        runCatching(block).onFailure {
            log(Log.WARN, TAG, "Hook failed [$methodName]: ${it.message}")
        }
    }

    // ── SystemProperties Hooks ────────────────────────────────────────────

    private fun installSystemPropertyHooks(cl: ClassLoader) {
        val sysPropClass = runCatching {
            Class.forName("android.os.SystemProperties", true, cl)
        }.getOrElse {
            log(Log.WARN, TAG, "SystemProperties class not found")
            return
        }

        // get(String key)
        safeHook("SystemProperties.get(String)") {
            val method = sysPropClass.getMethod("get", String::class.java)
            hook(method).intercept { chain ->
                val key = chain.getArg(0) as? String
                if (key == PROP_KEY && isEnabled) {
                    val original = runCatching { chain.proceed() as? String }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] SystemProperties.get($PROP_KEY) original=$original -> hooked=$hookValue")
                    hookValue
                } else chain.proceed()
            }
        }

        // get(String key, String def)
        safeHook("SystemProperties.get(String,String)") {
            val method = sysPropClass.getMethod("get", String::class.java, String::class.java)
            hook(method).intercept { chain ->
                val key = chain.getArg(0) as? String
                if (key == PROP_KEY && isEnabled) {
                    val original = runCatching { chain.proceed() as? String }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] SystemProperties.get($PROP_KEY, def) original=$original -> hooked=$hookValue")
                    hookValue
                } else chain.proceed()
            }
        }

        // getInt(String key, int def)
        safeHook("SystemProperties.getInt") {
            val method = sysPropClass.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            hook(method).intercept { chain ->
                val key = chain.getArg(0) as? String
                if (key == PROP_KEY && isEnabled) {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    val hooked = hookValue.toIntOrNull() ?: 0
                    log(Log.INFO, TAG, "[HOOK] SystemProperties.getInt($PROP_KEY) original=$original -> hooked=$hooked")
                    hooked
                } else chain.proceed()
            }
        }

        // getLong(String key, long def)
        safeHook("SystemProperties.getLong") {
            val method = sysPropClass.getMethod("getLong", String::class.java, Long::class.javaPrimitiveType)
            hook(method).intercept { chain ->
                val key = chain.getArg(0) as? String
                if (key == PROP_KEY && isEnabled) {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    val hooked = hookValue.toLongOrNull() ?: 0L
                    log(Log.INFO, TAG, "[HOOK] SystemProperties.getLong($PROP_KEY) original=$original -> hooked=$hooked")
                    hooked
                } else chain.proceed()
            }
        }

        // 遍历所有 native_get* 方法
        for (m in sysPropClass.declaredMethods) {
            if (!m.name.startsWith("native_get")) continue
            safeHook("SystemProperties.${m.name}") {
                hook(m).intercept { chain ->
                    if (!isEnabled) return@intercept chain.proceed()
                    val key = runCatching { chain.getArg(0) as? String }.getOrNull()
                    if (key != PROP_KEY) return@intercept chain.proceed()
                    val original = runCatching { chain.proceed() }.getOrNull()
                    val hooked = when (m.returnType) {
                        String::class.java           -> hookValue
                        Int::class.javaPrimitiveType -> hookValue.toIntOrNull() ?: 0
                        Long::class.javaPrimitiveType -> hookValue.toLongOrNull() ?: 0L
                        Boolean::class.javaPrimitiveType -> (hookValue.toIntOrNull() ?: 0) != 0
                        else -> return@intercept chain.proceed()
                    }
                    log(Log.INFO, TAG, "[HOOK] SystemProperties.${m.name}($PROP_KEY) original=$original -> hooked=$hooked")
                    hooked
                }
            }
        }
    }

    // ── AudioTrack Hooks ──────────────────────────────────────────────────

    private fun installAudioTrackHooks(cl: ClassLoader) {
        val cls = runCatching {
            Class.forName("android.media.AudioTrack", true, cl)
        }.getOrElse {
            log(Log.WARN, TAG, "AudioTrack class not found")
            return
        }

        safeHook("AudioTrack.getMinBufferSize") {
            val m = cls.getMethod("getMinBufferSize",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            hook(m).intercept { chain ->
                if (!isEnabled) chain.proceed() else {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] AudioTrack.getMinBufferSize original=$original -> hooked=$bufferSize")
                    bufferSize
                }
            }
        }

        safeHook("AudioTrack.getBufferSizeInFrames") {
            val m = cls.getMethod("getBufferSizeInFrames")
            hook(m).intercept { chain ->
                if (!isEnabled) chain.proceed() else {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] AudioTrack.getBufferSizeInFrames original=$original -> hooked=$bufferSize")
                    bufferSize
                }
            }
        }

        safeHook("AudioTrack.getBufferCapacityInFrames") {
            val m = cls.getMethod("getBufferCapacityInFrames")
            hook(m).intercept { chain ->
                if (!isEnabled) chain.proceed() else {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] AudioTrack.getBufferCapacityInFrames original=$original -> hooked=$bufferSize")
                    bufferSize
                }
            }
        }

        // 遍历 native buffer 相关方法
        for (m in cls.declaredMethods) {
            if (!m.name.contains("buffer", ignoreCase = true) &&
                !m.name.contains("frame", ignoreCase = true)) continue
            if (m.returnType != Int::class.javaPrimitiveType) continue
            if (m.parameterCount != 0) continue
            safeHook("AudioTrack.${m.name}") {
                hook(m).intercept { chain ->
                    if (!isEnabled) chain.proceed()
                    else {
                        val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                        log(Log.INFO, TAG, "[HOOK] AudioTrack.${m.name} original=$original -> hooked=$bufferSize")
                        bufferSize
                    }
                }
            }
        }
    }

    // ── AudioRecord Hooks ─────────────────────────────────────────────────

    private fun installAudioRecordHooks(cl: ClassLoader) {
        val cls = runCatching {
            Class.forName("android.media.AudioRecord", true, cl)
        }.getOrElse {
            log(Log.WARN, TAG, "AudioRecord class not found")
            return
        }

        safeHook("AudioRecord.getMinBufferSize") {
            val m = cls.getMethod("getMinBufferSize",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            hook(m).intercept { chain ->
                if (!isEnabled) chain.proceed() else {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] AudioRecord.getMinBufferSize original=$original -> hooked=$bufferSize")
                    bufferSize
                }
            }
        }

        safeHook("AudioRecord.getBufferSizeInFrames") {
            val m = cls.getMethod("getBufferSizeInFrames")
            hook(m).intercept { chain ->
                if (!isEnabled) chain.proceed() else {
                    val original = runCatching { chain.proceed() }.getOrNull() ?: "null"
                    log(Log.INFO, TAG, "[HOOK] AudioRecord.getBufferSizeInFrames original=$original -> hooked=$bufferSize")
                    bufferSize
                }
            }
        }
    }

    // ── AudioManager Hooks ────────────────────────────────────────────────

    private fun installAudioManagerHooks(cl: ClassLoader) {
        val cls = runCatching {
            Class.forName("android.media.AudioManager", true, cl)
        }.getOrElse {
            log(Log.WARN, TAG, "AudioManager class not found")
            return
        }

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

    // ── Reflection Hook（拦截反射调用 SystemProperties） ────────────────────

    private fun installReflectionHook() {
        safeHook("Method.invoke") {
            val invokeMethod = Method::class.java.getMethod(
                "invoke", Any::class.java, Array<Any>::class.java
            )
            hook(invokeMethod).intercept { chain ->
                if (!isEnabled) return@intercept chain.proceed()
                val method = chain.thisObject as? Method ?: return@intercept chain.proceed()
                if (method.declaringClass.name != "android.os.SystemProperties") {
                    return@intercept chain.proceed()
                }
                @Suppress("UNCHECKED_CAST")
                val args = runCatching { chain.getArg(1) as? Array<Any?> }.getOrNull()
                if (args.isNullOrEmpty() || args[0] != PROP_KEY) {
                    return@intercept chain.proceed()
                }
                val original = runCatching { chain.proceed() }.getOrNull()
                val hooked = when (method.name) {
                    "get"     -> hookValue
                    "getInt"  -> { hookValue.toIntOrNull() ?: 0 }
                    "getLong" -> { hookValue.toLongOrNull() ?: 0L }
                    else      -> return@intercept chain.proceed()
                }
                log(Log.INFO, TAG, "[HOOK] Reflection SystemProperties.${method.name}($PROP_KEY) original=$original -> hooked=$hooked")
                hooked
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FMOD Hooks
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 扫描 ClassLoader 中所有包含 "fmod" 的类，并 hook 其所有方法。
     */
    private fun scanAndHookFmodClasses(cl: ClassLoader?) {
        if (cl == null) return
        val clId = System.identityHashCode(cl)
        synchronized(scannedClassLoaders) {
            if (scannedClassLoaders.contains(clId)) return
            scannedClassLoaders.add(clId)
        }

        log(Log.INFO, TAG, "[FMOD] Scanning for FMOD classes...")

        // 额外尝试常见的 FMOD JNI 类名
        val commonFmodClasses = listOf(
            "org.fmod.FMOD",
            "org.fmod.System",
            "org.fmod.Channel",
            "org.fmod.ChannelGroup",
            "org.fmod.Sound",
            "org.fmod.DSP",
            "org.fmod.Geometry",
            "org.fmod.Reverb3D",
            "org.fmod.StudioSystem",
            "org.fmod.StudioEventDescription",
            "org.fmod.StudioEventInstance",
            "org.fmod.StudioBus",
            "org.fmod.StudioVCA",
            "org.fmod.StudioBank",
            "org.fmod.StudioCommandReplay",
            "com.fmod.FMOD",
            "com.fmod.javafmod",
            "fmod.FMOD",
            "fmod.fmodprovider",
            "moe.low.arc.FmodBridge",
            "moe.low.arc.AudioEngine",
            "moe.low.arc.SoundManager",
            "moe.low.arc.FmodProvider",
        )

        var hookedCount = 0
        for (name in commonFmodClasses) {
            val fmodClass = runCatching { Class.forName(name, false, cl) }.getOrNull() ?: continue
            log(Log.INFO, TAG, "[FMOD] Found class: $name")
            hookedCount += hookFmodClass(fmodClass, hookAll = true)
        }

        log(Log.INFO, TAG, "[FMOD] Scan complete, hooked $hookedCount methods from ${commonFmodClasses.size} candidates")
    }

    /**
     * Hook 一个 FMOD 类中的方法。
     * @param hookAll true=hook 所有方法；false=只 hook 缓冲区相关方法
     */
    private fun hookFmodClass(cls: Class<*>, hookAll: Boolean = false): Int {
        val className = cls.name
        var count = 0

        val bufferKeywords = listOf(
            "buffer", "buf", "dsp", "stream", "output",
            "format", "rate", "channel", "sample", "block",
            "frames", "latency", "mixer", "create", "init"
        )

        val methods = try {
            cls.declaredMethods
        } catch (e: Throwable) {
            return 0
        }

        for (method in methods) {
            val nameLower = method.name.lowercase()

            // 决定是否 hook 此方法
            if (!hookAll) {
                val isBufferMethod = bufferKeywords.any { nameLower.contains(it) }
                if (!isBufferMethod) continue
            }

            val paramTypes = method.parameterTypes.joinToString(", ") { it.simpleName }
            val hasParams = method.parameterCount > 0
            val isNative = Modifier.isNative(method.modifiers)

            safeHook("FMOD:${if (isNative) "native:" else ""}$className.${method.name}($paramTypes)") {
                hook(method).intercept { chain ->
                    if (!fmodEnabled) return@intercept chain.proceed()

                    // ── init 方法：在调用原始 init 前安装 native hook ──
                    if (nameLower == "init" && method.parameterCount == 1) {
                        log(Log.INFO, TAG, "[FMOD] Before init: installing native hook on setDSPBufferSize")
                        val ret = runCatching {
                            NativeHook.nativeInstallHook("libfmod.so", fmodDspBufferLen, fmodDspNumBuffers)
                        }.getOrElse { -99 }
                        log(Log.INFO, TAG, "[FMOD-NATIVE] Install result: $ret")

                        val result = chain.proceed()
                        val callCount = NativeHook.nativeGetHookCallCount()
                        log(Log.INFO, TAG, "[FMOD] init(Context) completed, result=$result, setDSPBufferSize called $callCount times")
                        return@intercept result
                    }

                    if (hasParams) {
                        // ── 有参数：篡改参数后再调用原始方法 ──
                        val originalArgs = (0 until method.parameterCount).map { i ->
                            runCatching { chain.getArg(i) }.getOrNull()
                        }

                        val modifiedArgs = buildModifiedFmodArgs(className, method, chain)

                        val result = chain.proceed(modifiedArgs)

                        log(Log.INFO, TAG,
                            "[FMOD] $className.${method.name}($paramTypes) " +
                            "args: $originalArgs -> ${modifiedArgs.toList()} | result=$result"
                        )
                        result
                    } else {
                        // ── 无参数：篡改返回值 ──
                        val originalResult = runCatching { chain.proceed() }.getOrNull()
                        val hookedResult = interceptFmodResult(
                            className, method.name, method.returnType, originalResult
                        )

                        if (hookedResult !== originalResult) {
                            log(Log.INFO, TAG,
                                "[FMOD] $className.${method.name}($paramTypes) " +
                                "original=$originalResult -> hooked=$hookedResult"
                            )
                        }
                        hookedResult
                    }
                }
                count++
            }
        }

        if (count > 0) {
            log(Log.INFO, TAG, "[FMOD] Class $className: hooked $count methods")
        }
        return count
    }

    /**
     * FMOD init 后尝试通过反射调用 native 方法重新配置缓冲区。
     * 遍历所有 native 方法，尝试以配置值调用。
     */
    private fun tryReconfigureFmodNative(className: String, cls: Class<*>, chain: XposedInterface.Chain) {
        log(Log.INFO, TAG, "[FMOD] init completed, scanning for native reconfiguration entry points...")

        val thisObj = chain.getThisObject()

        // 遍历所有 native 方法，尝试调用
        val nativeMethods = try { cls.declaredMethods } catch (e: Throwable) { emptyArray() }
            .filter { Modifier.isNative(it.modifiers) }

        log(Log.INFO, TAG, "[FMOD] Found ${nativeMethods.size} native methods:")
        for (m in nativeMethods) {
            val params = m.parameterTypes.joinToString(", ") { it.simpleName }
            log(Log.INFO, TAG, "[FMOD]   native: ${m.name}($params) -> ${m.returnType.simpleName}")
        }

        // 尝试所有可能的 native 配置方法签名
        val attempts = listOf(
            // (方法名, 参数类型数组, 参数值数组, 是否静态)
            Triple("nativeSetDSPBufferSize", arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodDspBufferLen, fmodDspNumBuffers)),
            Triple("nativeSetStreamBufferSize", arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodStreamBufSize.toLong(), 2)),
            Triple("nativeSetOutputRate", arrayOf(Int::class.javaPrimitiveType), arrayOf(fmodOutputRate)),
            Triple("nativeSetSoftwareFormat", arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodOutputRate, fmodOutputChannels, 0)),
            Triple("setDSPBufferSize", arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodDspBufferLen, fmodDspNumBuffers)),
            Triple("setStreamBufferSize", arrayOf(Long::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodStreamBufSize.toLong(), 2)),
            Triple("setSoftwareFormat", arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), arrayOf(fmodOutputRate, fmodOutputChannels, 0)),
        )

        for ((name, paramTypes, args) in attempts) {
            runCatching {
                val m = cls.getDeclaredMethod(name, *paramTypes)
                m.isAccessible = true
                val result = if (Modifier.isStatic(m.modifiers)) {
                    m.invoke(null, *args)
                } else {
                    m.invoke(thisObj, *args)
                }
                log(Log.INFO, TAG, "[FMOD] Called $className.$name(${args.toList()}) result=$result")
            }
        }

        // 也尝试在 org.fmod.System 上查找
        runCatching {
            val systemClass = Class.forName("org.fmod.System", false, cls.classLoader)
            log(Log.INFO, TAG, "[FMOD] Found org.fmod.System, scanning methods...")
            val sysMethods = systemClass.declaredMethods
            for (m in sysMethods) {
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                log(Log.INFO, TAG, "[FMOD]   System.${m.name}($params)")
            }

            for ((name, paramTypes, args) in attempts) {
                runCatching {
                    val m = systemClass.getDeclaredMethod(name, *paramTypes)
                    m.isAccessible = true
                    val result = m.invoke(null, *args)
                    log(Log.INFO, TAG, "[FMOD] Called System.$name(${args.toList()}) result=$result")
                }
            }
        }
    }

    /**
     * 修改 FMOD setter 方法的参数。
     */
    private fun buildModifiedFmodArgs(className: String, method: Method, chain: XposedInterface.Chain): Array<Any?> {
        val nameLower = method.name.lowercase()
        val paramTypes = method.parameterTypes
        val args = Array(method.parameterCount) { i -> chain.getArg(i) }

        for (i in 0 until method.parameterCount) {
            val pType = paramTypes[i]

            if (pType == Int::class.javaPrimitiveType || pType == Int::class.java) {
                val intVal = (args[i] as? Number)?.toInt() ?: continue

                if (nameLower.contains("dsp") && nameLower.contains("buffer") && i == 0) {
                    args[i] = fmodDspBufferLen
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (dspBufLen): $intVal -> $fmodDspBufferLen")
                    continue
                }
                if (nameLower.contains("dsp") && nameLower.contains("buffer") && i == 1) {
                    args[i] = fmodDspNumBuffers
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (numBufs): $intVal -> $fmodDspNumBuffers")
                    continue
                }
                if (nameLower.contains("stream") && nameLower.contains("buffer") && i == 0) {
                    args[i] = fmodStreamBufSize
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (streamBuf): $intVal -> $fmodStreamBufSize")
                    continue
                }
                if ((nameLower.contains("rate") || nameLower.contains("samplerate") ||
                     nameLower.contains("format")) && i == 0) {
                    args[i] = fmodOutputRate
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (rate): $intVal -> $fmodOutputRate")
                    continue
                }
                if (nameLower.contains("channel") && !nameLower.contains("group")) {
                    args[i] = fmodOutputChannels
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (channels): $intVal -> $fmodOutputChannels")
                    continue
                }
                if (nameLower.contains("blocksize") || nameLower.contains("block_size")) {
                    args[i] = fmodDspBufferLen
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (blocksize): $intVal -> $fmodDspBufferLen")
                    continue
                }
            }

            if (pType == Long::class.javaPrimitiveType || pType == Long::class.java) {
                val longVal = (args[i] as? Number)?.toLong() ?: continue
                if (nameLower.contains("stream") && nameLower.contains("buffer") && i == 0) {
                    args[i] = fmodStreamBufSize.toLong()
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (streamBuf): $longVal -> $fmodStreamBufSize")
                    continue
                }
                if (nameLower.contains("rate") && i == 0) {
                    args[i] = fmodOutputRate.toLong()
                    log(Log.INFO, TAG, "[FMOD] $className.${method.name} arg[$i] (rate): $longVal -> $fmodOutputRate")
                }
            }
        }

        return args
    }

    /**
     * 篡改 FMOD getter 方法的返回值。
     */
    private fun interceptFmodResult(
        className: String,
        methodName: String,
        returnType: Class<*>,
        original: Any?
    ): Any? {
        val nameLower = methodName.lowercase()

        if (returnType == Int::class.javaPrimitiveType || returnType == Int::class.java) {
            if (nameLower.contains("blocksize") || nameLower.contains("block_size")) return fmodDspBufferLen
            if (nameLower.contains("dsp") && nameLower.contains("buffer")) return fmodDspBufferLen
            if (nameLower.contains("stream") && nameLower.contains("buffer")) return fmodStreamBufSize
            if (nameLower.contains("rate") || nameLower.contains("samplerate")) return fmodOutputRate
            if (nameLower.contains("channel") && !nameLower.contains("group")) return fmodOutputChannels
            if (nameLower.contains("numbuffer")) return fmodDspNumBuffers
            if (nameLower.contains("frame")) return fmodDspBufferLen
            if (nameLower.contains("buffer")) return fmodDspBufferLen
        }

        if (returnType == Long::class.javaPrimitiveType || returnType == Long::class.java) {
            if (nameLower.contains("buffer") || nameLower.contains("stream")) return fmodStreamBufSize.toLong()
            if (nameLower.contains("rate")) return fmodOutputRate.toLong()
        }

        if (returnType == IntArray::class.java) {
            if (nameLower.contains("format") || nameLower.contains("rate") || nameLower.contains("channel")) {
                val arr = original as? IntArray
                if (arr != null && arr.isNotEmpty()) {
                    val hooked = arr.copyOf()
                    if (hooked.size >= 1) hooked[0] = fmodOutputRate
                    if (hooked.size >= 2) hooked[1] = fmodOutputChannels
                    return hooked
                }
            }
        }

        return original
    }
}
