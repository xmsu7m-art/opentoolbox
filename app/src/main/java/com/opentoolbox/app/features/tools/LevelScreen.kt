package com.opentoolbox.app.features.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 低通滤波系数：越小越平滑，响应越慢。 */
private const val LOW_PASS_ALPHA = 0.15f

/** 判定“水平”的容差角度。 */
private const val LEVEL_TOLERANCE_DEG = 1f

/** 双轴表盘量程（半边最大显示角度），超出后气泡贴边。 */
private const val DIAL_RANGE_DEG = 10f

/** 单轴水平尺量程。 */
private const val GAUGE_RANGE_DEG = 45f

/** 水平状态色：合格（绿）、轻度偏斜（黄）、严重偏斜（红）。 */
private val LevelOkColor = Color(0xFF4CAF50)
private val LevelWarnColor = Color(0xFFFFC107)
private val LevelDangerColor = Color(0xFFF44336)

/** 对加速度计原始读数做低通滤波，平滑重力分量、抑制抖动。 */
private fun lowPassFilter(
    incoming: FloatArray,
    previous: FloatArray?,
    alpha: Float = LOW_PASS_ALPHA,
): FloatArray {
    if (previous == null || previous.size != incoming.size) return incoming.copyOf()
    return FloatArray(incoming.size) { i -> previous[i] + alpha * (incoming[i] - previous[i]) }
}

/** 俯仰角：绕 X 轴的倾角，正值表示机身顶端偏高。 */
private fun gravityPitchDegrees(g: FloatArray): Float =
    Math.toDegrees(atan2(g[1], g[2]).toDouble()).toFloat()

/** 横滚角：绕 Y 轴的倾角，正值表示机身右端偏高。 */
private fun gravityRollDegrees(g: FloatArray): Float =
    Math.toDegrees(atan2(g[0], g[2]).toDouble()).toFloat()

/** 沿机身长边（Y 轴）与水平面的夹角，平放或竖持贴边摆放均适用。 */
private fun gravitySlopeDegrees(g: FloatArray): Float =
    Math.toDegrees(atan2(g[1], sqrt(g[0] * g[0] + g[2] * g[2])).toDouble()).toFloat()

/** 根据最大偏转角度返回状态色：±1° 内为绿色，随后按角度由黄到红渐变。 */
private fun levelStateColor(deflectionDeg: Float): Color {
    if (deflectionDeg <= LEVEL_TOLERANCE_DEG) return LevelOkColor
    val t = ((deflectionDeg - LEVEL_TOLERANCE_DEG) / (GAUGE_RANGE_DEG - LEVEL_TOLERANCE_DEG))
        .coerceIn(0f, 1f)
    return lerp(LevelWarnColor, LevelDangerColor, t)
}

/** 拼接方向提示文字，例如「左端偏高 3.2°」。 */
private fun formatTiltHint(direction: String, degrees: Float): String =
    direction + String.format(Locale.getDefault(), " %.1f°", degrees)

/** 双轴模式下按最大偏转方向给出文字提示。 */
private fun dualAxisHint(pitchDeg: Float, rollDeg: Float): String {
    val p = abs(pitchDeg)
    val r = abs(rollDeg)
    return when {
        maxOf(p, r) <= LEVEL_TOLERANCE_DEG -> "水平 ✓"
        r >= p && rollDeg > 0f -> formatTiltHint("右端偏高", r)
        r >= p -> formatTiltHint("左端偏高", r)
        pitchDeg > 0f -> formatTiltHint("顶端偏高", p)
        else -> formatTiltHint("底端偏高", p)
    }
}

/** 单轴模式下给出文字提示。 */
private fun singleAxisHint(slopeDeg: Float): String = when {
    abs(slopeDeg) <= LEVEL_TOLERANCE_DEG -> "水平 ✓"
    slopeDeg > 0f -> formatTiltHint("顶端偏高", abs(slopeDeg))
    else -> formatTiltHint("底端偏高", abs(slopeDeg))
}

/**
 * 水平仪（气泡水平尺）页面：基于加速度传感器计算重力分量，
 * 支持手机平放的双轴水平仪与竖持贴边的单轴水平尺两种模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val accelerometer = remember {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    var gravity by remember { mutableStateOf(FloatArray(3)) }

    DisposableEffect(accelerometer) {
        val listener = object : SensorEventListener {
            private var filtered: FloatArray? = null

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val smoothed = lowPassFilter(event.values, filtered)
                filtered = smoothed
                gravity = smoothed
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accelerometer?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            accelerometer?.let { sensorManager?.unregisterListener(listener) }
        }
    }

    var singleAxisMode by remember { mutableStateOf(false) }

    // 归零校准：记住当前姿态作为 0 点，置 null 表示取消校准。
    var zeroPitch by remember { mutableStateOf<Float?>(null) }
    var zeroRoll by remember { mutableStateOf<Float?>(null) }
    var zeroSlope by remember { mutableStateOf<Float?>(null) }
    val isCalibrated = zeroPitch != null

    val rawPitch = gravityPitchDegrees(gravity)
    val rawRoll = gravityRollDegrees(gravity)
    val rawSlope = gravitySlopeDegrees(gravity)
    val pitch = rawPitch - (zeroPitch ?: 0f)
    val roll = rawRoll - (zeroRoll ?: 0f)
    val slope = rawSlope - (zeroSlope ?: 0f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("水平仪") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (accelerometer == null) {
            LevelUnsupportedContent(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !singleAxisMode,
                        onClick = { singleAxisMode = false },
                        label = { Text("双轴水平仪") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = singleAxisMode,
                        onClick = { singleAxisMode = true },
                        label = { Text("单轴水平尺") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))

                if (singleAxisMode) {
                    SingleAxisGauge(slopeDeg = slope)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%+.1f°", slope),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = levelStateColor(abs(slope)),
                    )
                } else {
                    DualLevelDial(pitchDeg = pitch, rollDeg = roll)
                }

                Spacer(Modifier.height(20.dp))
                LevelReadingCard(
                    isSingleAxis = singleAxisMode,
                    pitchDeg = pitch,
                    rollDeg = roll,
                    slopeDeg = slope,
                    isCalibrated = isCalibrated,
                    onToggleCalibration = {
                        if (isCalibrated) {
                            zeroPitch = null
                            zeroRoll = null
                            zeroSlope = null
                        } else {
                            zeroPitch = rawPitch
                            zeroRoll = rawRoll
                            zeroSlope = rawSlope
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (singleAxisMode) {
                        "将手机竖持，让机身长边紧贴被测边缘，可测量 ±45° 内的倾角；读数为正表示顶端偏高。"
                    } else {
                        "将手机屏幕朝上平放在被测表面上，表盘量程为 ±10°；" +
                            "气泡居中变绿即表示水平，绿色目标圈对应 ±1° 容差。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 双轴模式读数卡片：俯仰/横滚角度、偏转方向提示与归零校准按钮。 */
@Composable
private fun LevelReadingCard(
    isSingleAxis: Boolean,
    pitchDeg: Float,
    rollDeg: Float,
    slopeDeg: Float,
    isCalibrated: Boolean,
    onToggleCalibration: () -> Unit,
) {
    val statusText = if (isSingleAxis) singleAxisHint(slopeDeg) else dualAxisHint(pitchDeg, rollDeg)
    val statusColor = levelStateColor(
        if (isSingleAxis) abs(slopeDeg) else maxOf(abs(pitchDeg), abs(rollDeg)),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isSingleAxis) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AngleMetric(label = "俯仰（前后）", degrees = pitchDeg)
                    AngleMetric(label = "横滚（左右）", degrees = rollDeg)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isCalibrated) "已将当前姿态设为零点" else "把当前姿态记为水平零点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onToggleCalibration) {
                    Text(if (isCalibrated) "取消校准" else "归零校准")
                }
            }
        }
    }
}

/** 读数卡片中的单个角度指标。 */
@Composable
private fun AngleMetric(label: String, degrees: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = String.format(Locale.getDefault(), "%+.1f°", degrees),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = levelStateColor(abs(degrees)),
        )
    }
}

/** 双轴水平仪表盘：方形刻度盘 + 十字准线 + 目标圈 + 随倾角移动的气泡。 */
@Composable
private fun DualLevelDial(pitchDeg: Float, rollDeg: Float) {
    val frameColor = MaterialTheme.colorScheme.outline
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val minorTickColor = MaterialTheme.colorScheme.outlineVariant
    val bubbleColor = levelStateColor(maxOf(abs(pitchDeg), abs(rollDeg)))
    val bubbleRadius = 9.dp

    Box(modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val halfOuter = size.minDimension / 2f - 2.dp.toPx()
            // 气泡中心的最大行程（边框内侧留出刻度区）
            val travel = halfOuter - 22.dp.toPx()

            // 方形外框
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(center.x - halfOuter, center.y - halfOuter),
                size = Size(halfOuter * 2f, halfOuter * 2f),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )

            // 四边刻度：每 1° 一格，每 5° 长刻度
            for (deg in -DIAL_RANGE_DEG.toInt()..DIAL_RANGE_DEG.toInt()) {
                val offsetAlong = (deg / DIAL_RANGE_DEG) * travel
                val major = deg % 5 == 0
                val color = if (major) tickColor else minorTickColor
                val tickLen = if (major) 10.dp.toPx() else 5.dp.toPx()
                val strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                // 上边与下边
                drawLine(
                    color = color,
                    start = Offset(center.x + offsetAlong, center.y - halfOuter),
                    end = Offset(center.x + offsetAlong, center.y - halfOuter + tickLen),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x + offsetAlong, center.y + halfOuter),
                    end = Offset(center.x + offsetAlong, center.y + halfOuter - tickLen),
                    strokeWidth = strokeWidth,
                )
                // 左边与右边
                drawLine(
                    color = color,
                    start = Offset(center.x - halfOuter, center.y + offsetAlong),
                    end = Offset(center.x - halfOuter + tickLen, center.y + offsetAlong),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x + halfOuter, center.y + offsetAlong),
                    end = Offset(center.x + halfOuter - tickLen, center.y + offsetAlong),
                    strokeWidth = strokeWidth,
                )
            }

            // 十字准线
            drawLine(
                color = minorTickColor,
                start = Offset(center.x - halfOuter, center.y),
                end = Offset(center.x + halfOuter, center.y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = minorTickColor,
                start = Offset(center.x, center.y - halfOuter),
                end = Offset(center.x, center.y + halfOuter),
                strokeWidth = 1.dp.toPx(),
            )

            // 5° 参考圈（淡）与 ±1° 目标圈
            drawCircle(
                color = minorTickColor.copy(alpha = 0.45f),
                radius = travel * 0.5f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = frameColor,
                radius = travel * (LEVEL_TOLERANCE_DEG / DIAL_RANGE_DEG),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = tickColor.copy(alpha = 0.5f),
                radius = 2.dp.toPx(),
                center = center,
            )

            // 气泡：朝抬高的一端移动，超出量程后贴边
            val bubbleX = center.x + (rollDeg / DIAL_RANGE_DEG * travel).coerceIn(-travel, travel)
            val bubbleY = center.y - (pitchDeg / DIAL_RANGE_DEG * travel).coerceIn(-travel, travel)
            val bubbleCenter = Offset(bubbleX, bubbleY)
            drawCircle(
                color = bubbleColor.copy(alpha = 0.22f),
                radius = bubbleRadius.toPx() * 2f,
                center = bubbleCenter,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = bubbleColor,
                radius = bubbleRadius.toPx(),
                center = bubbleCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = bubbleRadius.toPx() * 0.32f,
                center = bubbleCenter - Offset(
                    bubbleRadius.toPx() * 0.3f,
                    bubbleRadius.toPx() * 0.35f,
                ),
            )
        }
    }
}

/** 单轴水平尺：±45° 弧形刻度盘 + 指针，0° 指向正上方，正值（顶端偏高）向右偏。 */
@Composable
private fun SingleAxisGauge(slopeDeg: Float) {
    val arcColor = MaterialTheme.colorScheme.outline
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val minorTickColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val needleColor = levelStateColor(abs(slopeDeg))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pivot = Offset(size.width / 2f, size.height - 18.dp.toPx())
            val sin45 = sin(Math.toRadians(45.0)).toFloat()
            val radius = minOf(pivot.y - 10.dp.toPx(), (size.width / 2f - 12.dp.toPx()) / sin45)
            val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // ±1° 容差区（绿色高亮）
            drawArc(
                color = LevelOkColor,
                startAngle = -90f - LEVEL_TOLERANCE_DEG,
                sweepAngle = LEVEL_TOLERANCE_DEG * 2f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )

            // ±45° 量程弧线
            drawArc(
                color = arcColor,
                startAngle = -90f - GAUGE_RANGE_DEG,
                sweepAngle = GAUGE_RANGE_DEG * 2f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.dp.toPx()),
            )

            // 刻度：每 5° 一格，每 15° 长刻度
            for (angle in -GAUGE_RANGE_DEG.toInt()..GAUGE_RANGE_DEG.toInt() step 5) {
                val rad = Math.toRadians(angle.toDouble())
                val direction = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
                val major = angle % 15 == 0
                drawLine(
                    color = if (major) tickColor else minorTickColor,
                    start = pivot + direction * radius,
                    end = pivot + direction * (radius - (if (major) 12.dp.toPx() else 7.dp.toPx())),
                    strokeWidth = if (major) 2.5f.dp.toPx() else 1.5f.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 指针：随倾角摆动，超出量程后停在边界
            val needleAngle = slopeDeg.coerceIn(-GAUGE_RANGE_DEG, GAUGE_RANGE_DEG)
            rotate(degrees = needleAngle, pivot = pivot) {
                val needleLength = radius * 0.84f
                val halfWidth = radius * 0.032f
                val needlePath = Path().apply {
                    moveTo(pivot.x, pivot.y - needleLength)
                    lineTo(pivot.x - halfWidth, pivot.y)
                    lineTo(pivot.x + halfWidth, pivot.y)
                    close()
                }
                drawPath(path = needlePath, color = needleColor)
            }

            // 轴点
            drawCircle(color = needleColor, radius = 6.dp.toPx(), center = pivot)
            drawCircle(color = surfaceColor, radius = 2.5.dp.toPx(), center = pivot)
        }
    }
}

/** 设备没有加速度传感器时的提示。 */
@Composable
private fun LevelUnsupportedContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.WaterDrop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "设备不支持",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "未检测到加速度传感器，无法提供水平仪功能。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
