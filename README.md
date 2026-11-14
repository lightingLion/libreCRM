# 穷哥们电销助手

一款面向日常客户联系工作的本地 Android 工具。客户、任务、联系记录与导入草稿保存在设备本地；应用不要求注册账号，也不包含广告或统计 SDK。

## 当前版本

- 阶段六发布准备版，版本 `0.1.0`（versionCode `1`）。
- 最低 Android 版本为 Android 9（API 28）；当前构建目标为 API 36。
- 当前开发 applicationId 为 `com.qiongge.callassistant.dev`，仅作开发占位，正式包名待作者确认。
- 霞鹜文楷提供 Regular 和 Medium 字体文件。上游 Lite 字体包没有 Bold 文件，标题使用真实 Medium 字重。

## 已实现功能

- 本地创建、编辑、搜索客户，创建任务并管理任务成员和进度。
- 用户点击客户页的拨打按钮后直接发起电话，首次使用需授予拨打权限。任务卡片上的自动拨号设置默认关闭；开启后，返回应用时倒数拨打下一位，默认间隔 5 秒，期间可取消。
- 图片名单 OCR 校对、CSV 与 `.xlsx` 导入草稿、字段映射、重复与空字段跳过，以及客户数据导出。
- Room 数据库迁移保留已有数据；客户名单和记录默认不上传。

OCR 模型随应用离线打包。OCR 识别结果应由用户校对后再导入。当前发行包只构建 `arm64-v8a`。

## 构建

需要 Android SDK 36、JDK 17 和本机可用的 Android SDK 许可。Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease
```

APK 输出位置：

- Debug：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Release：`app/build/outputs/apk/release/app-arm64-v8a-release.apk`

Release 构建开启 R8 混淆与资源收缩。当前 release variant 使用 Android 调试密钥签名，便于开发验收与侧载，不是可用于商店发布的生产签名包。正式发布前，作者需要提供并安全保管正式签名密钥与凭据，并确认正式 applicationId、项目许可证、GitHub 仓库地址、作者联系邮箱和正式图标。不得复用或公开调试密钥作为生产凭证。

## 开源与第三方许可

项目自身许可证尚待作者确认，因此仓库暂不声明项目代码采用某一开源许可证。第三方组件及随包资源的许可说明见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)；完整许可文本保存在 `app/src/main/assets/licenses/`。项目自身许可证确定后，应在仓库根目录补充对应的 `LICENSE` 文件。

## 阶段边界

本仓库实现以 [开发文档.md](开发文档.md) 为准。应用不读取通话记录、不录音、不进行云端客户同步，也不包含广告或用户行为追踪。模型在线下载与模型管理设置不属于当前阶段。
