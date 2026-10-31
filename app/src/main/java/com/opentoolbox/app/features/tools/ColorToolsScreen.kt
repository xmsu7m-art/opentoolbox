package com.opentoolbox.app.features.tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 常用色板条目：中文名称 + ARGB 颜色值。 */
private data class PaletteColor(val name: String, val color: Int)

/** 常用色板：红橙黄绿青蓝紫粉棕灰黑白等 16 色。 */
private val paletteColors = listOf(
    PaletteColor("红色", 0xFFF44336.toInt()),
    PaletteColor("橙色", 0xFFFF9800.toInt()),
    PaletteColor("琥珀", 0xFFFFC107.toInt()),
    PaletteColor("黄色", 0xFFFFEB3B.toInt()),
    PaletteColor("黄绿", 0xFF8BC34A.toInt()),
    PaletteColor("绿色", 0xFF4CAF50.toInt()),
    PaletteColor("青色", 0xFF00BCD4.toInt()),
    PaletteColor("蓝色", 0xFF2196F3.toInt()),
    PaletteColor("靛蓝", 0xFF3F51B5.toInt()),
    PaletteColor("紫色", 0xFF9C27B0.toInt()),
    PaletteColor("粉色", 0xFFE91E63.toInt()),
    PaletteColor("棕色", 0xFF795548.toInt()),
    PaletteColor("灰色", 0xFF9E9E9E.toInt()),
    PaletteColor("蓝灰", 0xFF607D8B.toInt()),
    PaletteColor("黑色", 0xFF000000.toInt()),
    PaletteColor("白色", 0xFFFFFFFF.toInt()),
)

/** 纯白 / 纯黑（ARGB），用于变亮与变暗的混合。 */
private val pureWhite: Int = 0xFFFFFFFF.toInt()
private val pureBlack: Int = 0xFF000000.toInt()

/** HSL 表示：色相 0-360°，饱和度 0-100%，亮度 0-100%。 */
private data class HslColor(val hue: Int, val saturation: Int, val lightness: Int)

/** 由 Alpha/R/G/B 四个 0-255 分量合成 ARGB 颜色值。 */
private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

/**
 * 解析 HEX 颜色文本：支持 #RRGGBB 与 #AARRGGBB，可省略 #；
 * 先做长度与字符校验，再用 Color.parseColor 兜底，非法返回 null。
 */
private fun parseHexColor(raw: String): Int? {
    val body = raw.trim().removePrefix("#")
    if (body.length != 6 && body.length != 8) return null
    val isHex = body.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (!isHex) return null
    return runCatching { android.graphics.Color.parseColor("#$body") }.getOrNull()
}

/** 将 ARGB 颜色格式化为 HEX 文本：Alpha 为 255 时输出 #RRGGBB，否则输出 #AARRGGBB。 */
private fun formatHex(color: Int): String {
    val alpha = (color ushr 24) and 0xFF
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return if (alpha == 0xFF) {
        String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    } else {
        String.format(Locale.US, "#%02X%02X%02X%02X", alpha, red, green, blue)
    }
}

/**
 * RGB 转 HSL 的标准算法：
 * 以最大/最小通道求亮度，色差求饱和度，色相按最大通道所在区间分段计算。
 */
private fun rgbToHsl(red: Int, green: Int, blue: Int): HslColor {
    val r = red / 255.0
    val g = green / 255.0
    val b = blue / 255.0
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2.0
    val hue: Double
    val saturation: Double
    if (delta == 0.0) {
        // 灰色（含黑白）：无色相与饱和度
        hue = 0.0
        saturation = 0.0
    } else {
        saturation = delta / (1.0 - abs(2.0 * lightness - 1.0))
        hue = when (max) {
            r -> ((g - b) / delta) % 6.0
            g -> (b - r) / delta + 2.0
            else -> (r - g) / delta + 4.0
        } * 60.0
    }
    return HslColor(
        hue = ((hue.roundToInt() % 360) + 360) % 360,
        saturation = (saturation * 100).roundToInt().coerceIn(0, 100),
        lightness = (lightness * 100).roundToInt().coerceIn(0, 100),
    )
}

/** 将 base 与 target 按 ratio（target 占比 0-1）线性混合，保留 base 的 Alpha。 */
private fun mixColor(base: Int, target: Int, ratio: Double): Int {
    fun mixChannel(baseChannel: Int, targetChannel: Int): Int =
        (baseChannel + (targetChannel - baseChannel) * ratio).roundToInt().coerceIn(0, 255)
    return argb(
        alpha = (base ushr 24) and 0xFF,
        red = mixChannel((base shr 16) and 0xFF, (target shr 16) and 0xFF),
        green = mixChannel((base shr 8) and 0xFF, (target shr 8) and 0xFF),
        blue = mixChannel(base and 0xFF, target and 0xFF),
    )
}

/** 反色：RGB 三通道各自取反，Alpha 保持不变。 */
private fun invertColor(color: Int): Int = argb(
    alpha = (color ushr 24) and 0xFF,
    red = 255 - ((color shr 16) and 0xFF),
    green = 255 - ((color shr 8) and 0xFF),
    blue = 255 - (color and 0xFF),
)

/** 调整透明度：percent 为 0-100 的 Alpha 百分比。 */
private fun withAlphaPercent(color: Int, percent: Int): Int = argb(
    alpha = (255.0 * percent / 100.0).roundToInt().coerceIn(0, 255),
    red = (color shr 16) and 0xFF,
    green = (color shr 8) and 0xFF,
    blue = color and 0xFF,
)

/** 透明度棋盘格背景：白底 + 浅灰相间方块，用于直观展示 Alpha 效果。 */
private fun Modifier.checkerboard(cellSize: Dp = 8.dp): Modifier = drawBehind {
    drawRect(Color.White)
    val step = cellSize.toPx()
    if (step > 0f) {
        val columns = (size.width / step).toInt() + 1
        val rows = (size.height / step).toInt() + 1
        for (i in 0 until columns) {
            for (j in 0 until rows) {
                if ((i + j) % 2 == 0) {
                    drawRect(
                        color = Color(0xFFD8D8D8),
                        topLeft = Offset(i * step, j * step),
                        size = Size(step, step),
                    )
                }
            }
        }
    }
}

/**
 * 颜色工具页面：HEX 输入与 RGB 滑杆双向同步、大色块预览、
 * HEX/RGB/HSL 三格式一键复制、变亮变暗/反色/透明度预览与常用色板点选应用。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorToolsScreen(onBack: () -> Unit) {
    var currentColor by remember { mutableStateOf(0xFF2196F3.toInt()) }
    var hexInput by remember { mutableStateOf("#2196F3") }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val alpha = (currentColor ushr 24) and 0xFF
    val red = (currentColor shr 16) and 0xFF
    val green = (currentColor shr 8) and 0xFF
    val blue = currentColor and 0xFF

    val parsedColor = parseHexColor(hexInput)
    val hexText = formatHex(currentColor)
    val rgbText = "rgb($red, $green, $blue)"
    val hsl = rgbToHsl(red, green, blue)
    val hslText = "hsl(${hsl.hue}°, ${hsl.saturation}%, ${hsl.lightness}%)"

    fun applyColor(color: Int) {
        currentColor = color
        hexInput = formatHex(color)
    }

    fun copyToClipboard(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
    }

    val variations = listOf(
        "变亮 25%" to mixColor(currentColor, pureWhite, 0.25),
        "变亮 50%" to mixColor(currentColor, pureWhite, 0.5),
        "变暗 25%" to mixColor(currentColor, pureBlack, 0.25),
        "变暗 50%" to mixColor(currentColor, pureBlack, 0.5),
    )
    val transparencyVariants = listOf(
        "反色" to invertColor(currentColor),
        "透明度 25%" to withAlphaPercent(currentColor, 25),
        "透明度 50%" to withAlphaPercent(currentColor, 50),
        "透明度 75%" to withAlphaPercent(currentColor, 75),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("颜色工具") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 颜色输入与预览
            SectionCard(title = "颜色输入与预览") {
                // 大预览色块：棋盘格底衬让透明度变化直观可见，
                // 叠加白底后的有效色决定叠加文字用深色还是白色。
                val effectiveColor = mixColor(currentColor, pureWhite, 1.0 - alpha / 255.0)
                val previewTextColor = if (Color(effectiveColor).luminance() > 0.5f) {
                    Color.Black
                } else {
                    Color.White
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .checkerboard()
                        .background(Color(currentColor))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = hexText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = previewTextColor,
                        )
                        if (alpha != 0xFF) {
                            Text(
                                text = "Alpha ${(alpha * 100.0 / 255.0).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = previewTextColor,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = raw
                        // 输入合法 HEX 时同步到当前颜色（滑杆随之更新）
                        parseHexColor(raw)?.let { currentColor = it }
                    },
                    label = { Text("HEX 颜色值") },
                    singleLine = true,
                    isError = parsedColor == null,
                    supportingText = {
                        Text(
                            text = when {
                                hexInput.isBlank() -> "请输入 HEX 颜色值"
                                parsedColor == null -> "格式无效：需为 6 位 RRGGBB 或 8 位 AARRGGBB 十六进制"
                                else -> "支持 #RRGGBB 与 #AARRGGBB，可省略 #"
                            },
                            color = if (parsedColor == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(currentColor))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChannelSlider(
                    label = "红色 (R)",
                    value = red,
                    onValueChange = { applyColor(argb(alpha, it, green, blue)) },
                )
                ChannelSlider(
                    label = "绿色 (G)",
                    value = green,
                    onValueChange = { applyColor(argb(alpha, red, it, blue)) },
                )
                ChannelSlider(
                    label = "蓝色 (B)",
                    value = blue,
                    onValueChange = { applyColor(argb(alpha, red, green, it)) },
                )
            }

            // 格式转换
            SectionCard(title = "格式转换") {
                FormatRow(label = "HEX", value = hexText) { copyToClipboard(hexText) }
                FormatRow(label = "RGB", value = rgbText) { copyToClipboard(rgbText) }
                FormatRow(label = "HSL", value = hslText) { copyToClipboard(hslText) }
            }

            // 色彩变化
            SectionCard(title = "色彩变化") {
                Text(
                    text = "点击色块应用为新颜色；透明度百分比对应颜色的 Alpha 值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variations.forEach { (label, color) ->
                        ColorSwatch(
                            color = color,
                            label = label,
                            modifier = Modifier.weight(1f),
                            onClick = { applyColor(color) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    transparencyVariants.forEach { (label, color) ->
                        ColorSwatch(
                            color = color,
                            label = label,
                            modifier = Modifier.weight(1f),
                            onClick = { applyColor(color) },
                        )
                    }
                }
            }

            // 常用色板
            SectionCard(title = "常用色板") {
                Text(
                    text = "点击色块设为当前颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    paletteColors.forEach { item ->
                        ColorSwatch(
                            color = item.color,
                            label = item.name,
                            modifier = Modifier.width(64.dp),
                            onClick = { applyColor(item.color) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 分区卡片：统一标题与内部间距。 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

/** R/G/B 单通道滑杆：0-255，整数步进，右侧显示当前数值。 */
@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            steps = 254,
        )
    }
}

/** 格式转换结果行：格式名 + 值 + 复制按钮。 */
@Composable
private fun FormatRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制 $label")
        }
    }
}

/** 可点击色块：棋盘格底衬上叠加颜色层，下方显示名称，点击应用该颜色。 */
@Composable
private fun ColorSwatch(
    color: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .checkerboard()
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
