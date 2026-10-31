package com.opentoolbox.app.features.apps

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 列表项数据。 */
private data class AppEntry(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: Drawable,
    val sizeBytes: Long,
    val versionName: String?,
)

/** 过滤选项。 */
private enum class AppFilter(val label: String) {
    ALL("全部"),
    USER("用户应用"),
    SYSTEM("系统应用"),
}

/** 排序方式。 */
private enum class SortMode(val label: String) {
    NAME("按名称"),
    SIZE("按大小"),
}

/** 中文应用名按本地化规则排序。 */
private val appNameCollator: Collator = Collator.getInstance()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var sort by remember { mutableStateOf(SortMode.NAME) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // 初始 null 表示正在加载
    val loadedApps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            try {
                pm.getInstalledApplications(0).mapNotNull { info ->
                    runCatching {
                        val versionName = try {
                            pm.getPackageInfo(info.packageName, 0).versionName
                        } catch (e: Exception) {
                            null
                        }
                        AppEntry(
                            name = info.loadLabel(pm).toString(),
                            packageName = info.packageName,
                            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            icon = info.loadIcon(pm),
                            sizeBytes = info.sourceDir?.let { File(it).length() } ?: 0L,
                            versionName = versionName,
                        )
                    }.getOrNull()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val allApps = loadedApps.orEmpty()
    val filteredApps = remember(allApps, query, filter, sort) {
        val q = query.trim()
        allApps.asSequence()
            .filter { app ->
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !app.isSystem
                    AppFilter.SYSTEM -> app.isSystem
                }
            }
            .filter { app ->
                q.isEmpty() ||
                    app.name.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)
            }
            .toList()
            .let { list ->
                when (sort) {
                    SortMode.NAME -> list.sortedWith(compareBy { appNameCollator.getCollationKey(it.name) })
                    SortMode.SIZE -> list.sortedByDescending { it.sizeBytes }
                }
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("应用管理") }) },
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索应用名称或包名") },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.values().forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        SortMode.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortMenuOpen = false
                                },
                                trailingIcon = {
                                    if (sort == option) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            val statsText = buildString {
                append("共 ${allApps.size} 个应用")
                append(" · 用户 ${allApps.count { !it.isSystem }}")
                append(" · 系统 ${allApps.count { it.isSystem }}")
                if (filteredApps.size != allApps.size) {
                    append(" · 匹配 ${filteredApps.size} 个")
                }
            }
            Text(
                text = statsText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    loadedApps == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    filteredApps.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (query.isBlank()) "未读取到应用列表" else "未找到匹配的应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppListItem(
                                    app = app,
                                    onClick = { onOpenApp(app.packageName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(app: AppEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${app.versionName?.let { "v$it" } ?: "版本未知"} · ${formatAppSize(app.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 应用图标：Drawable 转 Bitmap（remember 缓存），48dp 圆角显示。 */
@Composable
private fun AppIcon(app: AppEntry, modifier: Modifier = Modifier) {
    val imageBitmap = remember(app.packageName) {
        runCatching { app.icon.toBitmap().asImageBitmap() }.getOrNull()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = app.name,
            modifier = modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = app.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 文件大小格式化。 */
private fun formatAppSize(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
