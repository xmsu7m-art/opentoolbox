package com.opentoolbox.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opentoolbox.app.features.adb.AdbScreen
import com.opentoolbox.app.features.apps.AppDetailScreen
import com.opentoolbox.app.features.apps.AppsScreen
import com.opentoolbox.app.features.battery.BatteryScreen
import com.opentoolbox.app.features.device.DeviceInfoScreen
import com.opentoolbox.app.features.network.NetworkScreen
import com.opentoolbox.app.features.settings.SettingsScreen
import com.opentoolbox.app.features.tools.CompassScreen
import com.opentoolbox.app.features.tools.FlashlightScreen
import com.opentoolbox.app.features.tools.KeepAwakeScreen
import com.opentoolbox.app.features.tools.ScreenTestScreen
import com.opentoolbox.app.features.tools.ColorToolsScreen
import com.opentoolbox.app.features.tools.DateToolsScreen
import com.opentoolbox.app.features.tools.LevelScreen
import com.opentoolbox.app.features.tools.RandomToolsScreen
import com.opentoolbox.app.features.tools.SoundMeterScreen
import com.opentoolbox.app.features.tools.StopwatchScreen
import com.opentoolbox.app.features.tools.TextToolsScreen
import com.opentoolbox.app.features.tools.ToolsHomeScreen
import com.opentoolbox.app.features.tools.UnitConverterScreen
import com.opentoolbox.app.features.tools.VibrationScreen
import com.opentoolbox.app.ui.navigation.TabRoutes

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    TabItem(TabRoutes.Tools, "工具", Icons.Outlined.Build),
    TabItem(TabRoutes.Apps, "应用", Icons.Outlined.Apps),
    TabItem(TabRoutes.Adb, "ADB", Icons.Outlined.Terminal),
    TabItem(TabRoutes.Settings, "设置", Icons.Outlined.Settings),
)

@Composable
fun ToolboxApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TabRoutes.Tools,
            modifier = Modifier.padding(padding),
        ) {
            composable(TabRoutes.Tools) {
                ToolsHomeScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable(TabRoutes.Apps) {
                AppsScreen(onOpenApp = { pkg -> navController.navigate("${TabRoutes.AppDetail}/$pkg") })
            }
            composable(TabRoutes.Adb) { AdbScreen() }
            composable(TabRoutes.Settings) { SettingsScreen() }

            composable("${TabRoutes.AppDetail}/{packageName}") { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                AppDetailScreen(
                    packageName = packageName,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TabRoutes.Battery) { BatteryScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.DeviceInfo) { DeviceInfoScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Network) { NetworkScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Flashlight) { FlashlightScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Compass) { CompassScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.ScreenTest) { ScreenTestScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.UnitConverter) { UnitConverterScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Vibration) { VibrationScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.KeepAwake) { KeepAwakeScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.TextTools) { TextToolsScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.RandomTools) { RandomToolsScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Stopwatch) { StopwatchScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.ColorTools) { ColorToolsScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.SoundMeter) { SoundMeterScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.DateTools) { DateToolsScreen(onBack = { navController.popBackStack() }) }
            composable(TabRoutes.Level) { LevelScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
