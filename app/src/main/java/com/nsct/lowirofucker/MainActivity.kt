package com.nsct.lowirofucker

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

@Composable
fun ConfigScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { ConfigManager.getRemotePrefs(ServiceManager.xposedService) }

    var bufferSize by remember {
        mutableStateOf(prefs?.let { ConfigManager.getBufferSize(it) } ?: ConfigManager.DEFAULT_BUFFER_SIZE)
    }
    var isEnabled by remember {
        mutableStateOf(prefs?.let { ConfigManager.isHookEnabled(it) } ?: ConfigManager.DEFAULT_ENABLED)
    }
    var fmodDspBufferLen by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodDspBufferLen(it) } ?: ConfigManager.DEFAULT_FMOD_DSP_BUFFER_LEN)
    }
    var fmodDspNumBuffers by remember {
        mutableStateOf(prefs?.let { ConfigManager.getFmodDspNumBuffers(it) } ?: ConfigManager.DEFAULT_FMOD_DSP_NUM_BUFFERS)
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
        Text(
            text = "去你妈的Lowiro",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )

        if (!serviceAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Xposed 服务不可用", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("请确保模块已在 LSPosed 中激活，若勾选了系统框架，则应该重启系统", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

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
                Switch(checked = isEnabled, enabled = serviceAvailable, onCheckedChange = { enabled ->
                    isEnabled = enabled
                    prefs?.let { ConfigManager.setEnabled(it, enabled) }
                    Toast.makeText(context, "请重启Arcaea以应用更改", Toast.LENGTH_LONG).show()
                })
            }
        }

        // Buffer Size
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AudioManager Buffer Size", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = bufferSize, onValueChange = { bufferSize = it },
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
                Text("FMOD Native Hook（仅对Arcaea生效）", style = MaterialTheme.typography.titleMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用 FMOD Hook", style = MaterialTheme.typography.bodyLarge)
                        Text("通过 native inline hook 拦截 FMOD 缓冲区设置", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Switch(checked = fmodEnabled, enabled = serviceAvailable, onCheckedChange = { fmodEnabled = it })
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = fmodDspBufferLen, onValueChange = { fmodDspBufferLen = it },
                    label = { Text("DSP Buffer Length") }, placeholder = { Text("16") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )
                OutlinedTextField(
                    value = fmodDspNumBuffers, onValueChange = { fmodDspNumBuffers = it },
                    label = { Text("DSP Num Buffers") }, placeholder = { Text("2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    enabled = serviceAvailable && fmodEnabled
                )

                Text("设置后需重启目标应用生效\nlogcat 过滤 tag: LowiroFucker",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        // 保存按钮
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        if (bufferSize.isEmpty()) {
                            Toast.makeText(context, "Buffer Size 不能为空", Toast.LENGTH_LONG).show()
                            return@Button
                        }
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceAvailable
                ) { Text("保存所有设置") }
            }
        }

        // 使用说明
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("使用说明", style = MaterialTheme.typography.titleSmall)
                Text("1. 确保已在 LSPosed 中激活本模块\n" +
                        "2. 作用域已自动配置为 moe.low.arc\n" +
                        "3. 设置完成后重启作用域内程序\n" +
                        "4. AudioManager 欺骗: 修改 OUTPUT_FRAMES_PER_BUFFER\n" +
                        "5. FMOD Native Hook: 强制设置Arcaea对FMOD设置的各种参数\n" +
                        "6. 查看 logcat (tag: LowiroFucker) 获取日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
