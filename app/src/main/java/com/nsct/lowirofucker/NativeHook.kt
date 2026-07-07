package com.nsct.lowirofucker

object NativeHook {
    init {
        System.loadLibrary("native-hook")
    }

    @JvmStatic
    external fun nativeInit()

    @JvmStatic
    external fun nativeInstallHook(libName: String, bufLen: Int, numBufs: Int): Int

    @JvmStatic
    external fun nativeUpdateConfig(bufLen: Int, numBufs: Int)

    @JvmStatic
    external fun nativeIsHookInstalled(): Boolean

    @JvmStatic
    external fun nativeGetHookCallCount(): Int
}
