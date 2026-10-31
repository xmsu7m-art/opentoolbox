package com.opentoolbox.app.features.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.opentoolbox.app.ui.components.InfoRow
import com.opentoolbox.app.ui.components.SectionTitle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 详情页状态。 */
private sealed interface DetailState {
    object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Loaded(
        val packageInfo: PackageInfo,
        val appInfo: ApplicationInfo,
        val appName: String,
        val icon: Drawable?,
        val isSystem: Boolean,
        val permissions: List<PermissionEntry>,
    ) : DetailState
}

/** 单条权限信息。 */
private data class PermissionEntry(
    val name: String,
    val granted: Boolean,
    val description: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    val state by produceState<DetailState>(DetailState.Loading, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                val ai = pi.applicationInfo
                if (ai == null) {
                    DetailState.Error("应用信息不完整，无法展示")
                } else {
                    val appName = runCatching { ai.loadLabel(pm).toString() }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
                    val icon = runCatching { ai.loadIcon(pm) }.getOrNull()
                    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val permissions = buildList {
                        val names = pi.requestedPermissions
                        val flags = pi.requestedPermissionsFlags
                        if (names != null) {
                            names.forEachIndexed { index, name ->
                                val granted = flags != null &&
                                    index < flags.size &&
                                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                                val description = runCatching {
                                    pm.getPermissionInfo(name, 0).loadDescription(pm)?.toString()
                                }.getOrNull()
                                add(
                                    PermissionEntry(
                                        name = name,
                                        granted = granted,
                                        description = description,
                                    ),
                                )
                            }
                        }
                    }
                    DetailState.Loaded(pi, ai, appName, icon, isSystem, permissions)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                DetailState.Error("未找到包名为「$packageName」的应用，可能已被卸载")
            } catch (e: Exception) {
                DetailState.Error("读取应用信息失败：${e.message ?: "未知错误"}")
            }
        }
    }

    val loaded = state as? DetailState.Loaded
    val launchIntent = remember(loaded, packageName) {
        loaded?.let { runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull() }
    }

    fun notifyFailure(action: String, e: Exception) {
        Toast.makeText(context, "${action}失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.appName?.takeIf { it.isNotBlank() } ?: "应用详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            if (loaded != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val intent = launchIntent
                                if (intent != null) {
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        notifyFailure("打开应用", e)
                                    }
                                }
                            },
                            enabled = launchIntent != null,
                            modifier = Modifier.weight(1f),
                        ) { Text("打开") }

                        FilledTonalButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:$packageName"),
                                        ),
                                    )
                                } catch (e: Exception) {
                                    notifyFailure("打开应用信息", e)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("应用信息") }

                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_DELETE,
                                        Uri.parse("package:$packageName"),
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    notifyFailure("卸载", e)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("卸载") }
                    }
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            is DetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is DetailState.Error -> {
                DetailErrorContent(
                    message = current.message,
                    onBack = onBack,
                    modifier = Modifier.padding(padding),
                )
            }

            is DetailState.Loaded -> {
                DetailContent(loaded = current, modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun DetailContent(loaded: DetailState.Loaded, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // 头部：图标 + 应用名 + 包名 + 版本
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppHeaderIcon(icon = loaded.icon)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = loaded.appName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = loaded.packageInfo.packageName ?: loaded.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = versionText(loaded.packageInfo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        SectionTitle("基本信息")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                InfoRow("目标 SDK", loaded.appInfo.targetSdkVersion.toString())
                InfoRow("最低 SDK", loaded.appInfo.minSdkVersion.toString())
                InfoRow("首次安装时间", formatAppDate(loaded.packageInfo.firstInstallTime))
                InfoRow("最近更新", formatAppDate(loaded.packageInfo.lastUpdateTime))
                InfoRow(
                    label = "APK 大小",
                    value = formatApkSize(
                        loaded.appInfo.sourceDir?.let { File(it).length() } ?: 0L,
                    ),
                )
                InfoRow("应用类型", if (loaded.isSystem) "系统应用" else "用户应用")
            }
        }

        SectionTitle("权限（${loaded.permissions.size} 项）")
        if (loaded.permissions.isEmpty()) {
            Text(
                text = "该应用未申请任何权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                loaded.permissions.forEach { permission ->
                    PermissionCard(permission = permission)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 头部应用图标，加载失败时使用默认图标。 */
@Composable
private fun AppHeaderIcon(icon: Drawable?, modifier: Modifier = Modifier) {
    val imageBitmap = remember(icon) {
        icon?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条权限卡片。 */
@Composable
private fun PermissionCard(permission: PermissionEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (permission.granted) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.RemoveCircleOutline
                    },
                    contentDescription = null,
                    tint = if (permission.granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = permission.name.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (permission.granted) "已授予" else "未授予",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (permission.granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            permission.description?.let { description ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = permission.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 错误提示 + 返回按钮。 */
@Composable
private fun DetailErrorContent(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "无法读取应用信息",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}

/** 版本名 + 版本号。 */
private fun versionText(packageInfo: PackageInfo): String {
    val code: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    val name = packageInfo.versionName ?: "未知"
    return "版本 $name ($code)"
}

/** 时间戳格式化。 */
private fun formatAppDate(millis: Long): String =
    if (millis <= 0L) {
        "未知"
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

/** 文件大小格式化。 */
private fun formatApkSize(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
