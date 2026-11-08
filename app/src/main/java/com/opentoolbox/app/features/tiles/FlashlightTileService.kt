package com.opentoolbox.app.features.tiles

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.opentoolbox.app.MainActivity

/**
 * 手电筒快捷设置磁贴。
 *
 * - 在下拉快捷设置面板中直接开关闪光灯，无需进入应用；
 * - 监听开始时通过 TorchCallback 把系统当前的闪光灯状态同步到磁贴（开启/关闭）；
 * - 未授予相机权限时磁贴置为不可用，并跳转主界面引导用户在应用内授权；
 * - 闪光灯操作失败（相机服务异常、权限被拒、设备不可用）时磁贴置为不可用并给出提示。
 */
class FlashlightTileService : TileService() {

    /** 相机服务，用于查找闪光灯设备与设置手电筒模式。 */
    private var cameraManager: CameraManager? = null

    /** 带闪光灯的摄像头 ID，null 表示设备不支持闪光灯或查询失败。 */
    private var flashCameraId: String? = null

    /** 当前闪光灯开关状态，由状态回调与点击操作共同维护。 */
    private var torchOn = false

    /** 状态回调运行在主线程，与 TileService 生命周期方法保持线程一致。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 闪光灯状态回调：注册后系统会立即上报一次当前状态，用于完成磁贴同步。 */
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != flashCameraId) return
            torchOn = enabled
            updateTile(
                state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                subtitle = if (enabled) "已开启" else "已关闭",
            )
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        // 防御性注销，避免异常情况下重复注册导致回调叠加
        cameraManager?.unregisterTorchCallback(torchCallback)
        val manager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraManager = manager
        flashCameraId = findFlashCameraId(manager)
        torchOn = false
        when {
            flashCameraId == null ->
                updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "未检测到闪光灯")

            !hasCameraPermission() ->
                updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "点击授权相机权限")

            else -> {
                try {
                    manager?.registerTorchCallback(torchCallback, mainHandler)
                    // 注册成功后系统会立即回调一次当前开关状态，磁贴随之同步
                } catch (_: Exception) {
                    updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "状态同步失败")
                }
            }
        }
    }

    override fun onStopListening() {
        cameraManager?.unregisterTorchCallback(torchCallback)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val manager = cameraManager
        val cameraId = flashCameraId ?: findFlashCameraId(manager)?.also { flashCameraId = it }
        if (cameraId == null) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "未检测到闪光灯")
            showToast("未检测到可用的闪光灯")
            return
        }
        if (!hasCameraPermission()) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "需要相机权限")
            showToast("请在应用内授予相机权限")
            openMainActivity()
            return
        }
        if (manager == null) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "相机服务不可用")
            showToast("相机服务暂不可用")
            return
        }
        try {
            val newState = !torchOn
            manager.setTorchMode(cameraId, newState)
            torchOn = newState
            updateTile(
                state = if (newState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                subtitle = if (newState) "已开启" else "已关闭",
            )
        } catch (_: CameraAccessException) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "手电筒操作失败")
            showToast("手电筒操作失败：相机服务暂不可用，请稍后重试")
        } catch (_: SecurityException) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "需要相机权限")
            showToast("手电筒操作失败：相机权限被拒绝")
        } catch (_: IllegalArgumentException) {
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "设备不支持")
            showToast("手电筒操作失败：闪光灯设备不可用")
        }
    }

    /** 检查是否已授予相机权限。 */
    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** 查找第一个带闪光灯的摄像头 ID，无硬件或查询失败时返回 null。 */
    private fun findFlashCameraId(manager: CameraManager?): String? {
        if (manager == null) return null
        return try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: CameraAccessException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 打开主界面，引导用户在应用内完成相机权限授权。 */
    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14 起仅保留 PendingIntent 重载
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_OPEN_MAIN,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // Android 14 以下使用 Intent 重载（该重载自 Android 14 起已废弃）
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            showToast("无法打开应用，请手动打开 OpenToolbox 授予相机权限")
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
        /** 跳转主界面的请求码。 */
        const val REQUEST_CODE_OPEN_MAIN = 1001

        /** 磁贴显示标题。 */
        const val TILE_LABEL = "手电筒"

        /** 平台图标资源所在的包名（框架资源固定为 "android"）。 */
        const val TILE_ICON_PACKAGE = "android"

        /** 磁贴图标：相机，闪光灯属于相机硬件。 */
        val TILE_ICON_RES = android.R.drawable.ic_menu_camera
    }
}
