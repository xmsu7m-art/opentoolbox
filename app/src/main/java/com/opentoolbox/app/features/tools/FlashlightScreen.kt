package com.opentoolbox.app.features.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * 手电筒页面：运行时申请相机权限，控制闪光灯开关，
 * 设备支持（API 33+ 且最大强度等级 > 1）时提供亮度调节。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        requestedOnce = true
    }

    var torchOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 授权后查找带闪光灯的摄像头：cameraId + 闪光灯最大强度等级
    val flashInfo = remember(granted) {
        if (granted) findFlashCamera(cameraManager) else null
    }
    val flashId = flashInfo?.first
    val maxLevel = flashInfo?.second ?: 1

    fun toggleTorch(on: Boolean, level: Float) {
        val id = flashId ?: return
        try {
            if (on && maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val strength = level.roundToInt().coerceIn(1, maxLevel)
                cameraManager?.turnOnTorchWithStrengthLevel(id, strength)
            } else {
                cameraManager?.setTorchMode(id, on)
            }
            torchOn = on
            errorMessage = null
        } catch (_: CameraAccessException) {
            errorMessage = "手电筒操作失败：相机服务暂不可用，请稍后重试"
        } catch (_: SecurityException) {
            errorMessage = "手电筒操作失败：相机权限被拒绝"
        } catch (_: IllegalArgumentException) {
            errorMessage = "手电筒操作失败：闪光灯设备不可用"
        }
    }

    // 离开页面时自动关灯
    DisposableEffect(flashId) {
        val id = flashId
        onDispose {
            if (id != null) {
                try {
                    cameraManager?.setTorchMode(id, false)
                } catch (_: Exception) {
                    // 页面销毁时忽略关闭失败
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手电筒") },
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !granted -> PermissionHint(
                    requestedOnce = requestedOnce,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )

                flashId == null -> NoFlashHint()

                else -> {
                    Spacer(Modifier.height(24.dp))
                    TorchCard(
                        torchOn = torchOn,
                        onToggle = { on -> toggleTorch(on, brightness) },
                    )
                    Spacer(Modifier.height(16.dp))
                    BrightnessCard(
                        supported = maxLevel > 1,
                        brightness = brightness,
                        maxLevel = maxLevel,
                        onLevelChange = { newLevel ->
                            brightness = newLevel
                            if (torchOn) toggleTorch(true, newLevel)
                        },
                    )
                }
            }
            errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** 查找带闪光灯的摄像头，返回 cameraId 与闪光灯最大强度等级（API < 33 时恒为 1）。 */
private fun findFlashCamera(manager: CameraManager?): Pair<String?, Int> {
    if (manager == null) return null to 1
    return try {
        var foundId: String? = null
        var strengthMax = 1
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                foundId = id
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    strengthMax = characteristics.get(
                        CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL,
                    ) ?: 1
                }
                break
            }
        }
        foundId to strengthMax
    } catch (_: CameraAccessException) {
        null to 1
    } catch (_: SecurityException) {
        null to 1
    } catch (_: IllegalArgumentException) {
        null to 1
    }
}

/** 开关卡片：图标 + 状态说明 + 大开关。 */
@Composable
private fun TorchCard(torchOn: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = if (torchOn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (torchOn) {
                        Icons.Outlined.FlashlightOn
                    } else {
                        Icons.Outlined.FlashlightOff
                    },
                    contentDescription = null,
                    tint = if (torchOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (torchOn) "手电筒已开启" else "手电筒已关闭",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (torchOn) "点击开关即可关闭" else "点击开关即可打开",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = torchOn, onCheckedChange = onToggle)
        }
    }
}

/** 亮度调节卡片：设备不支持时给出提示。 */
@Composable
private fun BrightnessCard(
    supported: Boolean,
    brightness: Float,
    maxLevel: Int,
    onLevelChange: (Float) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "亮度调节",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (supported) "${brightness.roundToInt()} / $maxLevel" else "不支持",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (supported) {
                Slider(
                    value = brightness,
                    onValueChange = onLevelChange,
                    valueRange = 1f..maxLevel.toFloat(),
                    steps = (maxLevel - 2).coerceAtLeast(0),
                )
                Text(
                    text = "拖动滑块可在开灯时实时调节闪光灯亮度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "当前设备的闪光灯不支持亮度调节（需要 Android 13 及以上且硬件支持多档亮度）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 未授权时的说明与申请入口。 */
@Composable
private fun PermissionHint(requestedOnce: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "闪光灯属于相机设备，控制手电筒需要「相机」权限。" +
                "该权限仅用于开关闪光灯，不会拍摄照片或视频。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (requestedOnce) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "授权请求被拒绝，请重试或前往系统设置手动开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) {
            Text("授予相机权限")
        }
    }
}

/** 设备没有闪光灯时的提示。 */
@Composable
private fun NoFlashHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "未检测到闪光灯",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "当前设备没有可用的闪光灯硬件，无法使用手电筒功能。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
