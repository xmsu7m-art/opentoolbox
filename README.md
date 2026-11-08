# 全能工具箱 OpenToolbox

[![Android CI](https://github.com/xmsu7m-art/opentoolbox/actions/workflows/android.yml/badge.svg)](https://github.com/xmsu7m-art/opentoolbox/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/xmsu7m-art/opentoolbox)](https://github.com/xmsu7m-art/opentoolbox/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

仿照开源项目 [essentials](https://github.com/sameerasw/essentials) 打造的安卓全能工具箱：
**完全开源、无广告**，集成电池监控、应用管理、ADB 快捷操作、设备信息等实用功能。

## 下载

- **最新正式版**：[Releases 页面](https://github.com/xmsu7m-art/opentoolbox/releases/latest) 下载 APK（推荐）
- **开发版**：每次提交后由 [Android CI](https://github.com/xmsu7m-art/opentoolbox/actions/workflows/android.yml) 自动产出（Actions 页面 → 最新 run → Artifacts）

- Kotlin 2.0 + Jetpack Compose（Material 3）
- 最低支持 Android 8.0（minSdk 26），目标 Android 15（targetSdk 35）
- 无任何第三方 SDK、无统计埋点、无广告

## 功能一览

| 页签 | 模块 | 说明 |
| --- | --- | --- |
| 工具 | 电池监控 | 环形电量、充放电状态、健康度、温度、电压、实时电流/功率、剩余容量、电量历史曲线 |
| 工具 | 设备信息 | 系统版本、安全补丁、CPU 架构与核心数、内存/存储占用、屏幕分辨率与刷新率、传感器列表 |
| 工具 | 网络信息 | 网络类型与计费状态、接口/DNS/网关、本机 IPv4/IPv6、Wi-Fi 信号详情 |
| 工具 | 手电筒 | 闪光灯开关，Android 13+ 支持亮度调节 |
| 工具 | 指南针 | 旋转矢量传感器实时方位角与表盘 |
| 工具 | 屏幕检测 | 九色全屏循环，检查坏点与亮度均匀性 |
| 工具 | 单位换算 | 长度/重量/温度/数据/速度，即时换算 |
| 工具 | 振动测试 | 短振、长振、节奏与双重振动波形 |
| 工具 | 屏幕常亮 | `FLAG_KEEP_SCREEN_ON` 开关，无需权限 |
| 工具 | 文本工具 | 字符/单词/行数统计，Base64·URL·HEX 编解码，大小写与空白处理 |
| 工具 | 随机工具 | 区间随机数与不重复抽取、多面骰子、抛硬币、SecureRandom 密码生成 |
| 工具 | 秒表计时 | 精确到百分秒的秒表与计次，倒计时预设与自定义、结束提示音 |
| 工具 | 分贝计 | 麦克风环境噪声估算与仪表盘、环境分级、最大/平均统计 |
| 工具 | 日期工具 | 日期间隔计算、日期推算（月末对齐）、今天速查与春节倒计时 |
| 工具 | 水平仪 | 双轴气泡水平仪与单轴水平尺、归零校准、低通滤波防抖 |
| 应用 | 应用管理 | 已装应用列表：搜索、按名称/大小排序、用户/系统过滤；详情页含版本、权限授予状态、打开/卸载/系统详情入口 |
| ADB | 命令中心 | 35 条常用命令卡片：分类、搜索、一键复制、收藏、执行历史，附 Shizuku 免 root 说明 |
| 设置 | 关于与数据 | 版本信息、开源说明、清除 ADB 收藏/历史 |
| 系统 | 快捷磁贴 | 通知栏磁贴：手电筒开关、屏幕常亮（系统级，插电时生效） |
| 系统 | 电池小部件 | 桌面小组件：电量百分比、充电状态、进度条，电量变化实时刷新 |

## 目录结构

```
app/src/main/java/com/opentoolbox/app/
├── MainActivity.kt
├── data/Prefs.kt                  # SharedPreferences 轻量存储
├── features/
│   ├── adb/                       # ADB 命令库 + 命令中心页
│   ├── apps/                      # 应用列表 + 应用详情
│   ├── battery/                   # 电池监控
│   ├── device/                    # 设备信息
│   ├── network/                   # 网络信息
│   ├── settings/                  # 设置
│   └── tools/                     # 九个实用工具页
└── ui/
    ├── ToolboxApp.kt              # 底部导航 + NavHost
    ├── components/                # FeatureCard / InfoRow 等通用组件
    ├── navigation/TabRoutes.kt    # 路由常量
    └── theme/Theme.kt             # Material 3 主题（动态取色 + 深色模式）
```

## 构建运行

1. 用 **Android Studio（Ladybug 或更新，JDK 17）** 打开项目根目录，首次打开会自动生成 Gradle Wrapper 并下载依赖。
2. 或命令行：安装 Gradle 8.9 后执行 `gradle wrapper && ./gradlew :app:assembleDebug`。
3. 产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `CAMERA` | 手电筒闪光灯控制（不拍照、不预览） |
| `RECORD_AUDIO` | 分贝计测量环境噪声（不录音保存，数据不出本机） |
| `VIBRATE` | 振动测试 |
| `WAKE_LOCK` | 屏幕常亮辅助 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 网络与 Wi-Fi 信息展示 |
| `QUERY_ALL_PACKAGES` | 应用管理列表（本应用为工具箱，符合政策范畴） |
| `REQUEST_DELETE_PACKAGES` | 应用管理内发起卸载 |

## 致谢与许可

灵感与功能设计参考 [sameerasw/essentials](https://github.com/sameerasw/essentials)，
本项目为独立实现，代码以 MIT 许可开源（见 [LICENSE](LICENSE)）。
