package com.opentoolbox.app.features.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** 页面分区。 */
private enum class DateSection(val label: String) {
    INTERVAL("日期间隔"),
    OFFSET("日期推算"),
    TODAY("今天速查"),
}

/** 日期推算单位：展示名 + 对应的 java.time 时间单位。 */
private enum class OffsetUnit(val label: String, val chrono: ChronoUnit) {
    DAY("天", ChronoUnit.DAYS),
    WEEK("周", ChronoUnit.WEEKS),
    MONTH("月", ChronoUnit.MONTHS),
    YEAR("年", ChronoUnit.YEARS),
}

/** 短格式：yyyy-MM-dd。 */
private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 中文长格式：yyyy年M月d日。 */
private val cnFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** 中文周几名称，下标 = DayOfWeek.value - 1。 */
private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 内置春节日期（2026、2027 两年），取其中最近的未来一个。 */
private val springFestivalDates = listOf(
    LocalDate.of(2026, 2, 17),
    LocalDate.of(2027, 2, 6),
)

/** 日期间隔计算结果。 */
private data class IntervalResult(
    val from: LocalDate,
    val to: LocalDate,
    val swapped: Boolean,
    val totalDays: Long,
    val weeks: Long,
    val weekRemainder: Long,
    val period: Period,
)

/** 中文周几。 */
private fun weekdayCn(date: LocalDate): String = weekdayNames[date.dayOfWeek.value - 1]

/** LocalDate → DatePicker 使用的 UTC 毫秒（选择器按 UTC 存储毫秒，避免时区偏移差一天）。 */
private fun dateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** DatePicker 的 UTC 毫秒 → LocalDate。 */
private fun pickerMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** 日期推算：结果越界或数值溢出时返回 null，由界面给出提示。 */
private fun safePlus(base: LocalDate, amount: Long, unit: ChronoUnit): LocalDate? =
    try {
        base.plus(amount, unit)
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

/** 月/年推算是否发生月末对齐（如 1 月 31 日 +1 个月 → 2 月 28 日）。 */
private fun isMonthEndAdjusted(base: LocalDate, result: LocalDate, unit: ChronoUnit): Boolean {
    if (unit != ChronoUnit.MONTHS && unit != ChronoUnit.YEARS) return false
    return base.dayOfMonth != result.dayOfMonth && result.dayOfMonth == result.lengthOfMonth()
}

/** 计算两个日期的间隔：内部自动按先后顺序排列，swapped 标记用户输入顺序是否被调换。 */
private fun computeInterval(start: LocalDate, end: LocalDate): IntervalResult {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val totalDays = ChronoUnit.DAYS.between(from, to)
    return IntervalResult(
        from = from,
        to = to,
        swapped = start > end,
        totalDays = totalDays,
        weeks = totalDays / 7,
        weekRemainder = totalDays % 7,
        period = Period.between(from, to),
    )
}

/** 距最近的未来春节（仅内置 2026/2027），全部已过则返回 null。 */
private fun nextSpringFestival(today: LocalDate): LocalDate? =
    springFestivalDates.filter { it >= today }.minOrNull()

/**
 * 日期工具页面：日期间隔 / 日期推算 / 今天速查三个分区，
 * 全部基于 java.time（minSdk 26 可直接使用），时区取系统默认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateToolsScreen(onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    var section by remember { mutableStateOf(DateSection.INTERVAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日期工具") },
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
            // 分区切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DateSection.values().forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (section) {
                DateSection.INTERVAL -> IntervalSection(today)
                DateSection.OFFSET -> OffsetSection(today)
                DateSection.TODAY -> TodaySection(today)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 分区一：日期间隔。选择两个日期后点击「计算」展示差值明细。 */
@Composable
private fun IntervalSection(today: LocalDate) {
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var result by remember { mutableStateOf<IntervalResult?>(null) }

    DatePickerField(
        label = "开始日期",
        date = startDate,
        modifier = Modifier.fillMaxWidth(),
        onDateSelected = {
            startDate = it
            result = null
        },
    )
    DatePickerField(
        label = "结束日期",
        date = endDate,
        modifier = Modifier.fillMaxWidth(),
        onDateSelected = {
            endDate = it
            result = null
        },
    )
    Button(
        onClick = { result = computeInterval(startDate, endDate) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("计算")
    }

    val current = result
    if (current == null) {
        Text(
            text = "选择开始与结束日期后，点击「计算」查看间隔结果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${current.from.format(cnFormatter)} → ${current.to.format(cnFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.swapped) {
                    Text(
                        text = "结束日期早于开始日期，已自动按先后顺序计算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoRow("相差天数", "${current.totalDays} 天")
                InfoRow(
                    "相差周数",
                    if (current.weekRemainder == 0L) {
                        "${current.weeks} 周"
                    } else {
                        "${current.weeks} 周 ${current.weekRemainder} 天"
                    },
                )
                InfoRow(
                    "年月日拆分",
                    "${current.period.years} 年 ${current.period.months} 个月 ${current.period.days} 天",
                )
                InfoRow(
                    "较早日期星期",
                    "${current.from.format(shortFormatter)} ${weekdayCn(current.from)}",
                )
                InfoRow(
                    "较晚日期星期",
                    "${current.to.format(shortFormatter)} ${weekdayCn(current.to)}",
                )
            }
        }
    }
}

/** 分区二：日期推算。起始日期加减指定天/周/月/年，实时给出结果。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffsetSection(today: LocalDate) {
    var baseDate by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf("30") }
    var unit by remember { mutableStateOf(OffsetUnit.DAY) }

    DatePickerField(
        label = "起始日期",
        date = baseDate,
        modifier = Modifier.fillMaxWidth(),
        onDateSelected = { baseDate = it },
    )
    OutlinedTextField(
        value = amountText,
        onValueChange = { raw ->
            val negative = raw.startsWith("-")
            val digits = raw.filter { it.isDigit() }.take(9)
            amountText = when {
                digits.isEmpty() -> if (negative) "-" else ""
                negative -> "-$digits"
                else -> digits
            }
        },
        label = { Text("偏移量（可正可负）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OffsetUnit.values().forEach { item ->
            FilterChip(
                selected = unit == item,
                onClick = { unit = item },
                label = { Text(item.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "推算结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val amount = amountText.toLongOrNull()
            if (amount == null) {
                Text(
                    text = "请输入偏移量数字，负数表示往前推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val resultDate = safePlus(baseDate, amount, unit.chrono)
                if (resultDate == null) {
                    Text(
                        text = "偏移量过大，结果超出可表示的日期范围。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val adjusted = isMonthEndAdjusted(baseDate, resultDate, unit.chrono)
                    val baseLabel = if (baseDate == today) "今天" else baseDate.format(shortFormatter)
                    val signed = if (amount >= 0) "+$amount" else amount.toString()
                    Text(
                        text = "$baseLabel $signed ${unit.label} = " +
                            "${resultDate.format(shortFormatter)} ${weekdayCn(resultDate)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = resultDate.format(cnFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (adjusted) {
                        Text(
                            text = "原日期的当日在目标月不存在，已按月末对齐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 分区三：今天速查。展示当天各项日历信息与彩蛋卡。 */
@Composable
private fun TodaySection(today: LocalDate) {
    val weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear())
    val daysToYearEnd = ChronoUnit.DAYS.between(today, LocalDate.of(today.year, 12, 31))
    val festival = nextSpringFestival(today)
    val festivalText = festival?.let { date ->
        val days = ChronoUnit.DAYS.between(today, date)
        if (days == 0L) {
            "就是今天（${date.format(shortFormatter)}）"
        } else {
            "还有 $days 天（${date.format(shortFormatter)}）"
        }
    } ?: "—（内置春节仅覆盖 2026 与 2027 年）"
    val hundredDaysLater = today.plusDays(100)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今天速查",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow(
                "当前日期",
                "${today.format(cnFormatter)}（${today.format(shortFormatter)}）",
            )
            InfoRow("星期", weekdayCn(today))
            InfoRow("今年第几天", "第 ${today.dayOfYear} 天")
            InfoRow("ISO 周数", "第 $weekNumber 周")
            InfoRow(
                "是否闰年",
                if (today.isLeapYear()) {
                    "是，今年共 ${today.lengthOfYear()} 天"
                } else {
                    "否，今年共 ${today.lengthOfYear()} 天"
                },
            )
            InfoRow("距今年底", "$daysToYearEnd 天")
            InfoRow("距最近春节", festivalText)
        }
    }

    // 彩蛋：距今天 100 天后的日期
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "彩蛋：距今天 100 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${hundredDaysLater.format(cnFormatter)} " +
                        "${weekdayCn(hundredDaysLater)}（${hundredDaysLater.format(shortFormatter)}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * 日期选择字段：只读输入框覆盖透明点击层，点击弹出 Material3 DatePickerDialog。
 * 确认时把选择器返回的 UTC 毫秒转换回 LocalDate 后回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onDateSelected: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = date.format(shortFormatter),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // 覆盖整个字段的透明点击层，让点击任意位置都能弹出选择器
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateToPickerMillis(date),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDateSelected(pickerMillisToDate(it))
                        }
                        showPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 键值信息行：标签靠左，值靠右并对齐末尾。 */
@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
