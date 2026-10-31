package com.opentoolbox.app.features.tools

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.SectionTitle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 倒计时预设时长（分钟）。 */
private val presetMinutes = listOf(1, 3, 5, 10)

/**
 * 秒表/计次格式化：分:秒.百分秒（如 03:07.95）；
 * 超过 1 小时进位为 时:分:秒.百分秒（如 1:02:03.04）。
 * 固定 Locale.US，保证任何系统语言下数字宽度一致、等宽对齐。
 */
private fun formatStopwatch(millis: Long): String {
    val safe = if (millis < 0L) 0L else millis
    val hours = safe / 3_600_000L
    val minutes = safe / 60_000L % 60L
    val seconds = safe / 1_000L % 60L
    val centis = safe / 10L % 100L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}

/**
 * 倒计时格式化：分:秒.十分秒（如 04:59.3），分钟数不设上限。
 * 全程 Long 整数运算，避免浮点累积误差。
 */
private fun formatCountdown(millis: Long): String {
    val safe = if (millis < 0L) 0L else millis
    val minutes = safe / 60_000L
    val seconds = safe / 1_000L % 60L
    val tenths = safe / 100L % 10L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

/**
 * 倒计时结束提示音：通知流短促蜂鸣。
 * 音频设备不可用时（构造抛异常）静默忽略，不影响页面其他功能。
 */
private suspend fun playFinishTone() {
    val toneGenerator = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        return
    }
    try {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        delay(350) // 等待提示音播放完成后再释放资源
    } finally {
        try {
            toneGenerator.release()
        } catch (e: RuntimeException) {
            // 释放失败可安全忽略
        }
    }
}

/**
 * 秒表 / 倒计时页面：上半部分秒表支持开始/暂停/复位与运行中计次，
 * 计次记录显示单圈耗时与总耗时；下半部分倒计时支持 1/3/5/10 分钟
 * 预设与自定义分钟数，可暂停/取消，结束时播放提示音。
 *
 * 计时循环基于 System.nanoTime 差值：每帧用「本段起点 + 实际经过时间」
 * 整体回写，暂停只停止循环、不改动已累计值，恢复时重新取起点，
 * 因此暂停不丢时、逐帧回写也不产生截断或浮点累积误差。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    // ===== 秒表状态 =====
    var stopwatchRunning by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    // 每次计次时的总耗时（毫秒），按下顺序保存
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(stopwatchRunning) {
        if (!stopwatchRunning) return@LaunchedEffect
        val segmentStartNanos = System.nanoTime()
        val baseMillis = elapsedMillis
        while (true) {
            withFrameMillis { } // 等待下一帧，帧间用 nanoTime 差值计算真实耗时
            elapsedMillis = baseMillis + (System.nanoTime() - segmentStartNanos) / 1_000_000L
        }
    }

    // ===== 倒计时状态 =====
    var selectedPresetMinutes by remember { mutableStateOf<Int?>(1) }
    var customMinutes by remember { mutableStateOf("") }
    var countdownTotalMillis by remember { mutableStateOf(60_000L) }
    var countdownRemainingMillis by remember { mutableStateOf(60_000L) }
    var countdownRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    /** 应用新的倒计时时长，并停止进行中的倒计时。 */
    fun applyCountdownDuration(millis: Long) {
        countdownTotalMillis = millis
        countdownRemainingMillis = millis
        countdownRunning = false
    }

    // 倒计时循环：以「结束时间点 = 当前时间 + 剩余毫秒」为基准，
    // 每帧回写剩余毫秒；暂停时保留剩余值，恢复时重新取当前时间，
    // 整数毫秒运算无浮点误差，逐帧回写也不累积截断误差。
    LaunchedEffect(countdownRunning) {
        if (!countdownRunning) return@LaunchedEffect
        val endNanos = System.nanoTime() + countdownRemainingMillis * 1_000_000L
        while (true) {
            withFrameMillis { }
            val remainingNanos = endNanos - System.nanoTime()
            if (remainingNanos <= 0L) {
                countdownRemainingMillis = 0L
                countdownRunning = false
                // 用页面级协程播放提示音，避免状态切换取消本协程时打断提示音
                coroutineScope.launch { playFinishTone() }
                break
            }
            countdownRemainingMillis = remainingNanos / 1_000_000L
        }
    }

    val countdownFinished = countdownTotalMillis > 0L && countdownRemainingMillis <= 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("秒表与倒计时") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ===== 秒表分区 =====
            item { SectionTitle("秒表") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "已用时",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatStopwatch(elapsedMillis),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                stopwatchRunning -> "运行中"
                                elapsedMillis > 0L -> "已暂停"
                                else -> "就绪"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (stopwatchRunning) {
                        // 运行中该位置为计次按钮，记录当前总耗时作为一个计次
                        FilledTonalButton(
                            onClick = { laps.add(elapsedMillis) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("计次")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                stopwatchRunning = false
                                elapsedMillis = 0L
                                laps.clear()
                            },
                            enabled = elapsedMillis > 0L || laps.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("复位")
                        }
                    }
                    Button(
                        onClick = { stopwatchRunning = !stopwatchRunning },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                stopwatchRunning -> "暂停"
                                elapsedMillis > 0L -> "继续"
                                else -> "开始"
                            },
                        )
                    }
                }
            }
            if (laps.isNotEmpty()) {
                item {
                    Text(
                        text = "计次记录（共 ${laps.size} 次，最新在上）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 最新的计次排在最上方；key 用按下顺序索引，新增记录不打乱已有项
                items(count = laps.size, key = { index -> laps.size - 1 - index }) { reversedIndex ->
                    val lapIndex = laps.size - 1 - reversedIndex
                    val totalMillis = laps[lapIndex]
                    val lapStartMillis = if (lapIndex > 0) laps[lapIndex - 1] else 0L
                    LapRow(
                        lapNumber = lapIndex + 1,
                        lapMillis = totalMillis - lapStartMillis,
                        totalMillis = totalMillis,
                    )
                }
            }

            // ===== 倒计时分区 =====
            item { SectionTitle("倒计时") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetMinutes.forEach { minutes ->
                        FilterChip(
                            selected = selectedPresetMinutes == minutes,
                            onClick = {
                                selectedPresetMinutes = minutes
                                customMinutes = ""
                                applyCountdownDuration(minutes * 60_000L)
                            },
                            label = { Text("$minutes 分钟") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }.take(3)
                        customMinutes = filtered
                        val minutes = filtered.toLongOrNull()
                        // 任何手动输入都退出预设选择；无效输入时禁用开始
                        selectedPresetMinutes = null
                        applyCountdownDuration(
                            if (minutes != null && minutes > 0L) minutes * 60_000L else 0L,
                        )
                    },
                    label = { Text("自定义分钟数") },
                    placeholder = { Text("如 25") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (countdownFinished) "时间到！" else "剩余时间",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (countdownFinished) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatCountdown(countdownRemainingMillis),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (countdownTotalMillis > 0L) {
                                    countdownRemainingMillis.toFloat() /
                                        countdownTotalMillis.toFloat()
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (countdownTotalMillis > 0L) {
                                "总时长 ${countdownTotalMillis / 60_000L} 分钟"
                            } else {
                                "请选择预设或输入有效分钟数"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            countdownRunning = false
                            countdownRemainingMillis = countdownTotalMillis
                        },
                        enabled = countdownRunning ||
                            (countdownRemainingMillis > 0L &&
                                countdownRemainingMillis < countdownTotalMillis),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (countdownRunning) {
                                countdownRunning = false
                            } else {
                                if (countdownRemainingMillis <= 0L) {
                                    countdownRemainingMillis = countdownTotalMillis
                                }
                                countdownRunning = true
                            }
                        },
                        enabled = countdownTotalMillis > 0L,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                countdownRunning -> "暂停"
                                countdownRemainingMillis in 1L until countdownTotalMillis -> "继续"
                                else -> "开始"
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** 单条计次记录：序号 + 单圈耗时 + 总耗时（等宽字体对齐）。 */
@Composable
private fun LapRow(
    lapNumber: Int,
    lapMillis: Long,
    totalMillis: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 $lapNumber 次",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "单圈 ${formatStopwatch(lapMillis)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "总计 ${formatStopwatch(totalMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
