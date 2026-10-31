package com.opentoolbox.app.features.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** 八个方位的名称，每 45° 一个。 */
private val directionNames = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

/** 根据方位角（0-360°）得到八向方位名。 */
private fun directionName(azimuth: Float): String {
    val index = ((azimuth + 22.5f) / 45f).toInt() % 8
    return directionNames[index]
}

/**
 * 指南针页面：基于旋转矢量传感器计算方位角，
 * Canvas 绘制表盘与随角度旋转的指针。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val rotationSensor = remember {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    var azimuth by remember { mutableStateOf(0f) }

    DisposableEffect(rotationSensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val degrees = Math.toDegrees(orientation[0].toDouble())
                azimuth = ((degrees % 360.0 + 360.0) % 360.0).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        rotationSensor?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            rotationSensor?.let { sensorManager?.unregisterListener(listener) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指南针") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (rotationSensor == null) {
            UnsupportedContent(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                CompassDial(azimuthDegrees = azimuth)
                Spacer(Modifier.height(24.dp))
                Text(
                    text = String.format(Locale.getDefault(), "%.0f°", azimuth),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = directionName(azimuth),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "方位角以正北为 0°、顺时针计算。" +
                        "将手机水平放置并缓慢旋转，可以获得更准确的读数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 表盘：外圈刻度 + 固定方位标签 + 随方位角旋转的指针。 */
@Composable
private fun CompassDial(azimuthDegrees: Float) {
    val dialColor = MaterialTheme.colorScheme.outline
    val majorTickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val minorTickColor = MaterialTheme.colorScheme.outlineVariant
    val needleColor = MaterialTheme.colorScheme.primary
    val tailColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 外圈与内圈
            drawCircle(
                color = dialColor,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = minorTickColor.copy(alpha = 0.5f),
                radius = radius * 0.55f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // 刻度：每 5° 一个小刻度，每 45° 一个大刻度
            for (i in 0 until 72) {
                val major = i % 9 == 0
                val rad = Math.toRadians(i * 5.0 - 90.0)
                val direction = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                drawLine(
                    color = if (major) majorTickColor else minorTickColor,
                    start = center + direction * (radius * (if (major) 0.86f else 0.90f)),
                    end = center + direction * (radius * 0.96f),
                    strokeWidth = if (major) 2.5f.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 指针：随方位角顺时针旋转，0° 时指向上方
            rotate(degrees = azimuthDegrees, pivot = center) {
                val needleLength = radius * 0.64f
                val tailLength = radius * 0.32f
                val halfWidth = radius * 0.06f

                val needlePath = Path().apply {
                    moveTo(center.x, center.y - needleLength)
                    lineTo(center.x - halfWidth, center.y)
                    lineTo(center.x + halfWidth, center.y)
                    close()
                }
                drawPath(path = needlePath, color = needleColor)

                val tailPath = Path().apply {
                    moveTo(center.x, center.y + tailLength)
                    lineTo(center.x - halfWidth, center.y)
                    lineTo(center.x + halfWidth, center.y)
                    close()
                }
                drawPath(path = tailPath, color = tailColor)
            }

            // 中心轴点
            drawCircle(color = needleColor, radius = radius * 0.05f, center = center)
        }

        // 固定的方位标签
        DialLabel(
            text = "北",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            emphasized = true,
        )
        DialLabel(
            text = "东",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
        )
        DialLabel(
            text = "南",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
        DialLabel(
            text = "西",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp),
        )
    }
}

/** 表盘上的单个方位标签。 */
@Composable
private fun DialLabel(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/** 设备没有旋转矢量传感器时的提示。 */
@Composable
private fun UnsupportedContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Explore,
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
            text = "未检测到旋转矢量传感器，无法提供指南针功能。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
