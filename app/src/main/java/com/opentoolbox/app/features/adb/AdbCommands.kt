package com.opentoolbox.app.features.adb

/**
 * 一条 ADB 快捷命令。
 *
 * @param category 分类名称（电池/应用管理/按键与输入/显示与系统/网络/截屏录屏）
 * @param title    中文标题
 * @param command  真实可执行的命令（shell 命令或 adb shell 命令）
 * @param description 一句话中文说明
 */
data class AdbCommand(
    val category: String,
    val title: String,
    val command: String,
    val description: String,
)

/**
 * 内置 ADB 命令库：全部数据真实可用，覆盖电池、应用管理、按键与输入、
 * 显示与系统、网络、截屏录屏六类常用场景。
 */
object AdbCommandLibrary {

    const val CATEGORY_BATTERY = "电池"
    const val CATEGORY_APP = "应用管理"
    const val CATEGORY_INPUT = "按键与输入"
    const val CATEGORY_DISPLAY = "显示与系统"
    const val CATEGORY_NETWORK = "网络"
    const val CATEGORY_CAPTURE = "截屏录屏"

    val commands: List<AdbCommand> = listOf(
        // ---------- 电池 ----------
        AdbCommand(
            category = CATEGORY_BATTERY,
            title = "电池信息",
            command = "adb shell dumpsys battery",
            description = "查看电量、充电状态、电池温度与健康度等信息",
        ),
        AdbCommand(
            category = CATEGORY_BATTERY,
            title = "耗电统计",
            command = "adb shell dumpsys batterystats",
            description = "查看整机耗电统计，可加包名定位单个应用的耗电情况",
        ),
        AdbCommand(
            category = CATEGORY_BATTERY,
            title = "模拟满电量",
            command = "adb shell dumpsys battery set level 100",
            description = "把电量临时模拟为 100%，多用于自动化测试场景",
        ),
        AdbCommand(
            category = CATEGORY_BATTERY,
            title = "重置电池模拟",
            command = "adb shell dumpsys battery reset",
            description = "取消全部电池模拟参数，恢复系统真实电量读数",
        ),
        AdbCommand(
            category = CATEGORY_BATTERY,
            title = "模拟未充电",
            command = "adb shell dumpsys battery unplug",
            description = "模拟拔掉充电线，用于测试耗电与省电逻辑",
        ),

        // ---------- 应用管理 ----------
        AdbCommand(
            category = CATEGORY_APP,
            title = "列出全部应用",
            command = "pm list packages",
            description = "列出设备上已安装的全部应用包名",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "列出第三方应用",
            command = "pm list packages -3",
            description = "仅列出用户自行安装的第三方应用包名",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "列出系统应用",
            command = "pm list packages -s",
            description = "仅列出系统内置应用的包名",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "查看 APK 路径",
            command = "pm path <包名>",
            description = "打印指定应用 APK 的安装路径，便于备份提取安装包",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "清除应用数据",
            command = "pm clear <包名>",
            description = "清空指定应用的全部数据与缓存，恢复到首次安装状态",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "强制停止应用",
            command = "am force-stop <包名>",
            description = "强制结束指定应用的全部进程，解决卡死无响应",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "停用应用",
            command = "pm hide <包名>",
            description = "隐藏并停用指定应用但不卸载，可随时恢复",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "恢复应用",
            command = "pm unhide <包名>",
            description = "恢复被 pm hide 停用隐藏的应用",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "免 Root 卸载系统应用",
            command = "pm uninstall -k --user 0 <包名>",
            description = "对当前用户卸载系统应用并保留数据，无需 root，可恢复",
        ),
        AdbCommand(
            category = CATEGORY_APP,
            title = "查看应用详情",
            command = "dumpsys package <包名>",
            description = "查看指定应用的版本、权限、签名与组件信息",
        ),

        // ---------- 按键与输入 ----------
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "电源键",
            command = "input keyevent 26",
            description = "模拟按下电源键，可点亮或熄灭屏幕",
        ),
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "解锁屏幕",
            command = "input keyevent 82",
            description = "模拟菜单键滑动解锁，适用于无密码锁屏",
        ),
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "返回键",
            command = "input keyevent 4",
            description = "模拟按下返回键，退出当前页面",
        ),
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "输入文本",
            command = "input text <文本>",
            description = "向当前焦点输入框输入文本，不支持中文与空格",
        ),
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "模拟点击",
            command = "input tap <x> <y>",
            description = "在指定屏幕坐标模拟一次点击，坐标可从开发者选项查看",
        ),
        AdbCommand(
            category = CATEGORY_INPUT,
            title = "模拟滑动",
            command = "input swipe <x1> <y1> <x2> <y2> <时长>",
            description = "从起点坐标滑动到终点坐标，时长单位为毫秒，如 300",
        ),

        // ---------- 显示与系统 ----------
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "查看分辨率",
            command = "wm size",
            description = "查看当前屏幕的物理分辨率与显示分辨率",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "修改屏幕 DPI",
            command = "wm density <dpi>",
            description = "修改屏幕显示密度（如 420），放大或缩小全局界面",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "重置屏幕 DPI",
            command = "wm density reset",
            description = "撤销手动修改的显示密度，恢复系统默认值",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "开启自动亮度",
            command = "settings put system screen_brightness_mode 1",
            description = "开启系统的自动亮度调节",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "手动设置亮度",
            command = "settings put system screen_brightness <0-255>",
            description = "设置固定亮度数值，需先关闭自动亮度",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "查看 Android ID",
            command = "settings get secure android_id",
            description = "读取当前用户的 Android ID，用于调试与设备标识",
        ),
        AdbCommand(
            category = CATEGORY_DISPLAY,
            title = "查看设备型号",
            command = "getprop ro.product.model",
            description = "读取设备型号的系统属性",
        ),

        // ---------- 网络 ----------
        AdbCommand(
            category = CATEGORY_NETWORK,
            title = "开启 Wi-Fi",
            command = "svc wifi enable",
            description = "打开设备的 Wi-Fi 开关",
        ),
        AdbCommand(
            category = CATEGORY_NETWORK,
            title = "关闭 Wi-Fi",
            command = "svc wifi disable",
            description = "关闭设备的 Wi-Fi 开关",
        ),
        AdbCommand(
            category = CATEGORY_NETWORK,
            title = "开启移动数据",
            command = "svc data enable",
            description = "打开移动数据开关",
        ),
        AdbCommand(
            category = CATEGORY_NETWORK,
            title = "关闭移动数据",
            command = "svc data disable",
            description = "关闭移动数据开关",
        ),
        AdbCommand(
            category = CATEGORY_NETWORK,
            title = "设置飞行模式开",
            command = "settings put global airplane_mode_on 1",
            description = "写入飞行模式开启标记，通常还需发送广播使其生效",
        ),

        // ---------- 截屏录屏 ----------
        AdbCommand(
            category = CATEGORY_CAPTURE,
            title = "截取屏幕",
            command = "screencap /sdcard/screen.png",
            description = "将当前屏幕截图保存到设备的 /sdcard/screen.png",
        ),
        AdbCommand(
            category = CATEGORY_CAPTURE,
            title = "录制屏幕",
            command = "screenrecord /sdcard/demo.mp4",
            description = "录制屏幕保存为 mp4，默认最长 3 分钟，Ctrl+C 结束",
        ),
    )
}
