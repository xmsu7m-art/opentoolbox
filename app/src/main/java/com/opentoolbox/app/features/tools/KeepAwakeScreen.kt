package com.opentoolbox.app.features.tools

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.SectionTitle

/**
 * 屏幕常亮页面：通过窗口标志 FLAG_KEEP_SCREEN_ON 保持屏幕常亮，
 * 仅在本应用前台时生效，无需任何权限；离开页面时自动恢复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAwakeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isActivity = activity != null

    var keepAwake by remember {
        val flags = activity?.window?.attributes?.flags ?: 0
        mutableStateOf((flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)
    }

    // 离开页面时清除常亮标志
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕常亮") },
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
            SectionTitle("状态")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrightnessHigh,
                        contentDescription = null,
                        tint = if (keepAwake) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(40.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (keepAwake) "屏幕常亮已开启" else "屏幕常亮已关闭",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (keepAwake) {
                                "屏幕将保持常亮，不会自动熄灭"
                            } else {
                                "屏幕将在系统设定的超时时间后自动熄灭"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = keepAwake,
                        onCheckedChange = { on ->
                            keepAwake = on
                            val window = activity?.window
                            if (on) {
                                window?.addFlags(
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                )
                            } else {
                                window?.clearFlags(
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                )
                            }
                        },
                        enabled = isActivity,
                    )
                }
            }

            if (!isActivity) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前界面上下文不是 Activity（例如预览环境），无法控制屏幕常亮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionTitle("说明")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "• 开关通过设置窗口标志 FLAG_KEEP_SCREEN_ON 实现，无需任何权限。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "• 仅在本应用处于前台时生效；切到后台或离开本页面后，屏幕恢复系统默认的熄屏行为。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "• 屏幕常亮会增加耗电，建议仅在阅读、导航等需要长时间注视屏幕的场景开启。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
