package com.nsct.lowirofucker

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        // 注册 Xposed Service 监听器
        ServiceManager.register()
        
        setContent {
            LowirofuckerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // 获取 Remote Preferences（可能为 null，若模块未通过 LSPosed 加载）
    val prefs = remember { ConfigManager.getRemotePrefs(ServiceManager.xposedService) }
    
    var hookValue by remember { 
        mutableStateOf(prefs?.let { ConfigManager.getHookValue(it) } ?: ConfigManager.DEFAULT_HOOK_VALUE)
    }
    var bufferSize by remember { 
        mutableStateOf(prefs?.let { ConfigManager.getBufferSize(it) } ?: ConfigManager.DEFAULT_BUFFER_SIZE)
    }
    var isEnabled by remember { 
        mutableStateOf(prefs?.let { ConfigManager.isHookEnabled(it) } ?: ConfigManager.DEFAULT_ENABLED)
    }
    
    // FMOD 配置状态
    var fmodDspBufferLen by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodDspBufferLen(it) } ?: ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN)
    }
    var fmodDspNumBuffers by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodDspNumBuffers(it) } ?: ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS)
    }
    var fmodStreamBufSize by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodStreamBufSize(it) } ?: ConfigManager.DEFAULT_FMOD_STREAM_BUF_SIZE)
    }
    var fmodOutputRate by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodOutputRate(it) } ?: ConfigManager.DEFAULT_FMOD_OUTPUT_RATE)
    }
    var fmodOutputChannels by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodOutputChannels(it) } ?: ConfigManager.DEFAULT_FMOD_OUTPUT_CHANNELS)
    }
    var fmodEnabled by remember {
        mutableStateOf(prefs?.let { ConfigManager.isFmodEnabled(it) } ?: ConfigManager.DEFAULT_FMOD_ENABLED)
    }
    
    val serviceAvailable = prefs != null
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "Lowiro Fucker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )
        
        // 服务状态提示
        if (!serviceAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Xposed 服务不可用",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "请确保模块已在 LSPosed 中激活，且设备已重启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Hook 状态",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "API: Modern Xposed API (libxposed 102)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "配置存储: Remote Preferences",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Hook 目标:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "  - System Property: aaudio.hw_burst_min_usec",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "  - Audio Buffer Size (AudioTrack/Record)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "  - FMOD DSP/Stream Buffer",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 启用/禁用开关
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用 Hook",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isEnabled) "已启用" else "已禁用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    enabled = serviceAvailable,
                    onCheckedChange = { enabled ->
                        isEnabled = enabled
                        prefs?.let { ConfigManager.setEnabled(it, enabled) }
                        Toast.makeText(
                            context,
                            "请重启 moe.low.arc 应用以应用更改",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
        
        // 值设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "System Property Hook",
                    style = MaterialTheme.typography.titleMedium
                )
                
                OutlinedTextField(
                    value = hookValue,
                    onValueChange = { hookValue = it },
                    label = { Text("aaudio.hw_burst_min_usec 值") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable
                )
                
                Text(
                    text = "默认值: 0 (推荐)\n输入数字值,单位为微秒 (usec)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Buffer Size 设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Buffer Size Hook",
                    style = MaterialTheme.typography.titleMedium
                )
                
                OutlinedTextField(
                    value = bufferSize,
                    onValueChange = { bufferSize = it },
                    label = { Text("Buffer Size (frames)") },
                    placeholder = { Text("192") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable
                )
                
                Text(
                    text = "默认值: 192 frames (推荐)\n" +
                            "影响 AudioTrack/AudioRecord 的 buffer 大小\n" +
                            "较小的值 = 更低延迟，但可能导致音频卡顿\n" +
                            "常见值: 96, 128, 192, 256, 512",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // ══════════════════════════════════════════════════════════════════
        // FMOD 缓冲区设置
        // ══════════════════════════════════════════════════════════════════
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FMOD 缓冲区 Hook",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // FMOD 启用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用 FMOD Hook",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "拦截 FMOD 音频引擎的缓冲区设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Switch(
                        checked = fmodEnabled,
                        enabled = serviceAvailable,
                        onCheckedChange = { fmodEnabled = it }
                    )
                }
                
                HorizontalDivider()
                
                // DSP Buffer Length
                OutlinedTextField(
                    value = fmodDspBufferLen,
                    onValueChange = { fmodDspBufferLen = it },
                    label = { Text("DSP Buffer Length") },
                    placeholder = { Text("1024") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                
                // DSP Num Buffers
                OutlinedTextField(
                    value = fmodDspNumBuffers,
                    onValueChange = { fmodDspNumBuffers = it },
                    label = { Text("DSP Num Buffers") },
                    placeholder = { Text("4") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                
                // Stream Buffer Size
                OutlinedTextField(
                    value = fmodStreamBufSize,
                    onValueChange = { fmodStreamBufSize = it },
                    label = { Text("Stream Buffer Size (bytes)") },
                    placeholder = { Text("65536") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                
                // Output Rate
                OutlinedTextField(
                    value = fmodOutputRate,
                    onValueChange = { fmodOutputRate = it },
                    label = { Text("Output Sample Rate (Hz)") },
                    placeholder = { Text("48000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                
                // Output Channels
                OutlinedTextField(
                    value = fmodOutputChannels,
                    onValueChange = { fmodOutputChannels = it },
                    label = { Text("Output Channels") },
                    placeholder = { Text("2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                
                Text(
                    text = "FMOD DSP Buffer: 控制 DSP 处理的块大小\n" +
                            "Num Buffers: DSP 缓冲区数量\n" +
                            "Stream Buffer: 流式音频的读取缓冲\n" +
                            "Output Rate: 输出采样率 (Hz)\n" +
                            "Channels: 输出通道数 (1=单声道, 2=立体声)\n\n" +
                            "设置后需重启目标应用生效\n" +
                            "logcat 过滤 tag: LowiroFucker",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        
        // 保存按钮
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (hookValue.isEmpty()) {
                            Toast.makeText(
                                context,
                                "错误: aaudio.hw_burst_min_usec 值不能为空",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if (bufferSize.isEmpty()) {
                            Toast.makeText(
                                context,
                                "错误: Buffer Size 不能为空",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        
                        try {
                            // 验证是数字
                            hookValue.toInt()
                            bufferSize.toInt()
                            
                            // 验证 FMOD 配置
                            if (fmodEnabled) {
                                fmodDspBufferLen.toInt()
                                fmodDspNumBuffers.toInt()
                                fmodStreamBufSize.toInt()
                                fmodOutputRate.toInt()
                                fmodOutputChannels.toInt()
                            }
                            
                            // 保存到 Remote Preferences
                            prefs?.let { 
                                ConfigManager.saveConfig(it, hookValue, bufferSize, isEnabled)
                                ConfigManager.saveFmodConfig(
                                    it,
                                    fmodDspBufferLen,
                                    fmodDspNumBuffers,
                                    fmodStreamBufSize,
                                    fmodOutputRate,
                                    fmodOutputChannels,
                                    fmodEnabled
                                )
                                Toast.makeText(
                                    context,
                                    "设置已保存，请重启目标应用以生效",
                                    Toast.LENGTH_LONG
                                ).show()
                            } ?: run {
                                Toast.makeText(
                                    context,
                                    "Xposed 服务不可用，无法保存",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: NumberFormatException) {
                            Toast.makeText(
                                context,
                                "错误: 请输入有效的数字",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "保存失败: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceAvailable
                ) {
                    Text("保存所有设置")
                }
            }
        }
        
        // 提示信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "1. 确保已在 LSPosed 中激活本模块\n" +
                            "2. 作用域已自动配置为 moe.low.arc\n" +
                            "3. 设置完成后重启目标应用\n" +
                            "4. Hook 将拦截应用读取的系统属性和音频参数\n" +
                            "5. FMOD Hook 会动态扫描并拦截 FMOD 音频引擎\n" +
                            "6. 查看 logcat (tag: LowiroFucker) 获取篡改日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        // 底部间距
        Spacer(modifier = Modifier.height(16.dp))
    }
}
