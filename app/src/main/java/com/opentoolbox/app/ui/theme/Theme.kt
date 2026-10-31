package com.opentoolbox.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.opentoolbox.app.data.Prefs

private val Teal600 = Color(0xFF00897B)
private val Teal300 = Color(0xFF4DB6AC)

private val LightColors = lightColorScheme(
    primary = Teal600,
    secondary = Teal300,
)

private val DarkColors = darkColorScheme(
    primary = Teal300,
    secondary = Teal600,
)

/**
 * 主题模式的全局可观察状态：设置页写入，MainActivity 读取，持久化到 Prefs。
 */
object ThemeController {
    var mode by mutableStateOf(Prefs.THEME_MODE_SYSTEM)
        private set

    fun init(context: Context) {
        mode = Prefs.string(context, Prefs.KEY_THEME_MODE, Prefs.THEME_MODE_SYSTEM)
    }

    fun set(context: Context, value: String) {
        mode = value
        Prefs.putString(context, Prefs.KEY_THEME_MODE, value)
    }
}

@Composable
fun ToolboxTheme(
    themeMode: String = Prefs.THEME_MODE_SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        Prefs.THEME_MODE_DARK -> true
        Prefs.THEME_MODE_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
