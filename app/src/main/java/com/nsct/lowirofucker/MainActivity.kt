package com.nsct.lowirofucker

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nsct.lowirofucker.ui.theme.LowirofuckerTheme
import kotlinx.coroutines.delay

// 等待框架异步送达 binder 的宽限期，期间不显示任何内容，避免误报未激活
private const val SERVICE_DETECT_TIMEOUT_MS = 1200L

private const val CONTENT_ANIM_MS = 500

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LowirofuckerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity

    val service by ServiceManager.service.collectAsState()

    var prefs by remember { mutableStateOf<SharedPreferences?>(null) }
    var showNotActivated by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    var bufferSize by remember { mutableStateOf(ConfigManager.DEFAULT_BUFFER_SIZE) }
    var isEnabled by remember { mutableStateOf(ConfigManager.DEFAULT_ENABLED) }
    var fmodDspBufferLen by remember { mutableStateOf(ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN) }
    var fmodDspNumBuffers by remember { mutableStateOf(ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS) }
    var fmodEnabled by remember { mutableStateOf(ConfigManager.DEFAULT_FMOD_ENABLED) }

    // 服务未就绪前不渲染任何内容；激活则载入配置并播放过渡动画，超时未激活则弹窗
    LaunchedEffect(service) {
        showNotActivated = false
        prefs = null
        contentVisible = false
        val current = service
        if (current == null) {
            delay(SERVICE_DETECT_TIMEOUT_MS)
            showNotActivated = true
        } else {
            prefs = ConfigManager.awaitRemotePrefs(current)
            if (prefs == null) showNotActivated = true
        }
    }

    // 读取完成后同步已保存的配置
    LaunchedEffect(prefs) {
        val current = prefs ?: return@LaunchedEffect
        bufferSize = ConfigManager.getBufferSize(current)
        isEnabled = ConfigManager.isHookEnabled(current)
        fmodDspBufferLen = ConfigManager.getFmodDspBufferLen(current)
        fmodDspNumBuffers = ConfigManager.getFmodDspNumBuffers(current)
        fmodEnabled = ConfigManager.isFmodEnabled(current)
        contentVisible = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(CONTENT_ANIM_MS, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(CONTENT_ANIM_MS, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 12 }
                    ),
            exit = fadeOut(tween(200))
        ) {
            ConfigContent(
                serviceAvailable = prefs != null,
                bufferSize = bufferSize,
                onBufferSizeChange = { bufferSize = it },
                isEnabled = isEnabled,
                onEnabledChange = { enabled ->
                    isEnabled = enabled
                    prefs?.let { ConfigManager.setEnabled(it, enabled) }
                    Toast.makeText(context, "请重启Arcaea以应用更改", Toast.LENGTH_LONG).show()
                },
                fmodDspBufferLen = fmodDspBufferLen,
                onFmodDspBufferLenChange = { fmodDspBufferLen = it },
                fmodDspNumBuffers = fmodDspNumBuffers,
                onFmodDspNumBuffersChange = { fmodDspNumBuffers = it },
                fmodEnabled = fmodEnabled,
                onFmodEnabledChange = { fmodEnabled = it },
                onSave = {
                    if (bufferSize.isEmpty()) {
                        Toast.makeText(context, "Buffer Size 不能为空", Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            bufferSize.toInt()
                            if (fmodEnabled) { fmodDspBufferLen.toInt(); fmodDspNumBuffers.toInt() }
                            prefs?.let {
                                ConfigManager.saveConfig(it, bufferSize, isEnabled,
                                    fmodDspBufferLen, fmodDspNumBuffers, fmodEnabled)
                                Toast.makeText(context, "设置已保存，请重启作用域内程序以生效", Toast.LENGTH_LONG).show()
                            } ?: Toast.makeText(context, "Xposed 服务不可用", Toast.LENGTH_LONG).show()
                        } catch (e: NumberFormatException) {
                            Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    if (showNotActivated) {
        NotActivatedDialog(
            onDismiss = { exitApp(activity) },
            onConfirm = { exitApp(activity) }
        )
    }
}

@Composable
internal fun ConfigContent(
    serviceAvailable: Boolean,
    bufferSize: String,
    onBufferSizeChange: (String) -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    fmodDspBufferLen: String,
    onFmodDspBufferLenChange: (String) -> Unit,
    fmodDspNumBuffers: String,
    onFmodDspNumBuffersChange: (String) -> Unit,
    fmodEnabled: Boolean,
    onFmodEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "去你妈的Lowiro",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )

        // 启用开关
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AudioManager 欺骗", style = MaterialTheme.typography.titleMedium)
                    Text(if (isEnabled) "已启用" else "已禁用", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isEnabled, enabled = serviceAvailable, onCheckedChange = onEnabledChange)
            }
        }

        // Buffer Size
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AudioManager Buffer Size", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = bufferSize, onValueChange = onBufferSizeChange,
                    label = { Text("OUTPUT_FRAMES_PER_BUFFER") }, placeholder = { Text("192") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = serviceAvailable
                )
                Text("欺骗 AudioManager 返回的帧缓冲大小", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // FMOD 设置
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("FMOD Native Hook（仅对Arcaea或其它使用非高度魔改fmod库的应用生效）", style = MaterialTheme.typography.titleMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用 FMOD Hook", style = MaterialTheme.typography.bodyLarge)
                        Text("通过 native inline hook 拦截 FMOD 缓冲区设置", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Switch(checked = fmodEnabled, enabled = serviceAvailable, onCheckedChange = onFmodEnabledChange)
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = fmodDspBufferLen, onValueChange = onFmodDspBufferLenChange,
                    label = { Text("DSP Buffer Length") }, placeholder = { Text("16") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                OutlinedTextField(
                    value = fmodDspNumBuffers, onValueChange = onFmodDspNumBuffersChange,
                    label = { Text("DSP Num Buffers") }, placeholder = { Text("2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )

                Text("设置后需重启目标应用生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        // 保存按钮
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceAvailable
                ) { Text("保存所有设置") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun NotActivatedDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("你还没激活插件") },
        text = { Text("请在 LSPosed 中启用本模块并勾选作用域，然后重新打开本应用。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("知道了") }
        }
    )
}

private fun exitApp(activity: Activity?) {
    activity?.finishAffinity()
    Process.killProcess(Process.myPid())
}

