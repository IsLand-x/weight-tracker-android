# 贡献说明

本仓库是体重记录专用版本。新增功能应围绕体重秤连接、记录、趋势或用户配置的 Webhook，保持界面简单。

修改蓝牙协议时，应说明对应设备名称、协议格式和真实设备验证结果。协议来源必须为公开资料或合法的个人观察，并保留 Gadgetbridge 及其他来源的版权与许可证声明。

提交前运行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。影响称重、去重或发送状态的变更需要验证失败场景，避免重复上传。同一请求超时不能被当作服务端未处理的证据。

源代码采用 AGPL-3.0-or-later。原 Gadgetbridge 作者见 CONTRIBUTORS.rst；本专用版本的协议代码来源见 NOTICE.txt。
