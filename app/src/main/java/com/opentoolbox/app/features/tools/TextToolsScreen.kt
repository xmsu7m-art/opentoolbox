package com.opentoolbox.app.features.tools

import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URLDecoder
import java.net.URLEncoder

/** 文本工具页的三个功能区。 */
private enum class TextToolCategory(val label: String) {
    STATS("统计"),
    ENCODING("编码转换"),
    PROCESS("文本处理"),
}

/** 单条统计项：名称 + 数值。 */
private data class TextStat(val label: String, val value: Int)

/** 一次转换的结果状态：成功时 result 有值，失败时 error 有值。 */
private data class ToolResult(val result: String = "", val error: String? = null)

/** 英文单词：连续的英文字母。 */
private val englishWordRegex = Regex("[A-Za-z]+")

/** 段落分隔：空行（行间只有空白字符也算）。 */
private val paragraphSeparatorRegex = Regex("\\n\\s*\\n")

/** 多余空白：连续出现的空白字符。 */
private val whitespaceRegex = Regex("\\s+")

/** 文本处理操作表：按钮文案 + 处理函数。 */
private val textOperations: List<Pair<String, (String) -> String>> = listOf(
    "转大写" to { text -> text.uppercase() },
    "转小写" to { text -> text.lowercase() },
    "去除多余空白" to { text -> text.replace(whitespaceRegex, " ").trim() },
    "去除所有空格" to { text ->
        text.filterNot { char -> char == ' ' || char == '\t' || char == '　' }
    },
    "反转文本" to { text -> StringBuilder(text).reverse().toString() },
    "Trim 每行" to { text -> text.lines().joinToString("\n") { line -> line.trim() } },
)

/** 判断是否为中文字符（CJK 统一表意文字、扩展 A 区与兼容表意文字）。 */
private fun isChineseChar(char: Char): Boolean =
    char in '\u3400'..'\u4DBF' || char in '\u4E00'..'\u9FFF' || char in '\uF900'..'\uFAFF'

/** 统计核心逻辑：字符、单词、行、中文、段落各类计数。 */
private fun computeStats(text: String): List<TextStat> {
    val wordCount = text.split(whitespaceRegex).count { it.isNotEmpty() }
    val lineCount = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
    val paragraphCount = if (text.isBlank()) {
        0
    } else {
        text.split(paragraphSeparatorRegex).count { it.isNotBlank() }
    }
    return listOf(
        TextStat("字符数（不含空白）", text.count { !it.isWhitespace() }),
        TextStat("含空格字符数", text.length),
        TextStat("单词数（按空白切分）", wordCount),
        TextStat("行数", lineCount),
        TextStat("中文字符数", text.count(::isChineseChar)),
        TextStat("英文单词数", englishWordRegex.findAll(text).count()),
        TextStat("段落数", paragraphCount),
    )
}

/** Base64 编码：UTF-8 字节，NO_WRAP 输出不换行。 */
private fun encodeBase64(text: String): String =
    Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

/** Base64 解码：输入非法时抛出带中文提示的异常。 */
private fun decodeBase64(text: String): String =
    try {
        Base64.decode(text.trim(), Base64.NO_WRAP).toString(Charsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("不是有效的 Base64 字符串", error)
    }

/** URL 编码：UTF-8。 */
private fun encodeUrl(text: String): String = URLEncoder.encode(text, "UTF-8")

/** URL 解码：UTF-8，输入非法时抛出带中文提示的异常。 */
private fun decodeUrl(text: String): String =
    try {
        URLDecoder.decode(text.trim(), "UTF-8")
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("不是有效的 URL 编码字符串", error)
    }

/** HEX 编码：按 UTF-8 字节转为两位大写十六进制。 */
private fun encodeHex(text: String): String =
    text.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }

/** HEX 解码：按 UTF-8 字节还原文本，忽略空白，输入非法时抛出带中文提示的异常。 */
private fun decodeHex(text: String): String {
    val cleaned = text.filterNot { it.isWhitespace() }
    require(cleaned.length % 2 == 0) { "HEX 长度必须为偶数" }
    val bytes = ByteArray(cleaned.length / 2)
    for (index in bytes.indices) {
        val high = Character.digit(cleaned[index * 2], 16)
        val low = Character.digit(cleaned[index * 2 + 1], 16)
        require(high >= 0 && low >= 0) { "包含非法的 HEX 字符" }
        bytes[index] = ((high shl 4) or low).toByte()
    }
    return bytes.toString(Charsets.UTF_8)
}

/** 执行一次转换：输入为空给出提示；用 runCatching 包住，成功记录结果，失败记录中文错误。 */
private fun runTool(input: String, transform: (String) -> String): ToolResult {
    if (input.isEmpty()) return ToolResult(error = "请先在上方输入文本")
    return runCatching { transform(input) }.fold(
        onSuccess = { ToolResult(result = it) },
        onFailure = { ToolResult(error = "转换失败：${it.message ?: "未知错误"}") },
    )
}

/** 复制文本到剪贴板并用 Toast 提示；内容为空时提示无法复制。 */
private fun copyWithToast(
    context: Context,
    clipboard: ClipboardManager,
    label: String,
    text: String,
) {
    if (text.isEmpty()) {
        Toast.makeText(context, "${label}结果为空，暂无可复制的内容", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setText(AnnotatedString(text))
    Toast.makeText(context, "已复制${label}结果", Toast.LENGTH_SHORT).show()
}

/**
 * 文本工具页面：统计 / 编码转换 / 文本处理三大功能区，
 * 共享顶部大输入框；统计随输入实时刷新，所有转换用 runCatching 包住，
 * 失败时显示中文错误提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsScreen(onBack: () -> Unit) {
    var category by remember { mutableStateOf(TextToolCategory.STATS) }
    var input by remember { mutableStateOf("") }
    var base64Result by remember { mutableStateOf(ToolResult()) }
    var urlResult by remember { mutableStateOf(ToolResult()) }
    var hexResult by remember { mutableStateOf(ToolResult()) }
    var processResult by remember { mutableStateOf(ToolResult()) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文本工具") },
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
            // 共享大输入框
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("输入文本") },
                placeholder = { Text("在此输入或粘贴需要处理的文本") },
                minLines = 4,
                maxLines = 10,
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { input = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空输入")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 功能区切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextToolCategory.values().forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (category) {
                TextToolCategory.STATS -> StatsSection(input = input)

                TextToolCategory.ENCODING -> {
                    CodecSection(
                        title = "Base64",
                        state = base64Result,
                        onEncode = { base64Result = runTool(input) { encodeBase64(it) } },
                        onDecode = { base64Result = runTool(input) { decodeBase64(it) } },
                        onCopy = { copyWithToast(context, clipboard, "Base64", base64Result.result) },
                    )
                    CodecSection(
                        title = "URL 编码",
                        state = urlResult,
                        onEncode = { urlResult = runTool(input) { encodeUrl(it) } },
                        onDecode = { urlResult = runTool(input) { decodeUrl(it) } },
                        onCopy = { copyWithToast(context, clipboard, "URL", urlResult.result) },
                    )
                    CodecSection(
                        title = "HEX（UTF-8）",
                        state = hexResult,
                        onEncode = { hexResult = runTool(input) { encodeHex(it) } },
                        onDecode = { hexResult = runTool(input) { decodeHex(it) } },
                        onCopy = { copyWithToast(context, clipboard, "HEX", hexResult.result) },
                    )
                }

                TextToolCategory.PROCESS -> ProcessSection(
                    state = processResult,
                    onRun = { action -> processResult = runTool(input, action) },
                    onCopy = { copyWithToast(context, clipboard, "处理", processResult.result) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 统计区块：随输入实时刷新的统计卡片网格。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSection(input: String) {
    val stats = remember(input) { computeStats(input) }
    if (input.isEmpty()) {
        Text(
            text = "在上方输入文本后，这里会实时显示统计结果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { stat ->
            StatCard(stat = stat, modifier = Modifier.weight(1f))
        }
    }
}

/** 单张统计卡片：名称在上，数值在下。 */
@Composable
private fun StatCard(stat: TextStat, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stat.value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 单个编码工具区块：标题 + 编码/解码按钮 + 只读结果 + 复制 + 错误提示。 */
@Composable
private fun CodecSection(
    title: String,
    state: ToolResult,
    onEncode: () -> Unit,
    onDecode: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onEncode, modifier = Modifier.weight(1f)) {
                    Text("编码")
                }
                OutlinedButton(onClick = onDecode, modifier = Modifier.weight(1f)) {
                    Text("解码")
                }
            }
            ResultField(value = state.result, onCopy = onCopy)
            val error = state.error
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 文本处理区块：操作按钮组 + 结果区 + 错误提示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessSection(
    state: ToolResult,
    onRun: ((String) -> String) -> Unit,
    onCopy: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        textOperations.forEach { (name, action) ->
            FilledTonalButton(onClick = { onRun(action) }) {
                Text(name)
            }
        }
    }
    ResultField(value = state.result, onCopy = onCopy)
    val error = state.error
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 只读结果框，右侧为复制按钮。 */
@Composable
private fun ResultField(value: String, onCopy: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text("结果") },
        minLines = 2,
        maxLines = 6,
        trailingIcon = {
            IconButton(onClick = onCopy, enabled = value.isNotEmpty()) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制结果")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
