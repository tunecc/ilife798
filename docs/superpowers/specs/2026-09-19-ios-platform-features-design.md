---
comet_change: ios-platform-features
role: technical-design
canonical_spec: openspec
archived-with: 2026-09-19-ios-platform-features
status: final
---

# 深度技术设计：iOS 平台功能替代实现

需求与验收标准见 OpenSpec change `ios-platform-features`（canonical spec），本文档细化实现方案、技术风险、测试策略与边界条件。基础环境（iOS target、iosMain 骨架、iosApp 工程、无签名打包、CI）由已归档的 `ios-packaging-foundation` 提供。

## 1. 扫码（ios-scanning）

**实现**：`QrScannerPage(onBack, onResult)` iOS actual = Compose 页面 + `UIKitView` 承载相机预览。

- 相机栈：`AVCaptureSession`（`sessionPreset = .high`）+ `AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)` + `AVCaptureMetadataOutput`（`metadataObjectTypes = [.qr]`）；`AVCaptureMetadataOutputObjectsDelegate` 在后台队列回调，取 `metadataMachineReadableCodeObject.stringValue` 首帧命中即 `session.stopRunning()` 并回主线程 `onResult(内容)`（防同帧重复回调）。
- 预览：`AVCaptureVideoPreviewLayer` 放入自定义 UIView，经 Compose `UIKitView({ 创建 UIView }, { 绑定/解绑 session })` 嵌入；`update` 阶段同步 layer.frame。
- 页面结构：TopAppBar 式返回栏（对齐 Android 扫码页视觉）+ 全幅预览 + 中央取景框提示文案。
- 权限：进入页面先 `AVCaptureDevice.authorizationStatus`；未决定 → `requestAccess` 回调决定是否启动；被拒 → 显示提示与「前往设置」按钮（`UIApplication.openSettingsURLString`），不启动 session。
- 生命周期：`DisposableEffect` onDispose 停止 session 并移除 delegate 引用，防相机泄漏。
- Info.plist：`NSCameraUsageDescription = "扫码添加设备需要使用相机"`。
- 边界：模拟器无摄像头 → `default(.builtInWideAngleCamera)` 返回 null，显示"设备无相机"提示（同时满足 CI 与模拟器路径不崩溃）。

备选被否：iOS 16 `DataScannerViewController`（部署目标 15）；ZXing-KMP（仍需自建相机流）。

## 2. 运行通知（ios-notifications）

**实现**：`RunNotifications` iOS actual 直接操作 `UNUserNotificationCenter`。

- 通知槽：设备状态 → id `ilife798.device`（`updateDevice(running=true)` 发"设备 {name} 运行中"，`running=false` 时移除）；积分任务 → id `ilife798.task`（`updateTask(gained)` 发"积分任务已获得 N 分"），`removeTask()` 移除。均用 `add(request)` 同 id 覆盖刷新。
- 权限：首次实际需要展示前调 `requestAuthorization([.alert, .sound])`；拒绝/失败静默跳过，不影响任务。
- 前台展示：`iOSApp.swift` 增加 `NotificationDelegate: NSObject, UNUserNotificationCenterDelegate`（`willPresent` 返回 `[.banner, .sound]`），启动时挂到 `UNUserNotificationCenter.current().delegate`；delegate 由 Swift 侧强持有（静态属性）防释放。
- 通知点击行为：默认打开 App（无深链需求，YAGNI）。

备选被否：仿 Android 多通道多通知（iOS 通知组语义不同）。

## 3. 支付跳转（ios-payment-launch）

**实现**：`payWithAlipay(orderInfo)` iOS actual。

1. `canOpenURL("alipays://")`（需 Info.plist `LSApplicationQueriesSchemes = [alipays, alipay]`）判安装；未安装 → `AlipayPayResult(false, "未安装支付宝")`。
2. 已安装：拼 `alipays://platformapi/startapp?appId=20000125&orderSuffix=<percent-encode(orderInfo)>`，`openURL` 唤起；返回 `AlipayPayResult(false, "已跳转支付宝，支付结果确认中，请稍后刷新余额")`（无法自动回查结果，语义与 Android 8000/6004 分支一致）。
3. 唤起异常 → `AlipayPayResult(false, "支付失败：...")`。

复用后端给 Android SDK 的同一份签名 orderInfo，零后端改动。**风险**：新版支付宝对 `orderSuffix` 收银台跳转偶有兼容性问题——真机验证；失败降级为提示"请手动打开支付宝完成支付"并记录。

备选被否：AlipaySDK-iOS（重量级依赖 + 回调配置，违背关键修改最小化）。

## 4. 更新移交（ios-update-handoff）

**现状问题**：iOS `currentAbis()=[]` → `selectAsset()` 返回 null → `checkForUpdate` 永远提示"暂无本设备安装包"，永远见不到新版本提示。

**共享层最小扩展**（Android 行为零变化）：

- `AppUpdatePlatform.kt`（commonMain）新增两个 expect：
  - `expect fun supportsReleasePageHandoff(): Boolean`——Android actual 返回 `false`；iOS actual 返回 `true`。
  - `expect fun openReleasePage(url: String): Boolean`——Android actual 空实现返回 `false`；iOS actual `openURL`。
- `AppUpdate.kt` 新增常量 `RELEASES_PAGE_URL = "https://github.com/Jursin/ilife798/releases"`。
- `UpdateController.checkForUpdate`：`asset == null` 且 `supportsReleasePageHandoff()` 时，不再提示"暂无本设备安装包"，而是 `pendingReleasePage = true` 并弹既有 `UpdateDialogState.Available(remoteVersion)`。
- `UpdateController.startUpdate`：`pendingReleasePage` 时打开 `RELEASES_PAGE_URL`、提示"已打开发布页，请下载最新 IPA"、关对话框；否则走原 Android 下载安装路径。
- `UpdateDialogs.kt` UI 零改动（Available → onConfirm=startUpdate 已存在）。

Info.plist 无需新增（https 链接不受 ATS 限制）。

## 5. 工程与配置增量

- `iosApp/iosApp/Info.plist`：`NSCameraUsageDescription`、`LSApplicationQueriesSchemes`。
- `iOSApp.swift`：通知 delegate 类与挂载。
- `shared/src/commonMain`：AppUpdatePlatform expect 扩展 + UpdateController 分支；`shared/src/androidMain`：两个新 expect 的 actual（false/no-op）。
- 通知/相机的权限声明与实现同 PR 提交。

## 6. 测试与验证策略

| 层级 | 覆盖 |
|---|---|
| 编译门 | Android 全量门 + iOS 双架构编译（每任务跑） |
| 模拟器 | 扫码页渲染 + 权限拒绝分支（无摄像头路径）；通知权限弹窗/展示/清除；支付未安装分支；更新全流程（版本比较对 GitHub API 真实可用，跳转 Safari 打开 Releases 页） |
| 真机（用户自签） | 扫码识别回填；前台/后台通知横幅；支付宝唤起；更新跳转 |
| CI | 沿用 ios.yml（validate 已覆盖编译+打包） |

## 7. 风险与回退

| 风险 | 缓解 |
|---|---|
| `orderSuffix` 跳转兼容性（新版支付宝） | 真机验证；降级提示手动打开；必要时 Change 后续换 universal link |
| `UIKitView` 相机生命周期泄漏 | DisposableEffect 统一 stop；页面退出路径单测性自查 |
| 前台通知不显示（delegate 未挂/释放） | Swift 侧静态持有 delegate；模拟器验证前台横幅 |
| 共享层分支影响 Android | Android actual=false/no-op；Android 全量门回归 |
| 权限弹窗时机打扰 | 仅首次实际需要时请求 |
| K/N platform API 绑定名与预期不符 | 以编译器提示修正（Change 1 已验证此工作法），不改设计决策 |

回退：各能力独立文件级替换（actual 文件），回滚单文件即可；无数据迁移。
