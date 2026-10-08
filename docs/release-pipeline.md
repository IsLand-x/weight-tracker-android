# 正式版自动打包与邮件通知

工作流：`.github/workflows/android-release.yml`，名称 **Android Release**。
向 `main` 推送代码、推送 `v*` 标签，或者在 Actions 页面点击 **Run workflow** 均可触发。
只修改 Markdown/文档不会自动触发，仍可手动运行。

流程使用 JDK 21、Android API 37.0、Build Tools 36.0.0 和仓库的 Gradle Wrapper。
先执行单元测试及 Release Lint，再使用原正式版密钥签名，校验签名证书后上传 APK、
SHA-256 校验文件和构建元信息。产物保留 90 天，测试/Lint 报告保留 14 天。
下载地址在每次运行的 Summary 与 Artifacts 中，邮件也会包含下载地址。

## 签名配置

仓库 Settings → Secrets and variables → Actions → **Secrets**：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 原正式版签名文件的 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | 签名文件密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

**Variables** 中的 `ANDROID_SIGNING_CERT_SHA256` 保存公开的签名证书 SHA-256 指纹。
工作流要求 APK 指纹与该值一致，避免换密钥导致无法覆盖安装。
密钥在构建时写入 Runner 临时目录，用完即删除，不包含在产物中。
本地仍可使用被 Git 忽略的 `.local-dev/signing.properties` 进行签名。

## GitHub 自带邮件通知

采用 GitHub Actions 原生通知，无需 SMTP 服务或邮件密码。
进入 https://github.com/settings/notifications ，在 **System → Actions** 中开启 **Email**，
并关闭 **Only notify for failed workflows**，保存后成功/失败构建都能通知。
同时确保正在关注本仓库。邮件发到 GitHub 账号已验证的默认通知邮箱。

这是账号级 Actions 偏好，也会影响你触发的其他仓库工作流。
原生邮件通知的是你本人触发的运行；其他协作者触发时遵循 GitHub 的通知规则。
邮件包含构建状态及运行链接，进入运行页 Summary/Artifacts 下载 APK。
工作流无法强制修改账号通知偏好，也不声明邮件已送达。

官方说明：
- https://docs.github.com/en/actions/concepts/workflows-and-actions/notifications-for-workflow-runs
- https://docs.github.com/en/subscriptions-and-notifications/how-tos/managing-github-actions-notifications

CLI 手动触发：

```sh
gh workflow run android-release.yml --repo IsLand-x/weight-tracker-android --ref main
gh run list --repo IsLand-x/weight-tracker-android --workflow android-release.yml
```
