package com.opentoolbox.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.FeatureCard
import com.opentoolbox.app.ui.navigation.TabRoutes

private data class ToolEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
)

private val toolEntries = listOf(
    ToolEntry("电池监控", "电量、温度、健康度与充电状态", Icons.Outlined.BatteryFull, TabRoutes.Battery),
    ToolEntry("设备信息", "系统、CPU、内存、屏幕与传感器", Icons.Outlined.Smartphone, TabRoutes.DeviceInfo),
    ToolEntry("网络信息", "Wi-Fi、IP 地址与连接状态", Icons.Outlined.Wifi, TabRoutes.Network),
    ToolEntry("手电筒", "开关与亮度调节", Icons.Outlined.FlashlightOn, TabRoutes.Flashlight),
    ToolEntry("指南针", "实时方位角与磁力计", Icons.Outlined.Explore, TabRoutes.Compass),
    ToolEntry("屏幕检测", "坏点、灰阶与色彩测试", Icons.Outlined.Contrast, TabRoutes.ScreenTest),
    ToolEntry("单位换算", "长度、重量、温度等换算", Icons.Outlined.Calculate, TabRoutes.UnitConverter),
    ToolEntry("振动测试", "短振、长振与节奏振动", Icons.Outlined.Vibration, TabRoutes.Vibration),
    ToolEntry("屏幕常亮", "保持屏幕常亮开关", Icons.Outlined.BrightnessHigh, TabRoutes.KeepAwake),
    ToolEntry("文本工具", "统计、编码转换与文本处理", Icons.Outlined.TextFields, TabRoutes.TextTools),
    ToolEntry("随机工具", "随机数、骰子、硬币与密码生成", Icons.Outlined.Casino, TabRoutes.RandomTools),
    ToolEntry("秒表计时", "秒表计次与倒计时提醒", Icons.Outlined.Timer, TabRoutes.Stopwatch),
    ToolEntry("颜色工具", "色值转换、变亮变暗与常用色板", Icons.Outlined.ColorLens, TabRoutes.ColorTools),
    ToolEntry("分贝计", "环境噪声测量与分级提示", Icons.Outlined.Mic, TabRoutes.SoundMeter),
    ToolEntry("日期工具", "日期间隔、推算与今天速查", Icons.Outlined.CalendarMonth, TabRoutes.DateTools),
    ToolEntry("水平仪", "双轴气泡水平仪与单轴水平尺", Icons.Outlined.Straighten, TabRoutes.Level),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHomeScreen(onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("全能工具箱") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(toolEntries) { tool ->
                FeatureCard(
                    title = tool.title,
                    description = tool.description,
                    icon = tool.icon,
                    onClick = { onOpen(tool.route) },
                )
            }
        }
    }
}
