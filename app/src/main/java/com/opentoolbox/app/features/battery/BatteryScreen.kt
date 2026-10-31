package com.opentoolbox.app.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.InfoRow
import com.opentoolbox.app.ui.components.SectionTitle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 一次 ACTION_BATTERY_CHANGED 广播解析出的电池快照。
 */
private data class BatterySnapshot(
    val levelPercent: Int?,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val temperature: Int,
    val voltageMv: Int,
    val technology: String?,
)

private fun parseSnapshot(intent: Intent): BatterySnapshot {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
    return BatterySnapshot(
        levelPercent = percent?.coerceIn(0, 100),
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
        temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE),
        voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE),
        technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
    )
}

private fun statusText(status: Int): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
    BatteryManager.BATTERY_STATUS_FULL -> "已充满"
    else -> "未知"
}

private fun pluggedText(plugged: Int): String = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC -> "AC 电源"
    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
    BatteryManager.BATTERY_PLUGGED_DOCK -> "底座充电"
    else -> "未接电源"
}

private fun healthText(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
    BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
    BatteryManager.BATTERY_HEALTH_COLD -> "低温"
    else -> "未知"
}

private fun temperatureText(tenths: Int): String =
    if (tenths == Int.MIN_VALUE) "未知" else String.format(Locale.US, "%.1f ℃", tenths / 10f)

private fun voltageText(mv: Int): String =
    if (mv == Int.MIN_VALUE || mv <= 0) "未知" else String.format(Locale.US, "%.2f V", mv / 1000f)

private fun technologyText(technology: String?): String =
    technology?.takeIf { it.isNotBlank() } ?: "未知"

private fun currentText(currentUa: Int?): String =
    currentUa?.let { String.format(Locale.US, "%+.1f mA", it / 1000f) } ?: "设备不支持"

private fun chargeSpeedText(currentUa: Int?, isCharging: Boolean): String {
    val ua = currentUa ?: return "设备不支持"
    return if (isCharging) String.format(Locale.US, "%.1f mA", abs(ua) / 1000f) else "未在充电"
}

private fun powerText(snapshot: BatterySnapshot?, currentUa: Int?, isCharging: Boolean): String {
    if (!isCharging) return "未在充电"
    val mv = snapshot?.voltageMv
    val ua = currentUa
    if (mv == null || mv <= 0 || ua == null) return "未知"
    // 功率(W) = 电压(mV)/1000 × 电流(µA)/1000000 = mV × µA / 1e9
    val watts = mv.toLong() * abs(ua.toLong()) / 1_000_000_000.0
    return if (watts <= 0.0) "未知" else String.format(Locale.US, "%.2f W", watts)
}

private fun chargeCounterText(mah: Int?): String =
    mah?.let { "$it mAh" } ?: "设备不支持"

/**
 * 电池监控页：环形电量、状态、健康度、实时电流与电量历史曲线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val batteryManager = remember {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    var snapshot by remember { mutableStateOf<BatterySnapshot?>(null) }
    var currentNowUa by remember { mutableStateOf<Int?>(null) }
    var chargeCounterMah by remember { mutableStateOf<Int?>(null) }
    val history = remember { mutableStateListOf<Int>() }

    // 注册 ACTION_BATTERY_CHANGED 广播；系统广播注册后立即回传当前状态。
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                intent?.let { snapshot = parseSnapshot(it) }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) {
            snapshot = parseSnapshot(sticky)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // 每 2 秒读取一次 BatteryManager 属性，驱动实时电流、剩余容量与历史曲线。
    LaunchedEffect(batteryManager) {
        while (true) {
            batteryManager?.let { manager ->
                val capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity in 0..100) {
                    history.add(capacity)
                    if (history.size > 60) {
                        history.removeAt(0)
                    }
                }
                val current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                currentNowUa = if (current == Int.MIN_VALUE) null else current
                val counter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                chargeCounterMah = if (counter == Int.MIN_VALUE) null else counter / 1000
            }
            delay(2000)
        }
    }

    val percent = snapshot?.levelPercent ?: history.lastOrNull()
    val isCharging = snapshot?.status == BatteryManager.BATTERY_STATUS_CHARGING ||
        snapshot?.status == BatteryManager.BATTERY_STATUS_FULL

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池监控") },
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
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                BatteryGauge(percent = percent)
            }
            item {
                Column {
                    SectionTitle("电池状态")
                    InfoRow("状态", snapshot?.let { statusText(it.status) } ?: "读取中…")
                    InfoRow("电源类型", snapshot?.let { pluggedText(it.plugged) } ?: "读取中…")
                    InfoRow("健康度", snapshot?.let { healthText(it.health) } ?: "读取中…")
                }
            }
            item {
                Column {
                    SectionTitle("硬件参数")
                    InfoRow("电池温度", snapshot?.let { temperatureText(it.temperature) } ?: "读取中…")
                    InfoRow("电压", snapshot?.let { voltageText(it.voltageMv) } ?: "读取中…")
                    InfoRow("电池技术", snapshot?.let { technologyText(it.technology) } ?: "读取中…")
                }
            }
            item {
                Column {
                    SectionTitle("实时数据")
                    InfoRow("实时电流", currentText(currentNowUa))
                    InfoRow("充电速度", chargeSpeedText(currentNowUa, isCharging))
                    InfoRow("估算充电功率", powerText(snapshot, currentNowUa, isCharging))
                    InfoRow("剩余容量", chargeCounterText(chargeCounterMah))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "说明：电流为负表示放电，为正表示充电；部分设备固件不支持读取实时电流。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column {
                    SectionTitle("电量历史")
                    HistoryChart(history = history)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (history.isEmpty()) {
                            "正在采集电量数据…"
                        } else {
                            "最近 ${history.size} 次电量采样，每 2 秒记录一次"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 环形电量指示：外圈 Canvas drawArc，中心显示百分比数字。
 */
@Composable
private fun BatteryGauge(percent: Int?, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor =
        if ((percent ?: 100) <= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                val sweep = ((percent?.coerceIn(0, 100) ?: 0) / 100f) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percent?.toString() ?: "--",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 电量历史折线：最近 60 个采样，最新值固定在最右侧。
 */
@Composable
private fun HistoryChart(history: List<Int>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        // 0% / 50% / 100% 参考线
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = size.height * (1f - fraction)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (history.isEmpty()) return@Canvas

        val count = history.size
        val stepX = size.width / 59f
        fun x(index: Int): Float = size.width - (count - 1 - index) * stepX
        fun y(value: Int): Float = size.height * (1f - value.coerceIn(0, 100) / 100f)

        val line = Path()
        history.forEachIndexed { index, value ->
            if (index == 0) line.moveTo(x(index), y(value)) else line.lineTo(x(index), y(value))
        }
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val area = Path().apply {
            addPath(line)
            lineTo(x(count - 1), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)),
        )
    }
}
