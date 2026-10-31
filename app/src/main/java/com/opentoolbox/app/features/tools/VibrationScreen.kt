package com.opentoolbox.app.features.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.SectionTitle

/**
 * 振动测试页面：短振、长振、节奏振动、双重振动与停止，
 * 全部通过 VibrationEffect 实现，设备不支持时给出提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vibrator: Vibrator? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    val supported = vibrator?.hasVibrator() == true

    fun notifyFailure(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun performVibration(action: (Vibrator) -> Unit) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) {
            notifyFailure("当前设备不支持振动")
            return
        }
        try {
            action(v)
        } catch (e: Exception) {
            notifyFailure("振动失败：${e.message ?: "未知错误"}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("振动测试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (!supported) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "当前设备没有振动器，或系统已禁用振动功能，无法进行测试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionTitle("单击振动")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VibrateButton(
                    label = "短振（100 毫秒）",
                    enabled = supported,
                ) {
                    performVibration {
                        it.vibrate(
                            VibrationEffect.createOneShot(
                                100L,
                                VibrationEffect.DEFAULT_AMPLITUDE,
                            ),
                        )
                    }
                }
                VibrateButton(
                    label = "长振（1000 毫秒）",
                    enabled = supported,
                ) {
                    performVibration {
                        it.vibrate(
                            VibrationEffect.createOneShot(
                                1000L,
                                VibrationEffect.DEFAULT_AMPLITUDE,
                            ),
                        )
                    }
                }
            }

            SectionTitle("模式振动")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VibrateButton(
                    label = "节奏振动（短-短-长）",
                    enabled = supported,
                ) {
                    performVibration {
                        it.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 150L, 100L, 150L, 100L, 300L),
                                -1,
                            ),
                        )
                    }
                }
                VibrateButton(
                    label = "双重振动（嗒-嗒）",
                    enabled = supported,
                ) {
                    performVibration {
                        it.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 120L, 160L, 120L),
                                -1,
                            ),
                        )
                    }
                }
            }

            SectionTitle("控制")
            VibrateButton(
                label = "停止振动",
                enabled = supported,
                danger = true,
            ) {
                performVibration { it.cancel() }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "说明：以上效果通过 VibrationEffect 驱动系统振动器，" +
                    "实际强度受系统振动强度设置影响；触发后可随时点击「停止振动」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 振动测试大按钮，danger 为 true 时使用错误色容器。 */
@Composable
private fun VibrateButton(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
