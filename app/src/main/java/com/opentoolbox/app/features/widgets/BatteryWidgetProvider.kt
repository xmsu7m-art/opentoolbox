package com.opentoolbox.app.features.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.util.Log
import android.widget.RemoteViews
import com.opentoolbox.app.MainActivity
import com.opentoolbox.app.R

/**
 * 一次 ACTION_BATTERY_CHANGED 粘性广播解析出的小部件展示数据。
 *
 * @property percent 电量百分比；EXTRA_LEVEL / EXTRA_SCALE 缺失时为 null（界面显示 --）
 * @property isCharging 是否处于充电相关状态（充电中或已充满）
 * @property statusText 状态行文案
 */
private data class BatteryDisplay(
    val percent: Int?,
    val isCharging: Boolean,
    val statusText: String,
)

/**
 * 桌面电池小部件：经典 AppWidget + RemoteViews 实现。
 *
 * 数据来源与刷新时机：
 * - [onUpdate]：系统按 updatePeriodMillis 周期回调；
 * - [updateAllWidgets]：通过 registerReceiver(null, ...) 读取系统粘性广播
 *   ACTION_BATTERY_CHANGED，无需常驻接收器即可拿到最新电池状态；
 * - [onEnabled]：首个实例放置到桌面时动态注册 ACTION_BATTERY_CHANGED 接收器
 *   （接收器由 companion object 持有，避免重复注册），进程存活期间电量变化
 *   实时刷新全部实例；[onDisabled] 最后一个实例被移除时注销；
 * - 点按：PendingIntent 发送自定义广播，[onReceive] 中打开 MainActivity 并
 *   立即刷新一次。
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "BatteryWidgetProvider"

        /** 点按小部件时发给自身的自定义广播 action。 */
        private const val ACTION_WIDGET_CLICK =
            "com.opentoolbox.app.features.widgets.action.WIDGET_CLICK"

        /** 低电量阈值：低于该值且未充电时百分比显示为红色。 */
        private const val LOW_BATTERY_THRESHOLD = 15

        /** 常规电量百分比颜色（白）。 */
        private val COLOR_NORMAL = Color.parseColor("#FFFFFF")

        /** 状态行常规颜色（灰），与布局默认一致。 */
        private val COLOR_STATUS_DEFAULT = Color.parseColor("#B0B0B0")

        /** 充电中前景色（绿）。 */
        private val COLOR_CHARGING = Color.parseColor("#4CAF50")

        /** 低电量前景色（红）。 */
        private val COLOR_LOW_BATTERY = Color.parseColor("#F44336")

        /** 动态注册的电池广播接收器；由 companion object 持有，避免重复注册。 */
        private var batteryReceiver: BroadcastReceiver? = null

        /**
         * 读取最新电池状态并刷新当前应用的全部电池小部件实例，任何失败都不抛出。
         */
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            try {
                val manager = AppWidgetManager.getInstance(appContext) ?: return
                val widgetIds = manager.getAppWidgetIds(
                    ComponentName(appContext, BatteryWidgetProvider::class.java),
                )
                if (widgetIds == null || widgetIds.isEmpty()) return

                val battery = readBatterySnapshot(appContext)
                val percent = battery.percent

                val views = RemoteViews(appContext.packageName, R.layout.battery_widget)

                // 百分比大字 + 水平进度条（max=100，progress=电量）
                if (percent != null) {
                    views.setTextViewText(R.id.widget_percent, "$percent%")
                    views.setProgressBar(R.id.widget_bar, 100, percent, false)
                } else {
                    views.setTextViewText(R.id.widget_percent, "--")
                    views.setProgressBar(R.id.widget_bar, 100, 0, false)
                }
                views.setTextViewText(R.id.widget_status, battery.statusText)

                // 颜色：充电中（含已充满）绿色；低电且未充电红色；其余保持默认色
                val isLowBattery = percent != null &&
                    percent < LOW_BATTERY_THRESHOLD &&
                    !battery.isCharging
                val percentColor = when {
                    battery.isCharging -> COLOR_CHARGING
                    isLowBattery -> COLOR_LOW_BATTERY
                    else -> COLOR_NORMAL
                }
                val statusColor = when {
                    battery.isCharging -> COLOR_CHARGING
                    isLowBattery -> COLOR_LOW_BATTERY
                    else -> COLOR_STATUS_DEFAULT
                }
                views.setTextColor(R.id.widget_percent, percentColor)
                views.setTextColor(R.id.widget_status, statusColor)

                // 点按：打开 MainActivity 并立即刷新一次
                views.setOnClickPendingIntent(R.id.widget_root, buildClickPendingIntent(appContext))

                manager.updateAppWidget(widgetIds, views)
            } catch (e: Exception) {
                Log.e(TAG, "更新电池小部件失败", e)
            }
        }

        /**
         * 点按小部件：向自身发送自定义广播，由 [onReceive] 打开 MainActivity 并刷新。
         */
        private fun buildClickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, BatteryWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * 打开 MainActivity：Intent 显式指向应用自身包名，包名唯一，startActivity 可靠。
         * 携带 FLAG_ACTIVITY_NEW_TASK，应用任务已存在时直接回到最近状态，不重复建栈。
         */
        private fun openMainActivity(context: Context) {
            try {
                val launch = Intent(context, MainActivity::class.java).apply {
                    setPackage(context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launch)
            } catch (e: Exception) {
                Log.e(TAG, "打开 MainActivity 失败", e)
            }
        }

        /**
         * 通过 registerReceiver(null, ...) 读取 ACTION_BATTERY_CHANGED 粘性广播获取
         * 最新电池状态；读取失败时返回缺省数据（百分比显示 --）。
         */
        private fun readBatterySnapshot(context: Context): BatteryDisplay {
            return try {
                val sticky = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                )
                parseBatteryDisplay(sticky)
            } catch (e: Exception) {
                Log.e(TAG, "读取电池状态失败", e)
                parseBatteryDisplay(null)
            }
        }

        /**
         * 解析电池广播：
         * - EXTRA_LEVEL / EXTRA_SCALE：电量百分比，缺失时为 null（显示 --）；
         * - EXTRA_STATUS：2=充电中、5=充满、3=放电、4=未充电；
         * - EXTRA_PLUGGED：1=AC、2=USB、4=无线。
         */
        private fun parseBatteryDisplay(intent: Intent?): BatteryDisplay {
            if (intent == null) {
                return BatteryDisplay(percent = null, isCharging = false, statusText = "未充电")
            }
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) {
                (level * 100 / scale).coerceIn(0, 100)
            } else {
                null
            }
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN,
            )
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> chargingText(plugged)
                BatteryManager.BATTERY_STATUS_FULL -> "充满"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "使用中"
                else -> "未充电"
            }
            return BatteryDisplay(percent = percent, isCharging = isCharging, statusText = statusText)
        }

        /** 充电中状态文案：按电源类型区分 AC / USB / 无线。 */
        private fun chargingText(plugged: Int): String = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "充电中·AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "充电中·USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "充电中·无线"
            else -> "充电中"
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 系统周期更新：重读电池状态并刷新全部实例；同时兜底补注册动态接收器
        //（进程被系统回收后由周期更新拉起时，onEnabled 不会再触发，此处保证
        // 本进程存活期间电量变化仍能实时刷新），内部幂等，重复调用安全。
        registerBatteryReceiver(context)
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            // 点按小部件：打开主界面并立即刷新一次
            openMainActivity(context)
            updateAllWidgets(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        registerBatteryReceiver(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        unregisterBatteryReceiver(context)
    }

    /**
     * 动态注册 ACTION_BATTERY_CHANGED 接收器：接收器由 companion object 持有，
     * 已注册时直接跳过，避免重复注册。注册成功后系统立即回传当前电池状态，
     * 进程存活期间电量变化都会实时刷新小部件。
     */
    private fun registerBatteryReceiver(context: Context) {
        if (batteryReceiver != null) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                        updateAllWidgets(receiverContext)
                    }
                }
            }
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            batteryReceiver = receiver
        } catch (e: Exception) {
            Log.e(TAG, "注册电池广播接收器失败", e)
        }
    }

    /** 注销动态注册的电池广播接收器，最后一个实例移除时调用。 */
    private fun unregisterBatteryReceiver(context: Context) {
        val receiver = batteryReceiver ?: return
        try {
            context.applicationContext.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销电池广播接收器失败", e)
        } finally {
            batteryReceiver = null
        }
    }
}
