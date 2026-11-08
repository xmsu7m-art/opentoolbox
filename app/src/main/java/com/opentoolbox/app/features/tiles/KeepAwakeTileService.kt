package com.opentoolbox.app.features.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * 屏幕常亮快捷设置磁贴。
 *
 * 通过系统设置 [Settings.System.STAY_ON_WHILE_PLUGGED_IN] 控制「充电时屏幕保持常亮」：
 * - 开启：AC + USB + 无线充电三种供电方式全部生效（值为 7），插电期间屏幕不会休眠；
 * - 关闭：恢复系统默认的自动休眠（值为 0）。
 *
 * 首次使用需要「修改系统设置」权限：磁贴点击后会跳转到系统授权页并定位到本应用，
 * 授权完成后再次下拉快捷设置面板即自动同步最新状态。
 */
class KeepAwakeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        if (!Settings.System.canWrite(this)) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = SUBTITLE_NEED_PERMISSION)
            return
        }
        syncTileFromSettings()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.System.canWrite(this)) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = SUBTITLE_NEED_PERMISSION)
            openWriteSettingsPage()
            return
        }
        try {
            val current = readStayOnValue()
            val newValue = if (current == 0) STAY_ON_WHILE_PLUGGED_IN_ALL else 0
            Settings.System.putInt(
                contentResolver,
                Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                newValue,
            )
            updateTile(
                state = if (newValue != 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                subtitle = if (newValue != 0) SUBTITLE_ACTIVE else SUBTITLE_INACTIVE,
            )
        } catch (_: SecurityException) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = SUBTITLE_NEED_PERMISSION)
            showToast("没有修改系统设置的权限，请先授权")
        } catch (_: Settings.SettingNotFoundException) {
            updateTile(state = Tile.STATE_INACTIVE, subtitle = SUBTITLE_INACTIVE)
            showToast("读取屏幕常亮设置失败")
        } catch (_: Exception) {
            updateTile(state = Tile.STATE_INACTIVE, subtitle = SUBTITLE_INACTIVE)
            showToast("设置失败，请稍后重试")
        }
    }

    /** 按当前设置值同步磁贴显示：非 0 视为开启，0 视为关闭。 */
    private fun syncTileFromSettings() {
        val value = readStayOnValue()
        updateTile(
            state = if (value != 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            subtitle = if (value != 0) SUBTITLE_ACTIVE else SUBTITLE_INACTIVE,
        )
    }

    /** 读取当前「充电时常亮」的设置值，从未设置过或读取失败时视为 0（关闭）。 */
    private fun readStayOnValue(): Int =
        try {
            Settings.System.getInt(contentResolver, Settings.System.STAY_ON_WHILE_PLUGGED_IN)
        } catch (_: Settings.SettingNotFoundException) {
            0
        }

    /** 打开系统的「修改系统设置」授权页，并通过 data 指定本应用的包名。 */
    private fun openWriteSettingsPage() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14 起仅保留 PendingIntent 重载
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_WRITE_SETTINGS,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // Android 14 以下使用 Intent 重载（该重载自 Android 14 起已废弃）
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            showToast("请允许「修改系统设置」权限后返回")
        } catch (_: Exception) {
            showToast("无法打开设置页面，请手动前往系统设置授权")
        }
    }

    /** 统一更新磁贴：状态、标题、图标与副标题（副标题需要 Android 10 及以上）。 */
    private fun updateTile(state: Int, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = TILE_LABEL
        tile.icon = Icon.createWithResource(TILE_ICON_PACKAGE, TILE_ICON_RES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    /** 在磁贴场景弹出提示，使用应用级 Context 避免持有服务引用。 */
    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 跳转「修改系统设置」授权页的请求码。 */
        const val REQUEST_CODE_WRITE_SETTINGS = 2001

        /** 磁贴显示标题。 */
        const val TILE_LABEL = "屏幕常亮"

        /** 平台图标资源所在的包名（框架资源固定为 "android"）。 */
        const val TILE_ICON_PACKAGE = "android"

        /** 磁贴图标：太阳，对应屏幕持续点亮。 */
        val TILE_ICON_RES = android.R.drawable.ic_menu_day

        /** 开启时的磁贴副标题。 */
        const val SUBTITLE_ACTIVE = "充电时常亮"

        /** 关闭时的磁贴副标题。 */
        const val SUBTITLE_INACTIVE = "插电时屏幕保持常亮"

        /** 未授权时的磁贴副标题。 */
        const val SUBTITLE_NEED_PERMISSION = "需要「修改系统设置」权限"

        /** AC + USB + 无线充电，覆盖所有供电方式，组合值为 7。 */
        val STAY_ON_WHILE_PLUGGED_IN_ALL =
            BatteryManager.BATTERY_PLUGGED_AC or
                BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
    }
}
