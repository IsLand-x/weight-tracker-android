# 体重记录 / Weight Recorder

基于 Gadgetbridge 体重秤协议拆出的 Android 小应用。只保留称重、趋势图和用户自行配置的 Webhook，已移除手环、手表、运动、消息同步等设备模块及代码生成器。

## 使用

1. 安装 APK，打开「选择体重秤」，允许蓝牙权限，踩一下秤唤醒，再选择扫描到的设备。
2. 首页显示广播名称、识别的协议类型。秤提供标准蓝牙型号信息时，也会显示型号；广播名称不一定等于商品型号。
3. 在「设置」填写 Webhook 地址并启用。允许通知后，成功返回的 `message` 会显示为通知，同时保存在记录详情和首页。
4. 保持「连接体重秤」开启即可接收稳定称重；秤休眠后会等待重新唤醒。断开按钮会停止后台监听。Android 6–11 扫描还需要位置权限，部分手机需要开启系统定位；后台持续运行受手机省电设置影响。

首页提供「近 7 天」「近 30 天」「近一年」，可左右拖动到更早的记录，点击数据点查看时间和体重。三种时间范围都包含今天，「近一年」按日历年计算。

## 设备范围

| 识别信息 | 协议 |
| --- | --- |
| `MI SCALE` / `MI SCALE2` | 小米体重秤，10 字节稳定重量数据 |
| `MIBFS` / `MIBCS`，或华米厂商标识与 `0x181B` 服务 | 小米/华米体脂秤，13 字节稳定重量数据 |
| `0x181D` 服务 | 标准 Bluetooth Weight Scale 服务 |

解码基于原项目的 Mi Smart Scale 与 Mi Composition Scale 实现。支持公斤、斤和磅数据，保存、显示、上传统一使用公斤。只接收稳定的成人称重数据（10–300 kg）；不计算体脂等指标。

没有实际体重秤接入开发电脑，尚未验证用户的具体型号。使用其他广播格式、加密协议或 Wi-Fi 的新款秤需要另外适配，不能仅凭「小米」品牌确认兼容。

## Webhook

HTTP / HTTPS `POST`，`Content-Type: application/json; charset=utf-8`。请求体严格只有两个数字字段：

```json
{"timestamp": 1791244800, "weight": 70.25}
```

- `timestamp` 是实际称重时刻的 Unix 时间戳，单位秒，并非当天零点或发送请求的时间。实时数据中明显失准的秤内时钟会使用手机接收时间。
- `weight` 是公斤数。

HTTP 2xx 且 JSON 中的数字 `status` 为 `0`、`message` 为字符串才视为成功：

```json
{"status": 0, "message": "今日体重已记录"}
```

先保存本机记录，再通过 WorkManager 在有网络时发送。重复数据不会再次创建请求。不自动跟随重定向；连接与读取超时均为 15 秒，响应上限 64 KiB。

网络恢复前显示「等待发送」。收到失败响应、请求超时或发送过程被中断会显示「发送未确认」，可以在记录详情手动重试；超时后服务端可能已处理，自动重试可能造成重复记录，因此不会静默重试。

小米体重秤若提供旧历史数据，会导入本机；旧历史不会自动调用 Webhook，可以在记录详情手动发送仅本机的记录。Webhook 默认关闭，地址由用户配置，不内置外部服务或密钥。

## 开发

界面使用官方 Material Components for Android 1.14.0 的 Material 3 主题。主操作用实心圆角按钮，次要操作用文字按钮，7 / 30 天 / 一年用单选分段按钮。统一采用 4 / 8 dp 间距、24 dp 卡片圆角、至少 48 dp 触控区，并为浅色和深色模式设置独立颜色。信息顺序为最近体重、趋势、秤连接、最近记录。

单一 `app` 模块，Java。需要 JDK 21、Android SDK Platform 37 和 Build Tools 36.0.0；Gradle 9.7.1 由项目 Wrapper 下载。应用最低 Android 6（API 23），目前 target SDK 35。

在 Android Studio 打开仓库根目录，使用 Gradle Local Java Home / JDK 21；`local.properties` 中设置自己的 SDK 路径（Windows 盘符冒号需转义）。此电脑已安装 JDK、Android Studio、SDK 和 Android 15 模拟器，可双击桌面「Gadgetbridge 开发」快捷方式打开项目。

PowerShell：

```powershell
# 仅此电脑：加载已配置的本地开发环境
. .\.local-dev\env.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。另外提供经压缩的 `app/build/outputs/apk/release/weight-recorder-1.0.0.apk` 测试安装包，由本机测试密钥签名。正式发布应配置自己的长期签名密钥。安装：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

测试覆盖设备识别、稳定重量解析、单位换算、异常时间戳、重复记录、时间范围、严格 JSON 契约及本地 HTTP POST 往返。模拟器可以验证页面、图表和通知，蓝牙兼容性仍需真机实测。

包名为 `org.freeyourgadget.weightrecorder`，可以与 Gadgetbridge 同时安装，数据库独立，不自动迁移原应用的数据。调试包可直接安装；发布版本应使用自己的签名密钥。

## 来源与许可证

这是独立的专用版本，源自 [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge/) 的提交 `86389f5366e6ecf438657e6d412bfb77b83480e2`。当前工作分支为 `codex/weight-recorder`，原代码保留在 Git 历史中。

遵循 **AGPL-3.0-or-later**，保留原作者版权，详见 [LICENSE](LICENSE)、[NOTICE.txt](NOTICE.txt) 和 [CONTRIBUTORS.rst](CONTRIBUTORS.rst)。分发修改后的应用时，应按许可证提供对应源代码。
