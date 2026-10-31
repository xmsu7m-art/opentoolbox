package com.opentoolbox.app.features.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 一个测试颜色：名称 + 纯色值。 */
private data class TestColor(val name: String, val color: Color)

/** 测试用纯色列表：红绿蓝白黑灰黄青品红。 */
private val testColors = listOf(
    TestColor("红", Color(0xFFFF0000)),
    TestColor("绿", Color(0xFF00FF00)),
    TestColor("蓝", Color(0xFF0000FF)),
    TestColor("白", Color(0xFFFFFFFF)),
    TestColor("黑", Color(0xFF000000)),
    TestColor("灰", Color(0xFF808080)),
    TestColor("黄", Color(0xFFFFFF00)),
    TestColor("青", Color(0xFF00FFFF)),
    TestColor("品红", Color(0xFFFF00FF)),
)

/**
 * 屏幕检测页面：全屏纯色逐一切换，点按任意区域切换下一种颜色，
 * 用于检查坏点、亮点与亮度均匀性。顶部悬浮半透明「退出」按钮。
 */
@Composable
fun ScreenTestScreen(onBack: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val current = testColors[index]

    // 全屏纯色展示，不放置常规顶栏，避免干扰大区域点按
    Scaffold { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(current.color)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    index = (index + 1) % testColors.size
                },
        ) {
            // 顶部悬浮「退出」按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "退出",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // 底部半透明提示条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "第 ${index + 1} / ${testColors.size} 色 · ${current.name}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "点按屏幕切换颜色，检查坏点与亮度均匀性",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
