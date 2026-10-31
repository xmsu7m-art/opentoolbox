package com.opentoolbox.app.features.device

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.InfoRow
import com.opentoolbox.app.ui.components.SectionTitle
import java.util.Locale

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else String.format(Locale.US, "%.2f %s", value, units[unitIndex])
}

private fun usedPercentText(used: Long, total: Long): String =
    if (total > 0) String.format(Locale.US, "%.1f%%", used * 100.0 / total) else "未知"

private fun rangeText(maximumRange: Float): String = when {
    maximumRange.isNaN() -> "未知"
    maximumRange >= Float.MAX_VALUE / 2f -> "无上限"
    else -> {
        val formatted = String.format(Locale.US, "%.2f", maximumRange)
            .trimEnd('0')
            .trimEnd('.')
        if (formatted.isEmpty()) "0" else formatted
    }
}

private fun refreshRateText(refreshRate: Float?): String =
    refreshRate?.let { String.format(Locale.US, "%.0f Hz", it) } ?: "未知"

/**
 * 设备信息页：系统、CPU、内存、存储、屏幕与传感器信息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val activityManager = remember {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }
    val memoryInfo = remember {
        ActivityManager.MemoryInfo().also { info -> activityManager?.getMemoryInfo(info) }
    }
    val storage = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            stat.totalBytes to stat.availableBytes
        } catch (e: Exception) {
            null
        }
    }
    val view = LocalView.current
    val refreshRate = remember(view) {
        view.display?.refreshRate
            ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
    }
    val metrics = context.resources.displayMetrics
    val sensors = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getSensorList(Sensor.TYPE_ALL)
            .orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备信息") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    SectionTitle("系统信息")
                    InfoRow("厂商", Build.MANUFACTURER)
                    InfoRow("型号", Build.MODEL)
                    InfoRow("设备代号", Build.DEVICE)
                    InfoRow("产品代号", Build.PRODUCT)
                    InfoRow("Android 版本", Build.VERSION.RELEASE)
                    InfoRow("SDK 版本", "API ${Build.VERSION.SDK_INT}")
                    InfoRow("安全补丁级别", Build.VERSION.SECURITY_PATCH.ifEmpty { "未知" })
                    InfoRow("Fingerprint", Build.FINGERPRINT)
                }
            }
            item {
                Column {
                    SectionTitle("CPU")
                    InfoRow("支持的 ABI", Build.SUPPORTED_ABIS.joinToString("、"))
                    InfoRow("核心数", "${Runtime.getRuntime().availableProcessors()} 核")
                }
            }
            item {
                Column {
                    SectionTitle("内存")
                    if (memoryInfo.totalMem > 0) {
                        InfoRow("总内存", humanBytes(memoryInfo.totalMem))
                        InfoRow("可用内存", humanBytes(memoryInfo.availMem))
                        InfoRow(
                            "内存占用",
                            usedPercentText(memoryInfo.totalMem - memoryInfo.availMem, memoryInfo.totalMem),
                        )
                    } else {
                        Text(
                            text = "无法读取内存信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column {
                    SectionTitle("存储（内部存储分区）")
                    val totalBytes = storage?.first ?: 0L
                    if (storage == null || totalBytes <= 0L) {
                        Text(
                            text = "无法读取存储信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        InfoRow("总空间", humanBytes(storage.first))
                        InfoRow("可用空间", humanBytes(storage.second))
                        InfoRow("已用空间", usedPercentText(storage.first - storage.second, storage.first))
                    }
                }
            }
            item {
                Column {
                    SectionTitle("屏幕")
                    InfoRow("分辨率", "${metrics.widthPixels} × ${metrics.heightPixels} px")
                    InfoRow("屏幕密度", "${metrics.densityDpi} dpi（${metrics.density}x）")
                    InfoRow("刷新率", refreshRateText(refreshRate))
                }
            }
            item {
                SectionTitle("传感器（${sensors.size} 个）")
                if (sensors.isEmpty()) {
                    Text(
                        text = "该设备未报告任何传感器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(sensors) { sensor ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = sensor.name, style = MaterialTheme.typography.titleSmall)
                        InfoRow("厂商", sensor.vendor)
                        InfoRow("类型", sensor.stringType)
                        InfoRow("量程", rangeText(sensor.maximumRange))
                    }
                }
            }
        }
    }
}
