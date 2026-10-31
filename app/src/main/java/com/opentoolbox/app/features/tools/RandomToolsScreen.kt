package com.opentoolbox.app.features.tools

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 随机工具分区。 */
private enum class RandomSection(val label: String) {
    NUMBER("随机数"),
    DICE("掷骰子"),
    COIN("抛硬币"),
    PASSWORD("密码生成"),
}

/** 常用骰子面数。 */
private val diceSideOptions = listOf(4, 6, 8, 10, 12, 20, 100)

/** 「不重复抽取」个数的上限，避免结果过长影响性能与阅读。 */
private const val MAX_UNIQUE_COUNT = 10_000

/** 密码可用字符池。 */
private const val UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz"
private const val DIGIT_CHARS = "0123456789"
private const val SYMBOL_CHARS = "!@#$%^&*()-_=+[]{};:,.<>?"

/** 密码生成使用的安全随机源。 */
private val secureRandom = SecureRandom()

/**
 * 随机工具页面：随机数、掷骰子、抛硬币、密码生成四个分区。
 * 前三项统一使用 kotlin.random.Random.Default 作为随机源，
 * 密码生成使用 java.security.SecureRandom 保证安全性。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomToolsScreen(onBack: () -> Unit) {
    var section by remember { mutableStateOf(RandomSection.NUMBER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随机工具") },
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
            // 分区筛选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RandomSection.values().forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (section) {
                RandomSection.NUMBER -> RandomNumberSection()
                RandomSection.DICE -> DiceSection()
                RandomSection.COIN -> CoinSection()
                RandomSection.PASSWORD -> PasswordSection()
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 随机数分区：范围内取单个随机数，或不重复抽取 N 个。 */
@Composable
private fun RandomNumberSection() {
    var minText by remember { mutableStateOf("1") }
    var maxText by remember { mutableStateOf("100") }
    var countText by remember { mutableStateOf("5") }
    var uniqueMode by remember { mutableStateOf(false) }
    var singleResult by remember { mutableStateOf<Long?>(null) }
    var uniqueResult by remember { mutableStateOf<List<Long>?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 范围输入
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = minText,
                onValueChange = { raw ->
                    minText = raw.filter { it.isDigit() || it == '-' }
                },
                label = { Text("最小值") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { raw ->
                    maxText = raw.filter { it.isDigit() || it == '-' }
                },
                label = { Text("最大值") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        // 不重复抽取模式开关
        FilterChip(
            selected = uniqueMode,
            onClick = {
                uniqueMode = !uniqueMode
                errorText = null
                singleResult = null
                uniqueResult = null
            },
            label = { Text("不重复抽取 N 个") },
        )

        if (uniqueMode) {
            OutlinedTextField(
                value = countText,
                onValueChange = { raw ->
                    countText = raw.filter { it.isDigit() }
                },
                label = { Text("抽取个数 N") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                val range = parseRangeInputs(minText, maxText)
                if (range == null) {
                    errorText = "请输入有效整数，且最小值不能大于最大值"
                } else if (uniqueMode) {
                    val count = countText.trim().toIntOrNull()
                    val rangeSize = (range.second.toULong() - range.first.toULong()) + 1UL
                    errorText = when {
                        count == null || count < 1 -> "抽取个数需为不小于 1 的整数"
                        count > MAX_UNIQUE_COUNT -> "抽取个数最多 $MAX_UNIQUE_COUNT 个"
                        count.toULong() > rangeSize ->
                            "区间内只有 $rangeSize 个不同的数，无法不重复抽取 $count 个"
                        else -> {
                            uniqueResult =
                                drawUniqueInRange(Random.Default, range.first, range.second, count)
                            singleResult = null
                            null
                        }
                    }
                } else {
                    singleResult = randomInRange(Random.Default, range.first, range.second)
                    uniqueResult = null
                    errorText = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("生成")
        }

        val rangeError = errorText
        if (rangeError != null) {
            Text(
                text = rangeError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
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
                    text = "随机结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val single = singleResult
                val multi = uniqueResult
                when {
                    single != null -> Text(
                        text = single.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )

                    multi != null -> Text(
                        text = multi.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )

                    else -> Text(
                        text = "设置范围后点击「生成」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 掷骰子分区：选择面数与数量后投掷，附带快速滚动动画。 */
@Composable
private fun DiceSection() {
    var sides by remember { mutableStateOf(6) }
    var diceCount by remember { mutableStateOf(1) }
    var rolls by remember { mutableStateOf<List<Int>?>(null) }
    var rollingPreview by remember { mutableStateOf<List<Int>?>(null) }
    var rollTrigger by remember { mutableStateOf(0) }

    // 投掷动画：先快速刷新若干轮预览值，再定格最终结果
    LaunchedEffect(rollTrigger) {
        if (rollTrigger == 0) return@LaunchedEffect
        repeat(10) {
            rollingPreview = rollDice(Random.Default, sides, diceCount)
            delay(60)
        }
        rolls = rollDice(Random.Default, sides, diceCount)
        rollingPreview = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 面数选择
        diceSideOptions.chunked(4).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = sides == option,
                        onClick = { sides = option },
                        label = { Text("D$option") },
                    )
                }
            }
        }

        // 数量步进器（1-10）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "骰子数量",
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = { if (diceCount > 1) diceCount -= 1 },
                enabled = diceCount > 1,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "减少一颗骰子")
            }
            Text(
                text = "$diceCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            IconButton(
                onClick = { if (diceCount < 10) diceCount += 1 },
                enabled = diceCount < 10,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "增加一颗骰子")
            }
        }

        Button(
            onClick = { rollTrigger += 1 },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("投掷")
        }

        // 结果卡片
        val shown = rollingPreview ?: rolls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "投掷结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (shown == null) {
                    Text(
                        text = "点击「投掷」开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    shown.forEachIndexed { index, value ->
                        Text(
                            text = "第 ${index + 1} 颗：$value",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "总和：${shown.sum()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 抛硬币分区：大按钮抛硬币，统计本轮正反面次数。 */
@Composable
private fun CoinSection() {
    var result by remember { mutableStateOf<String?>(null) }
    var headsCount by remember { mutableStateOf(0) }
    var tailsCount by remember { mutableStateOf(0) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "硬币结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val shown = result
                if (shown == null) {
                    Text(
                        text = "点击「抛硬币」开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Button(
            onClick = {
                if (Random.Default.nextBoolean()) {
                    result = "正面"
                    headsCount += 1
                } else {
                    result = "反面"
                    tailsCount += 1
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = "抛硬币",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // 本轮统计
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "本轮：正面 ${headsCount} 次，反面 ${tailsCount} 次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    headsCount = 0
                    tailsCount = 0
                },
                enabled = headsCount + tailsCount > 0,
            ) {
                Text("清零")
            }
        }
    }
}

/** 密码生成分区：长度与字符类型可配，生成后可一键复制。 */
@Composable
private fun PasswordSection() {
    var length by remember { mutableStateOf(16f) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "密码长度：${length.toInt()} 位",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..32f,
            steps = 23,
        )

        CheckboxRow("含大写字母 (A-Z)", useUpper) { useUpper = it }
        CheckboxRow("含小写字母 (a-z)", useLower) { useLower = it }
        CheckboxRow("含数字 (0-9)", useDigits) { useDigits = it }
        CheckboxRow("含符号 (!@#$…)", useSymbols) { useSymbols = it }

        Button(
            onClick = {
                if (!(useUpper || useLower || useDigits || useSymbols)) {
                    errorText = "请至少选择一种字符类型"
                } else {
                    errorText = null
                    password = generatePassword(
                        length = length.toInt(),
                        useUpper = useUpper,
                        useLower = useLower,
                        useDigits = useDigits,
                        useSymbols = useSymbols,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("生成密码")
        }

        val passwordError = errorText
        if (passwordError != null) {
            Text(
                text = passwordError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val shown = password
        if (shown != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(shown))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制密码")
                    }
                }
            }
        }
    }
}

/** 带点击热区的复选框行。 */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 解析最小/最大输入；非整数或最小值大于最大值时返回 null。 */
private fun parseRangeInputs(minText: String, maxText: String): Pair<Long, Long>? {
    val min = minText.trim().toLongOrNull() ?: return null
    val max = maxText.trim().toLongOrNull() ?: return null
    return if (min <= max) min to max else null
}

/** 在闭区间 [min, max] 内取随机数，正确处理 Long 边界。 */
private fun randomInRange(random: Random, min: Long, max: Long): Long = when {
    min == max -> min
    max < Long.MAX_VALUE -> random.nextLong(min, max + 1)
    else -> random.nextLong(min, max)
}

/**
 * 从闭区间 [min, max] 内不重复抽取 count 个数。
 * 区间不大时整段装入数组做部分洗牌后取前 count 个；
 * 区间很大时 count 相对极小，用集合去重的拒绝抽样即可。
 */
private fun drawUniqueInRange(random: Random, min: Long, max: Long, count: Int): List<Long> {
    val rangeSize = (max.toULong() - min.toULong()) + 1UL
    if (rangeSize <= 200_000UL) {
        val size = rangeSize.toInt()
        val pool = LongArray(size)
        var value = min
        for (i in 0 until size) {
            pool[i] = value
            value += 1
        }
        for (i in 0 until count) {
            val j = random.nextInt(i, size)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }
        return pool.take(count)
    }
    val drawn = HashSet<Long>(count * 2)
    while (drawn.size < count) {
        drawn.add(randomInRange(random, min, max))
    }
    return drawn.toList()
}

/** 一次投掷 count 颗 sides 面骰子，返回每颗的点数。 */
private fun rollDice(random: Random, sides: Int, count: Int): List<Int> =
    List(count) { random.nextInt(1, sides + 1) }

/**
 * 生成随机密码：每类已选字符先各保证出现一次，
 * 其余位从合并字符池随机填充，最后整体洗牌避免规律性。
 */
private fun generatePassword(
    length: Int,
    useUpper: Boolean,
    useLower: Boolean,
    useDigits: Boolean,
    useSymbols: Boolean,
): String {
    val pools = buildList {
        if (useUpper) add(UPPER_CHARS)
        if (useLower) add(LOWER_CHARS)
        if (useDigits) add(DIGIT_CHARS)
        if (useSymbols) add(SYMBOL_CHARS)
    }
    val all = pools.joinToString("")
    val chars = CharArray(length)
    pools.forEachIndexed { index, pool ->
        chars[index] = pool[secureRandom.nextInt(pool.length)]
    }
    for (i in pools.size until length) {
        chars[i] = all[secureRandom.nextInt(all.length)]
    }
    for (i in chars.indices.reversed()) {
        val j = secureRandom.nextInt(i + 1)
        val tmp = chars[i]
        chars[i] = chars[j]
        chars[j] = tmp
    }
    return String(chars)
}
