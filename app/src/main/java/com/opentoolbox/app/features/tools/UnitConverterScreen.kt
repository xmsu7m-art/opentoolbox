package com.opentoolbox.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/** 换算类别。 */
private enum class ConverterCategory(val label: String) {
    LENGTH("长度"),
    WEIGHT("重量"),
    TEMPERATURE("温度"),
    DATA("数据"),
    SPEED("速度"),
}

/** 单位定义：名称 + 相对基准单位的倍率（温度除外，温度用专用函数换算）。 */
private data class UnitDef(val name: String, val factor: Double)

/** 各类别单位表，倍率以列表中的基准单位为 1。 */
private val unitTables: Map<ConverterCategory, List<UnitDef>> = mapOf(
    ConverterCategory.LENGTH to listOf(
        UnitDef("米 (m)", 1.0),
        UnitDef("千米 (km)", 1000.0),
        UnitDef("厘米 (cm)", 0.01),
        UnitDef("毫米 (mm)", 0.001),
        UnitDef("英寸 (in)", 0.0254),
        UnitDef("英尺 (ft)", 0.3048),
        UnitDef("英里 (mi)", 1609.344),
    ),
    ConverterCategory.WEIGHT to listOf(
        UnitDef("千克 (kg)", 1.0),
        UnitDef("克 (g)", 0.001),
        UnitDef("吨 (t)", 1000.0),
        UnitDef("毫克 (mg)", 0.000001),
        UnitDef("磅 (lb)", 0.45359237),
        UnitDef("盎司 (oz)", 0.028349523125),
    ),
    ConverterCategory.TEMPERATURE to listOf(
        UnitDef("摄氏度 (°C)", 1.0),
        UnitDef("华氏度 (°F)", 1.0),
        UnitDef("开尔文 (K)", 1.0),
    ),
    ConverterCategory.DATA to listOf(
        UnitDef("兆字节 (MB)", 1048576.0),
        UnitDef("吉字节 (GB)", 1073741824.0),
        UnitDef("千字节 (KB)", 1024.0),
        UnitDef("字节 (B)", 1.0),
        UnitDef("太字节 (TB)", 1099511627776.0),
    ),
    ConverterCategory.SPEED to listOf(
        UnitDef("千米/小时 (km/h)", 1.0 / 3.6),
        UnitDef("米/秒 (m/s)", 1.0),
        UnitDef("英里/小时 (mph)", 0.44704),
        UnitDef("节 (kn)", 0.5144444444444445),
        UnitDef("英尺/秒 (ft/s)", 0.3048),
    ),
)

/**
 * 单位换算核心逻辑：非温度类别按倍率换算；
 * 温度先折算为摄氏度，再换算到目标单位。
 */
private fun convertValue(
    value: Double,
    category: ConverterCategory,
    fromIndex: Int,
    toIndex: Int,
): Double {
    if (category == ConverterCategory.TEMPERATURE) {
        val celsius = when (fromIndex) {
            0 -> value
            1 -> (value - 32.0) / 1.8
            else -> value - 273.15
        }
        return when (toIndex) {
            0 -> celsius
            1 -> celsius * 1.8 + 32.0
            else -> celsius + 273.15
        }
    }
    val units = unitTables.getValue(category)
    return value / units[fromIndex].factor * units[toIndex].factor
}

/** 结果格式化：常规数值最多保留 6 位小数并去掉多余的 0，极端数量级用科学计数法。 */
private fun formatResult(value: Double): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    val magnitude = abs(value)
    if (magnitude >= 1e9 || magnitude < 1e-6) {
        return String.format(Locale.getDefault(), "%.4e", value)
    }
    val text = String.format(Locale.getDefault(), "%.6f", value)
        .trimEnd('0')
        .trimEnd('.')
    return if (text == "-0") "0" else text
}

/**
 * 单位换算页面：长度/重量/温度/数据/速度五类，
 * 输入与单位变化即时重算，非法输入显示「—」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(onBack: () -> Unit) {
    var category by remember { mutableStateOf(ConverterCategory.LENGTH) }
    var input by remember { mutableStateOf("1") }
    var fromIndex by remember { mutableStateOf(0) }
    var toIndex by remember { mutableStateOf(1) }

    val units = unitTables.getValue(category)
    val fromUnit = units[fromIndex.coerceIn(0, units.lastIndex)]
    val toUnit = units[toIndex.coerceIn(0, units.lastIndex)]

    val parsed = input.trim().toDoubleOrNull()
    val result = if (parsed != null && parsed.isFinite()) {
        formatResult(convertValue(parsed, category, fromIndex, toIndex))
    } else {
        "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单位换算") },
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
            // 分类筛选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ConverterCategory.values().forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = {
                            if (category != item) {
                                category = item
                                fromIndex = 0
                                toIndex = 1
                            }
                        },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 数值输入
            OutlinedTextField(
                value = input,
                onValueChange = { raw ->
                    input = raw.filter { it.isDigit() || it == '.' || it == '-' }
                },
                label = { Text("数值") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 源单位 / 目标单位
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnitDropdown(
                    label = "源单位",
                    units = units,
                    selectedIndex = fromIndex,
                    modifier = Modifier.weight(1f),
                    onSelect = { fromIndex = it },
                )
                IconButton(
                    onClick = {
                        val oldFrom = fromIndex
                        fromIndex = toIndex
                        toIndex = oldFrom
                    },
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "交换单位")
                }
                UnitDropdown(
                    label = "目标单位",
                    units = units,
                    selectedIndex = toIndex,
                    modifier = Modifier.weight(1f),
                    onSelect = { toIndex = it },
                )
            }

            // 结果卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "换算结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = parsed?.let { value ->
                            "${formatResult(value)} ${fromUnit.name} = $result ${toUnit.name}"
                        } ?: "请输入有效数字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (category == ConverterCategory.TEMPERATURE) {
                Text(
                    text = "提示：温度换算使用摄氏度/华氏度/开尔文的专用公式，" +
                        "其他类别按国际单位倍率换算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 单位下拉选择框（只读输入框 + 菜单）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    label: String,
    units: List<UnitDef>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = units[selectedIndex.coerceIn(0, units.lastIndex)].name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            units.forEachIndexed { index, unit ->
                DropdownMenuItem(
                    text = { Text(unit.name) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
