# 提案：iOS 平台功能替代实现（ios-platform-features）

## Why

`ios-packaging-foundation` 里程碑让项目能产出 iOS 包，但扫码、运行通知、支付宝支付、应用内更新等重交互能力在 iOS 上仍是安全空实现，iOS 端体验不完整。本 change（批量清单 `.comet/batches/ios-two-milestones.json` 第 2 项，依赖第 1 项）按用户确认的"尽量找 iOS 替代实现"策略，为这些能力提供 iOS 原生替代，使 iOS 端成为可日常使用的一等平台。

## What Changes

- **扫码**：iOS 端用 AVFoundation（`AVCaptureSession` + `AVCaptureMetadataOutput`）实现二维码扫描页，经 Compose `UIKitView` 嵌入共享导航；替换 `QrScannerPage` 的 iOS 占位实现。
- **运行通知**：用 `UNUserNotificationCenter` 展示设备运行/积分任务状态通知，替换 `RunNotifications` iOS 空实现；新增通知权限请求流程。
- **支付宝支付**：iOS 端改为拼装支付宝 URL Scheme 跳转（`alipays://`），返回结果语义与 Android 对齐（无法自动回查支付结果时反馈"支付结果确认中"）。
- **应用内更新**：iOS 端将"下载安装 APK"语义替换为"提示新版本并跳转项目发布页下载"，复用已有版本比较逻辑。
- **权限声明**：`iosApp` 的 Info.plist 补充相机权限等必要声明。
- 不改动 Android 端与共享逻辑的行为。

## Capabilities

### New Capabilities

- `ios-scanning`：iOS 二维码扫描能力——相机权限、取景、解码回调与页面关闭的要求。
- `ios-notifications`：iOS 运行状态通知能力——权限请求、状态更新展示与清除的要求。
- `ios-payment-launch`：iOS 支付宝跳转能力——URL Scheme 拼装、跳转失败反馈与结果语义对齐的要求。
- `ios-update-handoff`：iOS 更新移交能力——新版本提示与跳转发布页下载的要求。

### Modified Capabilities

（无——Android 行为不变；本 change 只为 iOS 填充 Change 1 预留的 stub 行为）

## Impact

- **代码**：`shared/src/iosMain/**` 中扫码/通知/支付/更新相关 actual 的替换实现；`iosApp/` Info.plist 权限声明。
- **依赖**：无需新增第三方依赖（AVFoundation/UNUserNotificationCenter 为系统框架）。
- **平台限制**：iOS 无前台服务，应用退后台后任务执行会暂停——常驻保活仍不在 iOS 范围内。
- **上游 PR**：与 Change 1 同属 iOS 关键修改，可按里程碑分批 PR。
