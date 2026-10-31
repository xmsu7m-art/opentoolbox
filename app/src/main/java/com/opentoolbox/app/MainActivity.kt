package com.opentoolbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opentoolbox.app.ui.ToolboxApp
import com.opentoolbox.app.ui.theme.ThemeController
import com.opentoolbox.app.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeController.init(this)
        setContent {
            ToolboxTheme(themeMode = ThemeController.mode) {
                ToolboxApp()
            }
        }
    }
}
