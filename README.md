# 穷哥们电销助手

穷哥们电销助手是一款面向日常客户联系工作的 Android 应用，帮助整理客户资料、跟进任务和联系记录。客户数据保存在设备本地。
今天睡地板，明天做老板！

Copyright © 2026 lightingLion

## 功能

- 创建任务名单，管理客户和跟进进度。
- 手动联系客户，记录联系状态与备注；可启用自动拨号（默认关闭），拨号间隔默认 5 秒且可调整。
- 从图片名单识别客户信息，并在导入前校对。
- 导入 CSV、Excel 名单，设置字段映射并处理重复记录。
- 导出客户资料为 CSV 或 Excel 文件。

## 企业定制需求

可根据企业业务需求定制：

- WebDAV 数据管理
- 定期自动导出客户数据
- 从企业公池获取电话本

## 技术栈

| 用途 | 项目 |
| --- | --- |
| Android 开发 | [Kotlin](https://github.com/JetBrains/kotlin) |
| 原生界面与动画 | [Jetpack Compose](https://github.com/androidx/androidx)、[Material 3](https://github.com/androidx/androidx) |
| 本地数据与设置 | [Room](https://github.com/androidx/androidx)、[DataStore](https://github.com/androidx/androidx) |
| 中文 OCR | [PaddleOCR PP-OCRv6 Small](https://github.com/PaddlePaddle/PaddleOCR)、[ONNX Runtime Android](https://github.com/microsoft/onnxruntime) |
| 表格识别与图像处理 | [SLANet_plus](https://github.com/PaddlePaddle/PaddleOCR)、[OpenCV](https://github.com/opencv/opencv) |
| 中文姓名识别 | [HanLP](https://github.com/hankcs/HanLP) |
| 中文字体 | [霞鹜文楷](https://github.com/lxgw/LxgwWenKai) |
| 导入字段匹配 | 项目内实现 |

## 环境要求

- Android 9（API 28）或更高版本
- JDK 17
- Android SDK 36

## 构建

在 Windows PowerShell 中运行：

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease
```

APK 输出位置：

- Debug：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Release：`app/build/outputs/apk/release/app-arm64-v8a-release.apk`

## 联系

- 作者邮箱：[hjian9792@gmail.com](mailto:hjian9792@gmail.com)
- GitHub：[lightingLion/libreCRM](https://github.com/lightingLion/libreCRM)

## 许可证

本项目采用 [GNU General Public License v3.0（GPL-3.0）](LICENSE)。

## 第三方许可

第三方组件、模型与字体的许可信息见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)，完整许可文本位于 `app/src/main/assets/licenses/`。
