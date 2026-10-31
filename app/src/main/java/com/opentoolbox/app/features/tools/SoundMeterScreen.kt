package com.opentoolbox.app.features.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 麦克风振幅满量程值（MediaRecorder.maxAmplitude 的取值上限）。 */
private const val MAX_AMPLITUDE = 32767

/** 仪表盘刻度范围：30dB（最左）到 110dB（最右）。 */
private const val MIN_DB = 30.0
private const val MAX_DB = 110.0

/** 高噪声阈值：达到该值时给出保护听力提示。 */
private const val LOUD_DB_THRESHOLD = 80.0

/** 峰值振幅轮询间隔（毫秒）。 */
private const val POLL_INTERVAL_MS = 200L

/** 仪表盘分色带：安静 / 正常交谈 / 嘈杂 / 很吵。 */
private data class LevelBand(val label: String, val fromDb: Double, val toDb: Double, val color: Color)

/** 分色带定义，与仪表盘、图例、分级文字共用同一套颜色。 */
private val levelBands = listOf(
    LevelBand("安静", 30.0, 40.0, Color(0xFF2E7D32)),
    LevelBand("正常交谈", 40.0, 60.0, Color(0xFF66BB6A)),
    LevelBand("嘈杂", 60.0, 80.0, Color(0xFFF9A825)),
    LevelBand("很吵", 80.0, 110.0, Color(0xFFE53935)),
)

/** 一次测量会话：MediaRecorder 实例 + 输出临时文件（释放时一并删除）。 */
private class RecorderSession(val recorder: MediaRecorder, val outputFile: File)

/**
 * 创建并启动 MediaRecorder：麦克风采集、3GP/AAC 输出到 cacheDir 临时文件
 * （部分设备必须设置输出文件才能成功 start）。成功返回会话，失败返回中文错误提示。
 */
private fun startRecorderSession(context: Context): Pair<RecorderSession?, String?> {
    val tempFile: File
    val recorder: MediaRecorder
    try {
        tempFile = File.createTempFile("sound_meter_", ".3gp", context.cacheDir)
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    } catch (_: Exception) {
        return null to "无法创建录音组件，请稍后重试"
    }
    return try {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(44100)
        recorder.setAudioEncodingBitRate(96000)
        recorder.setAudioChannels(1)
        recorder.setOutputFile(tempFile.absolutePath)
        recorder.prepare()
        recorder.start()
        RecorderSession(recorder, tempFile) to null
    } catch (_: Exception) {
        runCatching { recorder.release() }
        tempFile.delete()
        null to "麦克风启动失败：可能被其他应用占用，请关闭相关应用后重试"
    }
}

/** 停止并释放 MediaRecorder，删除临时输出文件。stop 在未录音时会抛异常，统一忽略。 */
private fun releaseRecorderSession(session: RecorderSession?) {
    if (session == null) return
    runCatching { session.recorder.stop() }
    runCatching { session.recorder.release() }
    runCatching { session.outputFile.delete() }
}

/**
 * 振幅转估算分贝：dB = 20 * log10(amplitude / 32767) + 90（标准参考声压校准近似）。
 * 手机麦克风未经声学校准，结果仅为估算参考。
 */
private fun amplitudeToDb(amplitude: Int): Double {
    if (amplitude <= 0) return 0.0
    val clamped = amplitude.coerceAtMost(MAX_AMPLITUDE)
    return 20.0 * log10(clamped.toDouble() / MAX_AMPLITUDE) + 90.0
}

/** 环境噪声分级：<40 安静 / 40-60 正常交谈 / 60-80 嘈杂 / ≥80 很吵。 */
private fun noiseLevelLabel(db: Double): String = when {
    db < 40.0 -> "安静"
    db < 60.0 -> "正常交谈"
    db < 80.0 -> "嘈杂"
    else -> "很吵"
}

/** 分级对应的场景说明，≥80dB 时叠加保护听力提示。 */
private fun noiseLevelDescription(db: Double): String = when {
    db < 40.0 -> "环境非常安静，适合休息与专注"
    db < 60.0 -> "正常交谈音量，适合日常办公"
    db < 80.0 -> "环境嘈杂，长时间停留容易疲劳"
    else -> "噪声很大，请注意保护听力"
}

/** 按分级返回对应颜色，用于分级文字与仪表盘指针。 */
private fun noiseLevelColor(db: Double): Color = when {
    db < 40.0 -> levelBands[0].color
    db < 60.0 -> levelBands[1].color
    db < 80.0 -> levelBands[2].color
    else -> levelBands[3].color
}

/** 分贝映射为仪表盘角度：30dB 对应 180°（最左），110dB 对应 360°（最右）。 */
private fun dbToAngle(db: Double): Float {
    val fraction = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
    return (180.0 + fraction * 180.0).toFloat()
}

/**
 * 分贝计页面：运行时申请麦克风权限，通过 MediaRecorder 每 200ms 轮询峰值振幅，
 * 换算为估算分贝并用半圆仪表盘展示，支持最大值/平均值统计、重置统计与开始/停止测量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundMeterScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    // 已有权限时进入页面自动开始测量
    var measuring by remember { mutableStateOf(granted) }
    var session by remember { mutableStateOf<RecorderSession?>(null) }

    var currentDb by remember { mutableStateOf(0.0) }
    var maxDb by remember { mutableStateOf(0.0) }
    var sumDb by remember { mutableStateOf(0.0) }
    var sampleCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        requestedOnce = true
        if (isGranted) measuring = true
    }

    fun resetStats() {
        maxDb = 0.0
        sumDb = 0.0
        sampleCount = 0
    }

    // 测量核心：进入/离开页面或切换开始/停止时启动、释放 MediaRecorder
    DisposableEffect(granted, measuring) {
        var active: RecorderSession? = null
        if (granted && measuring) {
            val (created, error) = startRecorderSession(context)
            if (created != null) {
                active = created
                session = created
                errorMessage = null
            } else {
                errorMessage = error
                measuring = false
            }
        }
        onDispose {
            releaseRecorderSession(active)
            if (session === active) session = null
        }
    }

    // 每 200ms 轮询一次峰值振幅，换算分贝并累计统计；录音器失效时结束轮询
    LaunchedEffect(session) {
        val recorder = session?.recorder ?: return@LaunchedEffect
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            val amplitude = runCatching { recorder.maxAmplitude }.getOrNull() ?: break
            if (amplitude > 0) {
                val db = amplitudeToDb(amplitude)
                currentDb = db
                if (db > maxDb) maxDb = db
                sumDb += db
                sampleCount += 1
            }
        }
    }

    val hasReading = currentDb > 0.0
    val displayDb = if (hasReading) {
        String.format(Locale.getDefault(), "%.1f", currentDb)
    } else {
        "—"
    }
    val maxText = if (maxDb > 0.0) String.format(Locale.getDefault(), "%.1f", maxDb) else "—"
    val averageText = if (sampleCount > 0) {
        String.format(Locale.getDefault(), "%.1f", sumDb / sampleCount)
    } else {
        "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分贝计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (!granted) {
            PermissionHint(
                requestedOnce = requestedOnce,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 当前分贝与分级
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "当前噪声",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = displayDb,
                                style = MaterialTheme.typography.displayLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "dB",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (hasReading) noiseLevelLabel(currentDb) else "等待测量",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasReading) {
                                noiseLevelColor(currentDb)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (hasReading) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = noiseLevelDescription(currentDb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (currentDb >= LOUD_DB_THRESHOLD) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "当前环境噪声较大，请注意保护听力，避免长时间处于强噪声环境。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // 半圆仪表盘与图例
                DbGauge(
                    currentDb = currentDb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    levelBands.forEach { band ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color = band.color, shape = CircleShape),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = band.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 最大值 / 平均值统计
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatColumn(
                            title = "最大值",
                            value = maxText,
                            modifier = Modifier.weight(1f),
                        )
                        StatColumn(
                            title = "平均值",
                            value = averageText,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { resetStats() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("重置统计")
                        }
                    }
                }

                // 开始 / 停止测量
                Button(
                    onClick = { measuring = !measuring },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (measuring) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (measuring) "停止测量" else "开始测量")
                }
                Text(
                    text = if (measuring) "测量中，数值每 0.2 秒刷新一次" else "测量已停止，点击上方按钮继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = "手机麦克风未经声学校准，数值为估算参考；测量时请勿遮挡麦克风开孔。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 半圆分贝仪表盘：30-110dB 刻度、安静/正常交谈/嘈杂/很吵分色带、随分贝转动的指针。 */
@Composable
private fun DbGauge(currentDb: Double, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = noiseLevelColor(currentDb)

    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    // 指针角度带 200ms 动画，避免 5Hz 采样导致跳动
    val needleAngle by animateFloatAsState(
        targetValue = dbToAngle(currentDb),
        animationSpec = tween(durationMillis = 200),
    )

    Canvas(modifier = modifier) {
        val strokePx = 20.dp.toPx()
        val padPx = 8.dp.toPx()
        val radius = (size.width - strokePx) / 2f - padPx
        val center = Offset(size.width / 2f, size.height - strokePx / 2f - 2.dp.toPx())
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // 背景轨道（完整半圆）
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Butt),
        )

        // 安静 / 正常交谈 / 嘈杂 / 很吵 分色带
        levelBands.forEach { band ->
            val start = dbToAngle(band.fromDb)
            drawArc(
                color = band.color,
                startAngle = start,
                sweepAngle = dbToAngle(band.toDb) - start,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
            )
        }

        // 每 10dB 一条刻度线，每 20dB 标注数值
        val tickStartR = radius + strokePx / 2f + 2.dp.toPx()
        val tickEndR = tickStartR + 6.dp.toPx()
        val labelR = radius - strokePx / 2f - 16.dp.toPx()
        for (tickDb in MIN_DB.toInt()..MAX_DB.toInt() step 10) {
            val angleRad = Math.toRadians(dbToAngle(tickDb.toDouble()).toDouble())
            val dirX = cos(angleRad).toFloat()
            val dirY = sin(angleRad).toFloat()
            drawLine(
                color = tickColor,
                start = Offset(center.x + dirX * tickStartR, center.y + dirY * tickStartR),
                end = Offset(center.x + dirX * tickEndR, center.y + dirY * tickEndR),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (tickDb % 20 == 10) {
                labelPaint.color = labelColor.toArgb()
                labelPaint.textSize = 11.sp.toPx()
                val labelX = center.x + dirX * labelR
                val labelY = center.y + dirY * labelR + labelPaint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText("$tickDb", labelX, labelY, labelPaint)
            }
        }

        // 指针与轴心
        val needleRad = Math.toRadians(needleAngle.toDouble())
        val needleLen = radius * 0.72f
        drawLine(
            color = needleColor,
            start = center,
            end = Offset(
                center.x + cos(needleRad).toFloat() * needleLen,
                center.y + sin(needleRad).toFloat() * needleLen,
            ),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = needleColor, radius = 6.dp.toPx() / 2f, center = center)
    }
}

/** 统计项：标题 + 等宽字体数值。 */
@Composable
private fun StatColumn(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 未授权时的说明卡片与「授权并开始」入口，拒绝后展示重试提示。 */
@Composable
private fun PermissionHint(
    requestedOnce: Boolean,
    modifier: Modifier = Modifier,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "需要麦克风权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "分贝计通过麦克风采集环境声音来估算噪声等级。" +
                        "麦克风仅用于测量环境噪声，录音不会保存，数据也不会离开本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (requestedOnce) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "授权请求被拒绝，请点击下方按钮重试；" +
                            "若反复失败，请前往系统设置手动开启麦克风权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRequest) {
                    Text("授权并开始")
                }
            }
        }
    }
}
