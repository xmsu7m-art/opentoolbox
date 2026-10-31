package com.opentoolbox.app.features.adb

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.data.Prefs
import com.opentoolbox.app.ui.components.SectionTitle

/** 查看全部命令。 */
private const val FILTER_ALL = "全部"

/** 只看收藏的命令。 */
private const val FILTER_FAVORITES = "收藏"

/** 查看最近复制的历史记录。 */
private const val FILTER_HISTORY = "历史"

/** 顶部筛选 chip：全部 + 六个分类 + 两个特殊视图。 */
private val filterTabs: List<String> = listOf(
    FILTER_ALL,
    AdbCommandLibrary.CATEGORY_BATTERY,
    AdbCommandLibrary.CATEGORY_APP,
    AdbCommandLibrary.CATEGORY_INPUT,
    AdbCommandLibrary.CATEGORY_DISPLAY,
    AdbCommandLibrary.CATEGORY_NETWORK,
    AdbCommandLibrary.CATEGORY_CAPTURE,
    FILTER_FAVORITES,
    FILTER_HISTORY,
)

/**
 * ADB 快捷命令中心（主标签页）：
 * 命令查阅/搜索/分类筛选、一键复制并记录历史、收藏常用命令、
 * 以及免 Root（Shizuku）与电脑端连接的图文说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(FILTER_ALL) }
    var favorites by remember { mutableStateOf(Prefs.stringSet(context, Prefs.KEY_ADB_FAVORITES)) }
    var history by remember { mutableStateOf(Prefs.stringList(context, Prefs.KEY_ADB_HISTORY)) }

    val copyCommand: (String) -> Unit = { command ->
        clipboard.setText(AnnotatedString(command))
        Toast.makeText(context, "已复制：$command", Toast.LENGTH_SHORT).show()
        Prefs.appendStringList(context, Prefs.KEY_ADB_HISTORY, command)
        history = Prefs.stringList(context, Prefs.KEY_ADB_HISTORY)
    }

    val toggleFavorite: (String) -> Unit = { command ->
        val next = favorites.toMutableSet()
        val wasFavorite = next.remove(command)
        if (!wasFavorite) next.add(command)
        Prefs.putStringSet(context, Prefs.KEY_ADB_FAVORITES, next)
        favorites = next
        Toast.makeText(context, if (wasFavorite) "已取消收藏" else "已收藏", Toast.LENGTH_SHORT).show()
    }

    val deleteHistory: (String) -> Unit = { command ->
        Prefs.removeStringList(context, Prefs.KEY_ADB_HISTORY, command)
        history = Prefs.stringList(context, Prefs.KEY_ADB_HISTORY)
        Toast.makeText(context, "已删除该条记录", Toast.LENGTH_SHORT).show()
    }

    val clearHistory: () -> Unit = {
        Prefs.clearKeys(context, Prefs.KEY_ADB_HISTORY)
        history = emptyList()
        Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ADB 快捷操作") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索标题、命令或描述") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filterTabs) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) },
                        leadingIcon = when (tab) {
                            FILTER_FAVORITES -> {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            FILTER_HISTORY -> {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            else -> null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (selectedTab == FILTER_HISTORY) {
                HistoryContent(
                    history = history,
                    query = query,
                    onCopy = copyCommand,
                    onDelete = deleteHistory,
                    onClear = clearHistory,
                )
            } else {
                CommandContent(
                    query = query,
                    selectedTab = selectedTab,
                    favorites = favorites,
                    onCopy = copyCommand,
                    onToggleFavorite = toggleFavorite,
                )
            }
        }
    }
}

/**
 * 命令列表视图：按分类/收藏 + 关键词过滤后的命令卡片，底部附说明卡片。
 */
@Composable
private fun CommandContent(
    query: String,
    selectedTab: String,
    favorites: Set<String>,
    onCopy: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val keyword = query.trim().lowercase()
    val filtered = AdbCommandLibrary.commands.filter { cmd ->
        val matchTab = when (selectedTab) {
            FILTER_ALL -> true
            FILTER_FAVORITES -> cmd.command in favorites
            else -> cmd.category == selectedTab
        }
        val matchKeyword = keyword.isEmpty() ||
            cmd.title.lowercase().contains(keyword) ||
            cmd.command.lowercase().contains(keyword) ||
            cmd.description.lowercase().contains(keyword)
        matchTab && matchKeyword
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    text = when {
                        selectedTab == FILTER_FAVORITES && keyword.isEmpty() ->
                            "还没有收藏的命令\n点击卡片右侧的星标即可收藏常用命令"
                        else ->
                            "没有找到匹配的命令\n换个关键词或分类试试吧"
                    },
                )
            }
        } else {
            items(filtered, key = { "cmd_${it.command}" }) { cmd ->
                CommandCard(
                    command = cmd,
                    isFavorite = cmd.command in favorites,
                    onCopy = { onCopy(cmd.command) },
                    onToggleFavorite = { onToggleFavorite(cmd.command) },
                )
            }
        }
        item(key = "guide") { AdbGuideCard() }
        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/**
 * 单条命令卡片：标题 + 等宽字体命令 + 描述，右侧为复制与收藏按钮。
 */
@Composable
private fun CommandCard(
    command: AdbCommand,
    isFavorite: Boolean,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = command.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制命令",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * 历史记录视图：最近复制的命令列表，支持再次复制与单条/全部删除。
 */
@Composable
private fun HistoryContent(
    history: List<String>,
    query: String,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    val keyword = query.trim().lowercase()
    val shown = if (keyword.isEmpty()) history else history.filter { it.lowercase().contains(keyword) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "history_header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        text = "历史记录",
                        modifier = Modifier.weight(1f),
                    )
                    if (shown.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("清空历史") }
                    }
                }
                if (shown.isNotEmpty()) {
                    Text(
                        text = "最近复制的命令（新的在前）。点击卡片可再次复制，长按卡片或点删除按钮可移除单条记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "history_empty") {
                EmptyState(
                    text = if (history.isEmpty()) {
                        "暂无历史记录\n复制命令后会自动记录在这里"
                    } else {
                        "没有匹配的历史记录\n换个关键词试试吧"
                    },
                )
            }
        } else {
            items(shown, key = { "history_$it" }) { entry ->
                HistoryCard(
                    entry = entry,
                    onCopy = { onCopy(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
        item(key = "history_guide") { AdbGuideCard() }
        item(key = "history_bottom_spacer") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/**
 * 历史条目卡片：点击复制，长按或点删除按钮移除。
 */
@Composable
private fun HistoryCard(
    entry: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry) {
                detectTapGestures(
                    onTap = { onCopy() },
                    onLongPress = { onDelete() },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "再次复制",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除该条记录",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 底部说明卡片：免 Root（Shizuku）执行步骤 + 电脑端连接基础用法。
 */
@Composable
private fun AdbGuideCard() {
    Column {
        SectionTitle(text = "免 Root 执行说明")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuideStep("1. 在手机「开发者选项」中开启「USB 调试」和「无线调试」。")
                GuideStep("2. 在手机上安装 Shizuku，并按照应用内的引导完成初始化。")
                GuideStep("3. 进入系统「无线调试」页面，选择「使用配对码配对设备」，将配对码与端口填入 Shizuku 完成授权。")
                GuideStep("4. 授权成功后 Shizuku 即持有 shell（adb）权限，本应用通过 Shizuku 即可免 root 以 shell 身份执行上述命令。")
                GuideStep("5. 本页面当前提供命令的查阅与复制能力：复制后可通过电脑端 adb 或支持 Shizuku 的执行场景使用。")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "电脑端连接",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                GuideStep("① 电脑安装 platform-tools 后连接手机，运行 adb devices 确认设备已授权连接。")
                GuideStep("② 运行 adb shell 进入设备交互终端，之后可直接输入命令。")
                GuideStep("③ 也可单条执行：adb shell <命令>，例如 adb shell wm size。")
                GuideStep("④ 本页命令中电池类已带 adb shell 前缀，可直接粘贴到电脑终端；其余为 shell 命令，在 adb shell 会话中直接执行或自行加上 adb shell 前缀。")
            }
        }
    }
}

/**
 * 说明卡片中的一行步骤文字。
 */
@Composable
private fun GuideStep(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 空状态提示（搜索无结果 / 无收藏 / 无历史）。
 */
@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
