# LowiroFucker

一个用于 Arcaea（`moe.low.arc`）的 LSPosed / Xposed 模块，通过篡改音频缓冲区参数来调整游戏音频延迟。

> 本项目与 lowiro 无任何关联，仅供学习交流使用。

## 功能

- **AudioManager 欺骗**：Hook `AudioManager.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER")`，让游戏拿到自定义的帧缓冲大小。
- **FMOD Native Hook**：拦截 `libfmod.so` 的加载（Hook `android_dlopen_ext`），对 FMOD 的 `System::setDSPBufferSize`、`System::init`、`System::setSoftwareFormat` 进行 ARM64 inline hook，直接改写 DSP 缓冲参数。
- **图形化配置界面**：Jetpack Compose (Material 3) 编写，使用 Xposed Service 读写 Remote Preferences，配置实时生效（需重启作用域内程序）。

## 环境要求

- Android 11+（`minSdk 30`），仅支持 **arm64-v8a**
- 已安装 LSPosed（要求 libxposed API 101+，模块使用 API 102）
- 作用域：`moe.low.arc`

## 安装与使用

1. 安装 APK 后，在 LSPosed 管理器中启用模块，并勾选作用域 `moe.low.arc`。
2. 打开本应用，等待配置界面加载（若未激活会弹出提示）。
3. 按需修改参数后点击「保存所有设置」。
4. 重启 Arcaea 使配置生效。

### 可配置项

| 配置项               | 默认值   | 说明                                   |
|-------------------|-------|--------------------------------------|
| AudioManager 欺骗   | 启用    | Hook `OUTPUT_FRAMES_PER_BUFFER` 的返回值 |
| Buffer Size       | `192` | 欺骗 AudioManager 返回的帧缓冲大小             |
| FMOD Native Hook  | 禁用    | 通过 native inline hook 拦截 FMOD 缓冲区设置  |
| DSP Buffer Length | `16`  | 传给 FMOD `setDSPBufferSize` 的缓冲长度     |
| DSP Num Buffers   | `2`   | 传给 FMOD `setDSPBufferSize` 的缓冲数量     |

> FMOD Hook 仅对使用非高度魔改 FMOD 库的应用有效，修改后需重启目标应用。

## 构建

需要 JDK 17+、Android SDK（compileSdk 37）与 NDK / CMake。

```powershell
.\gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/`，文件名为 `LowiroFucker-<variant>-v<version>.apk`。

## 技术实现

- **Java 层**（`HookModule.kt`）：基于 libxposed API 的 `XposedModule`，在 `onPackageReady` 时安装 `AudioManager` Hook，配置通过 Remote Preferences 同步。
- **Native 层**（`native-hook.c`）：纯 C 实现的 ARM64 inline hook（覆写目标函数前 16 字节为跳转指令并保留 trampoline），`dlopen` Hook 等待 `libfmod.so` 加载后按符号名安装。
- **模块 App**（Compose + `ServiceManager`）：通过 `XposedServiceHelper` 监听框架服务，判断模块激活状态并访问 Remote Preferences。

## 目录结构

```
app/src/main/
├── cpp/native-hook.c            # ARM64 inline hook 与 FMOD 拦截
├── java/com/nsct/lowirofucker/
│   ├── ConfigManager.kt         # 配置键、默认值与 Remote Preferences 封装
│   ├── HookModule.kt            # Xposed 模块入口与 Java Hook
│   ├── NativeHook.kt            # Native 库 JNI 接口
│   ├── ServiceManager.kt        # Xposed Service 监听
│   └── MainActivity.kt          # Compose 配置界面
└── resources/META-INF/xposed/   # module.prop / scope.list / java_init.list
```

## 免责声明

本项目仅供学习与技术研究，使用造成的任何后果由使用者自行承担。  

## 侵权联系
如有侵权或其他问题，请通过 GitHub Issues 或通过[邮箱](mailto:nrlt@nuanr-mxi.com)联系。
