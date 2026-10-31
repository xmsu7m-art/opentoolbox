package com.opentoolbox.app.features.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.data.Prefs
import com.opentoolbox.app.ui.components.InfoRow
import com.opentoolbox.app.ui.components.SectionTitle
import com.opentoolbox.app.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 主题三档选项（与 Step Studio 版本对齐）。 */
private val themeOptions = listOf(
    "跟随系统" to Prefs.THEME_MODE_SYSTEM,
    "浅色" to Prefs.THEME_MODE_LIGHT,
    "深色" to Prefs.THEME_MODE_DARK,
)

/** 「数据」卡片中可清除的数据类型。 */
private enum class ClearTarget(
    val dialogTitle: String,
    val dialogText: String,
    val successMessage: String,
    val prefKey: String,
) {
    FAVORITES(
        "清除 ADB 收藏",
        "将删除全部已收藏的 ADB 命令，此操作无法撤销。确定要清除吗？",
        "已清除 ADB 收藏",
        Prefs.KEY_ADB_FAVORITES,
    ),
    HISTORY(
        "清除 ADB 历史",
        "将删除全部 ADB 命令执行历史，此操作无法撤销。确定要清除吗？",
        "已清除 ADB 历史",
        Prefs.KEY_ADB_HISTORY,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val pm = context.packageManager

    val appName by produceState("Open Toolbox") {
        value = withContext(Dispatchers.IO) {
            runCatching { context.applicationInfo.loadLabel(pm).toString() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: "Open Toolbox"
        }
    }
    val versionName by produceState("未知") {
        value = withContext(Dispatchers.IO) {
            runCatching { pm.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "未知"
        }
    }

    var pendingClear by remember { mutableStateOf<ClearTarget?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("关于")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("应用名称", appName)
                    InfoRow("版本", versionName)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Open Toolbox 是一款完全开源、无广告的 Android 工具箱应用。" +
                            "灵感来自 GitHub 开源项目 essentials：" +
                            "https://github.com/sameerasw/essentials",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("外观")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "主题",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = ThemeController.mode == value,
                                onClick = { ThemeController.set(context, value) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        text = when (ThemeController.mode) {
                            Prefs.THEME_MODE_LIGHT -> "当前：浅色，界面始终使用浅色配色。"
                            Prefs.THEME_MODE_DARK -> "当前：深色，界面始终使用深色配色。"
                            else -> "当前：跟随系统，随系统深色模式自动切换。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "在 Android 12 及以上版本支持 Material You 动态取色，" +
                            "界面配色会跟随系统壁纸自动生成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("数据")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("清除 ADB 收藏") },
                        supportingContent = { Text("删除所有已收藏的 ADB 命令") },
                        leadingContent = {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingClear = ClearTarget.FAVORITES },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("清除 ADB 历史") },
                        supportingContent = { Text("删除所有 ADB 命令执行历史") },
                        leadingContent = {
                            Icon(Icons.Outlined.History, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingClear = ClearTarget.HISTORY },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingClear?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(target.dialogTitle) },
            text = { Text(target.dialogText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        Prefs.clearKeys(context, target.prefKey)
                        Toast.makeText(context, target.successMessage, Toast.LENGTH_SHORT).show()
                        pendingClear = null
                    },
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("取消") }
            },
        )
    }
}
