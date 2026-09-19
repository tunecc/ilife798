# Brainstorm Summary

- Change: ios-platform-features
- Date: 2026-09-19

## 确认的技术方案

四个能力全部按 open 阶段选型深化，用户确认：

1. **扫码（ios-scanning）**：`AVCaptureSession` + `AVCaptureMetadataOutput(.qr)`，预览经 Compose `UIKitView` 嵌入 `QrScannerPage` iOS actual；首帧命中即停 session → `onResult`；权限进入页面时请求，被拒显示提示 +「前往设置」（`openSettingsURLString`）；Composable 离开时停 session（DisposableEffect）；Info.plist 增 `NSCameraUsageDescription`。
2. **通知（ios-notifications）**：`RunNotifications` iOS actual → UNUserNotificationCenter；设备状态/积分进度两个固定 id（`ilife798.device`/`ilife798.task`）刷新与移除；权限**首次实际展示前**才请求；未授权静默跳过；前台横幅展示由 Swift 侧 delegate（`iOSApp.swift` 增小 delegate 类）负责，Kotlin 侧只发/删。
3. **支付（ios-payment-launch）**：`canOpenURL("alipays://")` 判安装（Info.plist 增 `LSApplicationQueriesSchemes: [alipays, alipay]`）；已安装拼 `alipays://platformapi/startapp?appId=20000125&orderSuffix=<urlencode(orderInfo)>` 唤起，返回「已跳转支付宝，支付结果确认中，请稍后刷新余额」语义（success=false）；未安装返回明确失败。复用后端给 Android SDK 的同一份 orderInfo，无后端改动。
4. **更新移交（ios-update-handoff）**：共享层最小扩展——`AppUpdatePlatform` 新增 `supportsReleasePageHandoff()`（Android=false/iOS=true）与 `openReleasePage(url)`（Android no-op）两个 expect；`UpdateController` 在「有新版本但 selectAsset 为 null」且平台支持移交时弹既有 Available 对话框，`startUpdate()` 分支改为打开 `https://github.com/Jursin/ilife798/releases` 并关对话框；`UpdateDialogs.kt` UI 零改动，Android 行为零变化。

## 关键取舍与风险

- AVFoundation vs DataScanner（iOS16+，部署目标 15 不满足）；URL Scheme vs AlipaySDK（重量级，违背最小修改）
- `orderSuffix` 收银台跳转在新版支付宝偶有兼容性问题 → 真机验证；失败降级为「请手动打开支付宝」提示
- `UIKitView` 相机生命周期 → DisposableEffect 统一 stop，防泄漏
- 前台通知横幅依赖 Swift delegate 挂载 → iOSApp.swift 固定挂载
- 通知权限首次展示前请求，避免启动打扰
- 共享层 expect 扩展：Android actual 返回 false/no-op，行为零变化（不违反 proposal 非目标）

## 测试策略

模拟器可测：扫码页渲染 + 权限拒绝分支、通知权限与展示、支付未安装分支、更新全流程（对 GitHub API 真实可用）。真机必测（用户自签）：扫码识别、前台/后台通知、支付宝唤起。Android 门照跑。

## Spec Patch

无——4 个 delta spec（ios-scanning/ios-notifications/ios-payment-launch/ios-update-handoff）已覆盖全部行为；共享层 expect 属实现细节。
