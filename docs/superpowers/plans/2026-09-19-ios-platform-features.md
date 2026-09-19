---
change: ios-platform-features
design-doc: docs/superpowers/specs/2026-09-19-ios-platform-features-design.md
base-ref: f56e965
---

<!-- comet-task-authority: docs/openspec/changes/ios-platform-features/tasks.md -->

<!-- comet-task-authority: docs/openspec/changes/ios-platform-features/tasks.md -->

# iOS 平台功能替代实现（ios-platform-features）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ILife798 的 iOS 端补齐四个重交互能力的原生替代：AVCaptureSession 扫码页、UNUserNotificationCenter 运行通知、支付宝 URL Scheme 支付跳转、以及「新版本提示 + 跳转项目 Releases 页」的应用内更新移交；替换 Change 1（`ios-packaging-foundation`）预置的四个安全空实现，Android 端行为零变化。

**Architecture:** 四个能力各自落在独立的 iosMain actual 文件（`QrScannerPage.ios.kt` / `RunNotifications.ios.kt` / `Alipay.ios.kt` / `AppUpdatePlatform.ios.kt`），经共享层既有 expect 接口接入，单文件可独立回滚。更新移交需要共享层最小扩展：`AppUpdatePlatform` 新增 2 个 expect（Android actual 恒 false/no-op 保证零行为变化），`UpdateController` 增加 `pendingReleasePage` 分支复用既有 `UpdateDialogState.Available` 弹窗，UI 层零改动。Swift 侧仅 `iOSApp.swift` 增加前台通知 delegate；`Info.plist` 增加相机权限与支付宝 scheme 查询声明。

**Tech Stack:** Kotlin 2.4.20 / Compose Multiplatform 1.12.0（`UIKitView`）/ Kotlin/Native platform 库（AVFoundation、UserNotifications、UIKit、darwin GCD）/ SwiftUI（iOS 15.0+）/ Gradle 双门 + xcodebuild 模拟器构建。零新增第三方依赖。

**Spec:** `docs/openspec/changes/ios-platform-features/proposal.md` 与 `specs/` 下 4 个 delta spec（`ios-scanning`、`ios-notifications`、`ios-payment-launch`、`ios-update-handoff`）为 canonical；技术设计 `docs/superpowers/specs/2026-09-19-ios-platform-features-design.md`（下文 design §N 均指该文档章节）。计划与 spec 冲突时以 OpenSpec 文件为准。

## 全局约束（Global Constraints）

以下约束作用于每一个任务（数值原样取自 spec/design）：

- **版本冻结**：Kotlin 2.4.20 / CMP 1.12.0 / AGP 9.4.0 / Ktor 3.5.2 / miuix 0.9.4-rc01 / coil 3.6.2；本 change **零新增第三方依赖**（AVFoundation、AVKit、UIKit、UserNotifications 均为系统框架，design §Impact）。
- **平台底线**：iOS 部署目标 `15.0`；bundle id `com.github.ilife798.iosApp`；framework 名 `shared`。通知前台展示用 `[.banner, .sound]`（`.banner` 需 iOS 14+，部署目标 15.0 满足）。
- **Android 零回归**：不改动 androidApp 构建配置与运行行为；共享层只做加法——`AppUpdatePlatform` 新增 2 个 expect，Android actual 恒 `false`/no-op（design §4），`UpdateController` 分支在 Android 上语义逐字不变。
- **双编译门**：每个动 Kotlin 的任务收尾执行 `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`（下称「双门」，与 `.github/workflows/ios.yml` 和 android.yml 的核心任务一致）。
- **Swift/工程门**：凡改动 `iosApp/`（iOSApp.swift、Info.plist）的任务，另跑 xcodebuild 模拟器构建（Gradle 不编译 Swift）；`Info.plist` 改动先 `plutil -lint`。
- **代码风格**：提交前 `./gradlew spotlessApply`（ktlint 1.8.0 / ktlint_official，4 空格缩进 + 尾逗号 + max 140 列，规则见 `.editorconfig`）；iosMain 文件命名 `Xxx.ios.kt`。
- **绑定名原则**：Kotlin/Native platform API 的绑定名/参数名/可空性以编译器提示为准修正（补删 `!!`、改参数标签、增删 import、`deviceInputWithDevice` 与构造器之间二选一等），**不改设计决策、不改对外签名**（Change 1 已验证该工作法，design §7 风险表）。本文代码按最可能的绑定形态书写。
- **权限最小化**：相机权限在进入扫码页时请求一次；通知权限在首次实际需要展示时请求（`requestAuthorizationWithOptions`）；拒绝/失败一律静默降级，不崩溃、不阻塞任务（design §1/§2、§7 风险「权限弹窗时机打扰」）。
- **平台边界**：iOS 无前台服务，应用退后台后任务执行暂停——常驻保活不在本 change 范围（proposal Impact）。
- **验证证据**：模拟器/真机验证结论统一写入 `docs/openspec/changes/ios-platform-features/verification-evidence.md`（Task 3 创建，后续任务追加），对应 tasks 5.1「记录验证证据于 change 目录」。
- **环境要求**：涉及 `./gradlew ...Ios...`、xcodebuild、模拟器的步骤只能在 macOS（Xcode 16+）上执行；所有 Gradle 命令在仓库根目录执行；模拟器名以 `xcrun simctl list devices available` 实际输出调整（本文以 `iPhone 16` 为例）。
- **分支与基线**：从 base-ref `f56e965` 之后的工作分支执行；每任务单独提交，提交信息用约定式前缀（feat/docs）。

## Review Focus

spec 暗示但任务测试未直接覆盖、最可能咬人的五类输入/条件（每行已在其归属任务中以步骤钉住，按咬人概率排序）：

1. **Android 零行为变化**：共享层加分支后，Android 上 `checkForUpdate` 在 `asset == null` 时必须仍提示「暂无本设备安装包」，下载/安装路径逐字不变——Task 1 步骤 4 双门 + Task 6 步骤 3 逐字 diff 核对钉住。
2. **扫码同帧/连续多帧重复回调**：`captureOutput` 在后台队列高频回调，识别命中后必须仅回填一次设备编号并停会话——Task 2 步骤 1 的 `AtomicBoolean.compareAndSet` + `stopRunning` 代码钉住；Task 7 真机清单第 1 项复验「仅一次回填」。
3. **通知权限被拒**：拒绝后设备/积分任务必须照常运行，投递被系统静默忽略，应用不崩溃——Task 4 步骤 4 的模拟器拒绝路径钉住。
4. **orderInfo 含保留字符**：签名 orderInfo 含 `&`/`=`/`+`/`%` 等字符时 percent-encoding 必须完整，截断会改变订单——Task 5 步骤 1 的「全量非字母数字编码」钉住；Task 7 真机清单第 3 项以真实订单复验支付宝唤起。
5. **iOS Toast 静默**：支付结果与更新移交的提示文案经 `showToast` 展示，而 iOS Toast 目前是 no-op（`Toast.ios.kt` 为 Change 1 骨架，注释已预告替代评估）——本 change 不扩大范围改 Toast，Task 5/6 的验证以 `logDebug` console 日志 + 支付宝/Safari 实际唤起行为为准，并把该边界记入验证证据。

---

## 文件结构（本次改动落点）

```
shared/src/commonMain/kotlin/com/github/ilife798/update/
  AppUpdatePlatform.kt          ← 改：文件末尾 +2 expect（supportsReleasePageHandoff / openReleasePage）
  AppUpdate.kt                  ← 改：object 内 +RELEASES_PAGE_URL 常量
  UpdateController.kt           ← 改：+pendingReleasePage 字段与 checkForUpdate/startUpdate 两处分支
shared/src/androidMain/kotlin/com/github/ilife798/update/
  AppUpdatePlatform.android.kt  ← 改：文件末尾 +2 actual（false / no-op 返回 false）
shared/src/iosMain/kotlin/com/github/ilife798/
  update/AppUpdatePlatform.ios.kt     ← 改：文件末尾 +2 actual（true / openURL）+ import
  ui/page/device/QrScannerPage.ios.kt ← 替换：AVCaptureSession + UIKitView 扫码页（含权限状态机）
  RunNotifications.ios.kt             ← 替换：UNUserNotificationCenter 单通知槽
  pay/Alipay.ios.kt                   ← 替换：canOpenURL + orderSuffix URL Scheme 跳转
iosApp/iosApp/
  Info.plist                    ← 改：+NSCameraUsageDescription（Task 2）、+LSApplicationQueriesSchemes（Task 5）
  iOSApp.swift                  ← 改：+NotificationDelegate（前台横幅，静态强持有）
docs/openspec/changes/ios-platform-features/
  verification-evidence.md      ← 新：模拟器/真机验证证据（Task 3 创建，Task 4-7 追加）
```

不改动：`UpdateDialogs.kt`（Available → onConfirm=startUpdate 已存在，design §4）、`UpdateDialogState.kt`、`MainScaffold.kt`（扫码调用方不变）、`Toast.ios.kt`（边界见 Review Focus #5）、androidApp 全部、`.comet/**` 工作流文件。

## 与 tasks.md 的映射与两处执行说明

| tasks.md | 本计划任务 |
|---|---|
| 1.1 | Task 2（扫码页实现；Info.plist 相机声明并入本任务——模拟器验证需要合法的权限弹窗，否则 `requestAccessForMediaType` 会因缺 usage description 直接崩溃） |
| 1.2 | Task 2（被拒提示与「前往设置」代码，与 1.1 同一文件不可分割）+ Task 3（权限拒绝分支的模拟器验证与证据） |
| 2.1 | Task 4 |
| 3.1 | Task 5 |
| 4.1 | Task 1（共享层 expect 扩展 + 双平台 actual，编译门最前置）+ Task 6（UpdateController 移交分支与更新流程验证） |
| 5.1 | Task 7 |

说明：任务顺序按「共享层 expect 扩展 → 扫码 → 通知 → 支付 → 更新 → 收尾回归」排列（编译门最前置）。更新移交拆成 Task 1 / Task 6 两段：Task 1 只做纯加法的签名扩展与双平台 actual，让共享层签名变化在所有 iOS 实现改动之前先过双门；`UpdateController` 的消费分支与更新流程验证留在 Task 6。

---

### Task 1: 共享层 AppUpdatePlatform expect 扩展与双平台 actual【tasks 4.1 前置部分】
<!-- comet-task-ref:d710cdbb-2ec0-406f-8eb1-cbe82c1dc9ec -->

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.kt`（文件末尾追加）
- Modify: `shared/src/androidMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.android.kt`（文件末尾追加）
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.ios.kt`（文件末尾追加 + 顶部 import）

**Interfaces:**
- Consumes: 无（起点任务）。
- Produces: commonMain 两个新 expect 及双平台 actual，后续 Task 6 的 `UpdateController` 按此 exact 签名消费：
  - `fun supportsReleasePageHandoff(): Boolean`（Android `false` / iOS `true`）
  - `fun openReleasePage(url: String): Boolean`（Android no-op 返回 `false` / iOS `UIApplication.openURL`）

- **Step 1: 在 `AppUpdatePlatform.kt` 文件末尾追加两个 expect**

```kotlin
// 是否支持「跳转发布页下载」的更新移交：无 APK 安装概念的平台返回 true（design §4）。
expect fun supportsReleasePageHandoff(): Boolean

// 打开发布页并返回是否成功发起跳转；不支持移交的平台为空实现并返回 false。
expect fun openReleasePage(url: String): Boolean
```

- **Step 2: 在 `AppUpdatePlatform.android.kt` 文件末尾追加两个 actual**

```kotlin
actual fun supportsReleasePageHandoff(): Boolean = false

actual fun openReleasePage(url: String): Boolean = false
```

（Android 零行为变化：恒 `false`/no-op，Task 6 之前无任何调用方。）

- **Step 3: 在 `AppUpdatePlatform.ios.kt` 中追加 actual 与 import**

文件顶部 import 区追加两行：

```kotlin
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
```

文件末尾追加：

```kotlin
// iOS 无 APK 安装概念：更新移交发布页，由用户在 Safari 自行下载 IPA（design §4）。
actual fun supportsReleasePageHandoff(): Boolean = true

actual fun openReleasePage(url: String): Boolean =
    runCatching {
        val nsUrl = NSURL(string = url) ?: return@runCatching false
        UIApplication.sharedApplication.openURL(nsUrl)
    }.getOrDefault(false)
```

（`openURL` 的绑定形态与 `Sponsor.ios.kt` 已合入主干并编译通过的写法一致；https 链接不受 ATS 限制，Info.plist 无需新增。）

- **Step 4: 双门验证（Android 零回归确认点）**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: 全部 PASS。绑定名/可空性与本文不符时按编译器提示修正（全局约束「绑定名原则」），不改签名。

- **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/commonMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.kt shared/src/androidMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.android.kt shared/src/iosMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.ios.kt
git commit -m "feat: add release-page handoff expects with platform actuals (android no-op)"
```

---

### Task 2: iOS 扫码页（AVCaptureSession + UIKitView）【tasks 1.1 + 1.2 代码部分】
<!-- comet-task-ref:6dc264a4-d89f-4644-975d-4a703461cdb3 -->

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/ui/page/device/QrScannerPage.ios.kt`（整文件替换）
- Modify: `iosApp/iosApp/Info.plist`（+`NSCameraUsageDescription`）

**Interfaces:**
- Consumes: commonMain `@Composable expect fun QrScannerPage(onBack: () -> Unit, onResult: (String) -> Unit)`；调用方 `MainScaffold.kt:450` 的 `onResult = { raw -> viewModel.submitScannedRaw(raw); onBack(); ... }`（回填与导航由共享层负责，本页只回调原始文本）；miuix 组件 `TopAppBar`/`IconButton`/`MiuixIcons.Back`/`MiuixTheme`（Android 扫码页已在用，绑定已验证）。
- Produces: iOS 扫码页完整行为——相机栈 + `UIKitView` 预览、首帧命中即停会话并回主线程 `onResult`、权限状态机（未决定请求 / 已授权启动 / 被拒提示 + 「前往设置」）、无相机降级提示、退出停会话防泄漏。Task 3 消费其运行时行为做验证；Task 7 真机复验识别回填。

- **Step 1: 用以下内容整文件替换 `QrScannerPage.ios.kt`**

```kotlin
package com.github.ilife798.ui.page.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.github.ilife798.logDebug
import kotlin.concurrent.AtomicBoolean
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

// iOS 端扫码页（design §1）：AVCaptureSession（.high）+ back 广角相机 + MetadataOutput(.qr)，
// 预览经 AVCaptureVideoPreviewLayer 嵌入 Compose UIKitView。
// 权限：进入页面先查状态，未决定即请求；被拒给明确提示与「前往设置」；拒绝不启动 session。
// 防重复回调：MetadataOutput 后台队列回调，首帧命中即 stopRunning 并回主线程 onResult 一次。
// 生命周期：会话操作串行化到专用队列，页面退出（或权限状态切换）时 stopRunning，防相机泄漏。
@Composable
actual fun QrScannerPage(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    var authStatus by remember {
        mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(mediaType = AVMediaTypeVideo))
    }
    var cameraAvailable by remember { mutableStateOf(true) }

    val session = remember { AVCaptureSession() }
    val handled = remember { AtomicBoolean(false) }
    // 相机会话专用串行队列：start/stop 串行化，避免退出与启动竞态导致相机保持占用（design §7）
    val cameraQueue = remember { dispatch_queue_create("ilife798.qrscanner.session", null) }
    val metadataDelegate =
        remember(session, handled) {
            QrMetadataDelegate(
                session = session,
                handled = handled,
                onResult = { value -> currentOnResult(value) },
            )
        }

    // 权限未决定：进入页面即请求相机权限（design §1——首次实际需要时请求）。
    LaunchedEffect(Unit) {
        if (authStatus == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(mediaType = AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    authStatus =
                        if (granted) AVAuthorizationStatusAuthorized else AVAuthorizationStatusDenied
                }
            }
        }
    }

    // 已授权：串行队列配置并启动相机；权限状态变化或页面退出时停止会话（design §1）。
    DisposableEffect(authStatus) {
        if (authStatus == AVAuthorizationStatusAuthorized) {
            dispatch_async(cameraQueue) {
                startCameraSession(session, metadataDelegate) { available ->
                    dispatch_async(dispatch_get_main_queue()) { cameraAvailable = available }
                }
            }
        }
        onDispose {
            handled.value = true
            dispatch_async(cameraQueue) {
                runCatching { session.stopRunning() }
            }
        }
    }

    val denied =
        authStatus == AVAuthorizationStatusDenied || authStatus == AVAuthorizationStatusRestricted

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (authStatus == AVAuthorizationStatusAuthorized && cameraAvailable) {
            UIKitView(
                factory = { CameraPreviewView(session) },
                update = { preview -> preview.syncFrame() },
                onRelease = { preview -> preview.detach() },
                modifier = Modifier.fillMaxSize(),
            )
            // 中央取景框与提示文案（design §1 页面结构）
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(240.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp)),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "将设备二维码放入框内即可自动识别",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        } else {
            // 相机不可用：权限被拒/受限，或设备无摄像头（模拟器路径）——明确失败反馈，不崩溃
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (denied) "扫码需要相机权限，当前未授权" else "当前设备没有可用相机，无法扫码",
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.body2,
                )
                if (denied) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                                .clickable {
                                    // 前往系统设置开启相机权限（design §1）
                                    val settingsUrl = NSURL(string = UIApplication.openSettingsURLString)
                                    if (settingsUrl != null) {
                                        runCatching { UIApplication.sharedApplication.openURL(settingsUrl) }
                                    }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "前往设置", color = Color.White, style = MiuixTheme.textStyles.body2)
                    }
                }
            }
        }

        TopAppBar(
            title = "扫描二维码",
            modifier = Modifier.align(Alignment.TopCenter),
            color = Color.Transparent,
            largeTitleColor = Color.White,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = Color.White)
                }
            },
        )
    }
}

// 二维码识别回调：MetadataOutput 在后台队列派发；首帧命中即停会话并回主线程上报一次。
private class QrMetadataDelegate(
    private val session: AVCaptureSession,
    private val handled: AtomicBoolean,
    private val onResult: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (handled.value) return
        val value =
            didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstOrNull { it.stringValue != null }
                ?.stringValue
                ?: return
        if (!handled.compareAndSet(false, true)) return
        runCatching { session.stopRunning() }
        dispatch_async(dispatch_get_main_queue()) {
            onResult(value)
        }
    }
}

// 预览容器：AVCaptureVideoPreviewLayer 作为子层；布局变化与 update 阶段同步 frame（design §1）。
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        syncFrame()
    }

    fun syncFrame() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }

    fun detach() {
        previewLayer.removeFromSuperlayer()
        previewLayer.session = null
    }
}

// 会话配置与启动：必须在串行队列执行（startRunning 阻塞，design §1）。
// 设备缺失（模拟器）/输入输出挂载失败/异常 → 回调 false，页面降级提示；不向调用方抛异常。
private fun startCameraSession(
    session: AVCaptureSession,
    metadataDelegate: QrMetadataDelegate,
    onCameraAvailable: (Boolean) -> Unit,
) {
    try {
        if (session.inputs.isNotEmpty()) {
            // 已配置过（权限状态切换重入）：直接恢复运行
            session.startRunning()
            onCameraAvailable(true)
            return
        }
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh
        val device =
            AVCaptureDevice.defaultDeviceWithDeviceType(
                deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionBack,
            )
        val input =
            device?.let { AVCaptureDeviceInput.deviceInputWithDevice(device = it, error = null) }
        if (input == null || !session.canAddInput(input)) {
            session.commitConfiguration()
            onCameraAvailable(false)
            return
        }
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            session.commitConfiguration()
            onCameraAvailable(false)
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(
            metadataDelegate,
            queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u),
        )
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        session.commitConfiguration()
        session.startRunning()
        onCameraAvailable(true)
    } catch (e: Exception) {
        logDebug("QrScannerPage", "camera start failed: ${e.message}")
        onCameraAvailable(false)
    }
}
```

绑定名校正说明（全局约束「绑定名原则」，逐项按编译器提示处理，不改行为）：
- `authorizationStatusForMediaType` / `requestAccessForMediaType` / `defaultDeviceWithDeviceType` 按 ObjC selector 命名，若编译器给出 Swift 风格名（如 `authorizationStatus`）以提示为准。
- `AVCaptureDeviceInput.deviceInputWithDevice(device:error:)` 若绑定的是构造器形态（`AVCaptureDeviceInput(device, error = null)`），按提示改写，null 判断语义不变。
- `dispatch_queue_create` / `dispatch_get_global_queue` 返回值若被绑定标记为可空，在使用处补 `!!`。
- `CameraPreviewView.detach()` 中 `previewLayer.session = null` 若 `session` 属性绑定为非空，删除该行即可（会话已由 `DisposableEffect` 停止、层已移除）。
- `UIKitView` 若不在 `androidx.compose.ui.viewinterop`（CMP 1.12 应在此），按 IDE 提示改 import。

- **Step 2: 在 `iosApp/iosApp/Info.plist` 插入相机权限声明**

在 `<key>UIApplicationSupportsIndirectInputEvents</key><true/>` 与 `<key>UILaunchScreen</key>` 之间插入（缩进与文件一致用 Tab）：

```xml
	<key>NSCameraUsageDescription</key>
	<string>扫码添加设备需要使用相机</string>
```

- **Step 3: 双门验证**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: 全部 PASS。

- **Step 4: 模拟器构建、安装、启动，验证渲染与页面关闭路径（tasks 1.1 验证点）**

Run:
```bash
plutil -lint iosApp/iosApp/Info.plist
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build/sim-dd build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
xcrun simctl boot "iPhone 16" 2>/dev/null || true
xcrun simctl install booted iosApp/build/sim-dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.github.ilife798.iosApp
```
Expected: 构建与安装成功；进入「添加设备 → 扫一扫」：页面渲染出黑色背景 + 返回栏（首次弹相机权限弹窗，文案为 Info.plist 声明）；授权后因模拟器无摄像头显示「当前设备没有可用相机，无法扫码」，不崩溃；点返回可正常回退。

- **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain/kotlin/com/github/ilife798/ui/page/device/QrScannerPage.ios.kt iosApp/iosApp/Info.plist
git commit -m "feat: implement iOS qr scanner page with AVCaptureSession and UIKitView"
```

---

### Task 3: 扫码权限拒绝分支验证与证据记录【tasks 1.2 验证部分】
<!-- comet-task-ref:e37b111b-055a-42f9-98bb-863feb44ddbb -->

**Files:**
- Create: `docs/openspec/changes/ios-platform-features/verification-evidence.md`

**Interfaces:**
- Consumes: Task 2 构建出的 `.app` 与其运行时行为。
- Produces: 验证证据文件（本 change 后续任务共用）与扫码能力验证结论（tasks 1.2「验证权限拒绝分支不崩溃」）。

- **Step 1: 模拟器上验证权限拒绝分支（权限弹窗为系统 UI，需手动点击；沿用 Task 2 的 sim-dd 构建产物）**

操作序列与预期（逐项执行）：
1. 应用已在模拟器运行；`xcrun simctl privacy booted reset camera com.github.ilife798.iosApp` 清掉已授权状态（若该 service 在当前 Xcode 的 `xcrun simctl privacy --help` 列表中不支持，则卸载重装应用重置弹窗：`xcrun simctl uninstall booted com.github.ilife798.iosApp && xcrun simctl install booted iosApp/build/sim-dd/Build/Products/Debug-iphonesimulator/iosApp.app`）。
2. 进入「添加设备 → 扫一扫」→ 权限弹窗出现 → 点「不允许」。
3. Expected: 页面显示「扫码需要相机权限，当前未授权」+「前往设置」按钮，不崩溃。
4. 点「前往设置」→ Expected: 跳出应用进入系统设置页；手动返回应用 → 应用界面完好，返回栏可用。
5. 重新授权后再进入扫码页 → 显示「当前设备没有可用相机，无法扫码」（模拟器无摄像头路径，同时是 CI/模拟器不崩溃的钉子）。

- **Step 2: 创建证据文件并写入扫码结论**

`docs/openspec/changes/ios-platform-features/verification-evidence.md` 初始内容如下（表格「待验证」在各任务执行后改为「通过/失败 + 一句结论」；后续任务追加第 2-5 节）：

```markdown
# 验证证据（ios-platform-features）

> 对应 tasks.md 5.1「记录验证证据于 change 目录」。模拟器项在本地验证时填写；真机项在 Task 7（用户自签）填写。

## 1. 扫码（tasks 1.1 / 1.2）

| 验证点 | 环境 | 结果 | 备注 |
|---|---|---|---|
| 扫码页渲染 + 返回路径可回退 | 模拟器 | 待验证 | |
| 首次进入弹相机权限弹窗，文案 = NSCameraUsageDescription | 模拟器 | 待验证 | |
| 授权后无摄像头 → 「当前设备没有可用相机」提示，不崩溃 | 模拟器 | 待验证 | |
| 拒绝 → 明确提示 + 前往设置跳转 + 返回后应用完好 | 模拟器 | 待验证 | |
| 扫码识别回填设备编号、页面关闭、仅一次回填 | 真机 | 待验证（Task 7） | |

## 2. 运行通知（tasks 2.1）

（Task 4 填写）

## 3. 支付跳转（tasks 3.1）

（Task 5 填写）

## 4. 更新移交（tasks 4.1）

（Task 6 填写）

## 5. 收尾回归（tasks 5.1）

（Task 7 填写）
```

- **Step 3: 提交**

```bash
git add docs/openspec/changes/ios-platform-features/verification-evidence.md
git commit -m "docs: record iOS scanning permission verification evidence"
```

---

### Task 4: iOS 运行通知（UNUserNotificationCenter 单通知槽）【tasks 2.1】
<!-- comet-task-ref:c8cb6426-e32b-4260-ad9c-e2ef58c042cd -->

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/RunNotifications.ios.kt`（整文件替换）
- Modify: `iosApp/iosApp/iOSApp.swift`（整文件替换，追加 NotificationDelegate）

**Interfaces:**
- Consumes: commonMain `expect object RunNotifications`（`updateDevice(deviceId: String, deviceName: String, running: Boolean)` / `updateTask(gained: Int)` / `removeTask()`，调用方 `DeviceController`/`TaskController` 不变）；调用时序事实：`TaskController` 先 `requestNotificationPermission()`（iOS 上是 no-op）再 `updateTask(0)`，因此通知权限请求由本实现的投递路径自行发起（design §2「首次实际需要展示前请求」）。
- Produces: 设备状态 → 通知 id `ilife798.device`（running=true 发「设备 {name} 运行中」，false 时清除）；积分任务 → id `ilife798.task`（「积分任务已获得 N 分」，`removeTask()` 清除）；同 id `add` 覆盖刷新；未授权静默跳过。Swift 侧 `NotificationDelegate`（前台横幅）。

- **Step 1: 用以下内容整文件替换 `RunNotifications.ios.kt`**

```kotlin
package com.github.ilife798

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

// iOS 运行通知：UNUserNotificationCenter 单通知槽（design §2）。
// 设备状态 → id "ilife798.device"，积分任务 → id "ilife798.task"；add(request) 同 id 覆盖刷新。
// 权限在首次实际需要展示前请求（[.alert, .sound]），仅系统弹窗时机；拒绝/失败时回调 granted=false
// 静默跳过投递，任务执行不受影响（spec ios-notifications「未授权不影响功能」）。
actual object RunNotifications {
    private const val DEVICE_NOTIFICATION_ID = "ilife798.device"
    private const val TASK_NOTIFICATION_ID = "ilife798.task"

    actual fun updateDevice(
        deviceId: String,
        deviceName: String,
        running: Boolean,
    ) {
        if (deviceId.isBlank()) return
        if (running) {
            post(DEVICE_NOTIFICATION_ID, "设备 $deviceName 运行中")
        } else {
            remove(DEVICE_NOTIFICATION_ID)
        }
    }

    actual fun updateTask(gained: Int) {
        post(TASK_NOTIFICATION_ID, "积分任务已获得 ${gained.coerceAtLeast(0)} 分")
    }

    actual fun removeTask() {
        remove(TASK_NOTIFICATION_ID)
    }

    // 授权就绪才投递：未决定时系统弹窗、已授权时立即回调；清除类操作（remove）无需权限。
    private fun post(
        id: String,
        body: String,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            if (granted) {
                val content =
                    UNMutableNotificationContent().apply {
                        this.body = body
                        this.sound = UNNotificationSound.defaultSound()
                    }
                val request =
                    UNNotificationRequest.requestWithIdentifier(
                        identifier = id,
                        content = content,
                        trigger = null,
                    )
                center.add(request) { _ ->
                    // 同 id 覆盖刷新；投递错误静默（design §2）
                }
            }
        }
    }

    private fun remove(id: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }
}
```

（绑定名说明：`currentNotificationCenter()` / `requestAuthorizationWithOptions(_:completionHandler:)` / `requestWithIdentifier(identifier:content:trigger:)` / `defaultSound()` 按 ObjC selector 绑定，与编译器提示不符时按提示修正，不改通知 id 与文案。）

- **Step 2: 用以下内容整文件替换 `iOSApp.swift`（追加 NotificationDelegate 与挂载）**

```swift
import SwiftUI
import UserNotifications
import shared

// 前台通知展示代理（design §2）：willPresent 返回横幅+声音，使运行通知在前台也可见。
// 静态属性强持有，防止 delegate 被释放导致前台横幅不显示（design §7 风险）。
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // 启动时挂载通知 delegate（App init 早于任何 Compose UI 与通知投递）。
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { newPhase in
            // iOS 15 兼容：使用单参数 onChange（两参数版本需 iOS 17+）。
            switch newPhase {
            case .active:
                AppLifecycle.shared.notifyResumed()
            case .background:
                AppLifecycle.shared.notifyStopped()
            default:
                break
            }
        }
    }
}
```

- **Step 3: 双门 + Swift 工程门（iOSApp.swift 变更 Gradle 不覆盖）**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

Run:
```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build/sim-dd build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
```
Expected: PASS。

- **Step 4: 模拟器验证两条路径（需有效测试账号登录；权限弹窗手动点击）**

安装并启动（沿用 Task 2 命令），逐项验证：
1. 授权路径：首次启动设备任务 → 系统通知权限弹窗出现 → 点「允许」→ 通知中心/横幅出现「设备 {name} 运行中」；应用保持前台时（delegate 生效）横幅以 banner 形式展示；停止设备 → 对应通知消失。启动积分任务 → 「积分任务已获得 N 分」随进度刷新；任务结束 → 通知消失。
2. 拒绝路径（Review Focus #3 的钉子）：卸载重装后首次弹权限时点「不允许」→ 再次启动设备/积分任务 → 任务照常运行（任务日志正常滚动）、无通知展示、应用不崩溃。
3. 记录：将两路径结果写入 `verification-evidence.md` 第 2 节表格（「待验证」改为结论）。

- **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain/kotlin/com/github/ilife798/RunNotifications.ios.kt iosApp/iosApp/iOSApp.swift docs/openspec/changes/ios-platform-features/verification-evidence.md
git commit -m "feat: implement iOS run notifications via UNUserNotificationCenter"
```

---

### Task 5: iOS 支付跳转（支付宝 URL Scheme）【tasks 3.1】
<!-- comet-task-ref:9cf511bc-5ba2-4981-8eb8-0c086c52502b -->

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/pay/Alipay.ios.kt`（整文件替换）
- Modify: `iosApp/iosApp/Info.plist`（+`LSApplicationQueriesSchemes`）

**Interfaces:**
- Consumes: commonMain `expect suspend fun payWithAlipay(orderInfo: String): AlipayPayResult` 与 `data class AlipayPayResult(success: Boolean, message: String)`；调用方 `WalletBillController.submitRecharge`（结果 message 由 `BillPage` 经 `showToast` 展示——iOS Toast 静默，见 Review Focus #5，故本实现加 `logDebug` 供验证观测）；后端 `prepayAlipay` 返回的签名 orderInfo（与 Android SDK 同一份，零后端改动）。
- Produces: iOS 支付分支语义——未安装 → `AlipayPayResult(false, "未安装支付宝")`；已安装跳转成功 → `AlipayPayResult(false, "已跳转支付宝，支付结果确认中，请稍后刷新余额")`（语义对齐 Android 8000/6004 分支，design §3）；唤起失败/异常 → `AlipayPayResult(false, "支付失败：…")`。

- **Step 1: 用以下内容整文件替换 `Alipay.ios.kt`**

```kotlin
package com.github.ilife798.pay

import com.github.ilife798.logDebug
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// iOS 支付：支付宝 URL Scheme 跳转（design §3）。
// 复用后端给 Android SDK 的同一份签名 orderInfo，零后端改动；iOS 无法自动回查支付结果，
// 成功唤起时返回语义与 Android 8000/6004 分支一致（"支付结果确认中，请稍后刷新余额"）。
// 结果文案经 BillPage 的 showToast 展示——iOS Toast 当前为静默 no-op（Change 1 骨架边界），
// 故以 logDebug 记录分支供模拟器/真机验证观测（Review Focus #5）。
actual suspend fun payWithAlipay(orderInfo: String): AlipayPayResult {
    val application = UIApplication.sharedApplication
    val schemeUrl = NSURL(string = ALIPAY_SCHEME)
    // LSApplicationQueriesSchemes 已声明 alipays/alipay，canOpenURL 才可判安装（design §3）
    val installed =
        schemeUrl != null &&
            runCatching { application.canOpenURL(schemeUrl) }.getOrDefault(false)
    if (!installed) {
        logDebug("Alipay", "alipay not installed")
        return AlipayPayResult(success = false, message = "未安装支付宝")
    }
    // orderInfo 含 & = % + 等保留字符：按「非字母数字全量编码」保证 orderSuffix 完整不截断
    val encoded =
        (orderInfo as NSString)
            .stringByAddingPercentEncodingWithAllowedCharacters(
                NSCharacterSet.alphanumericCharacterSet.invertedSet(),
            ) ?: return AlipayPayResult(success = false, message = "支付失败：订单信息编码失败")
    val encodedText = encoded as String
    val jumpUrl =
        NSURL(string = "$ALIPAY_STARTAPP_URL?appId=20000125&orderSuffix=$encodedText")
            ?: return AlipayPayResult(success = false, message = "支付失败：跳转地址拼装失败")
    val opened = runCatching { application.openURL(jumpUrl) }.getOrDefault(false)
    return if (opened) {
        logDebug("Alipay", "jumped to alipay, result pending")
        AlipayPayResult(success = false, message = "已跳转支付宝，支付结果确认中，请稍后刷新余额")
    } else {
        logDebug("Alipay", "openURL failed")
        AlipayPayResult(success = false, message = "支付失败：无法唤起支付宝，请手动打开支付宝完成支付")
    }
}

private const val ALIPAY_SCHEME = "alipays://"
private const val ALIPAY_STARTAPP_URL = "alipays://platformapi/startapp"
```

（绑定名说明：`NSCharacterSet.alphanumericCharacterSet` 为类属性绑定，若编译器提示为函数调用形态改 `alphanumericCharacterSet()`；`stringByAddingPercentEncodingWithAllowedCharacters` 返回 `NSString?`，`(orderInfo as NSString)` 与 `(encoded as String)` 为 K/N 字符串桥接惯用写法；`canOpenURL`/`openURL` 与 `Sponsor.ios.kt` 主干写法一致。）

- **Step 2: 在 `iosApp/iosApp/Info.plist` 插入 scheme 查询声明**

在 Task 2 加入的 `NSCameraUsageDescription` 键值对之后插入（缩进用 Tab）：

```xml
	<key>LSApplicationQueriesSchemes</key>
	<array>
		<string>alipays</string>
		<string>alipay</string>
	</array>
```

- **Step 3: 双门验证**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- **Step 4: 模拟器验证未安装分支（需有效测试账号登录；模拟器无支付宝，恰好覆盖未安装路径）**

```bash
plutil -lint iosApp/iosApp/Info.plist
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build/sim-dd build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
xcrun simctl install booted iosApp/build/sim-dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch --console-pty booted com.github.ilife798.iosApp
```
在账单页发起充值（选择任一金额）：Expected: `payWithAlipay` 返回未安装分支——console 出现 `[Alipay] alipay not installed`，应用不崩溃、支付流程可安全重试（再次点击仍正常返回）。将结果写入 `verification-evidence.md` 第 3 节。

- **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain/kotlin/com/github/ilife798/pay/Alipay.ios.kt iosApp/iosApp/Info.plist docs/openspec/changes/ios-platform-features/verification-evidence.md
git commit -m "feat: implement iOS alipay url-scheme payment launch"
```

---

### Task 6: 更新移交（Releases 页跳转）【tasks 4.1】
<!-- comet-task-ref:d710cdbb-2ec0-406f-8eb1-cbe82c1dc9ec -->

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/github/ilife798/update/AppUpdate.kt`（object 内 + 常量）
- Modify: `shared/src/commonMain/kotlin/com/github/ilife798/update/UpdateController.kt`（3 处）

**Interfaces:**
- Consumes: Task 1 的 `supportsReleasePageHandoff(): Boolean` 与 `openReleasePage(url: String): Boolean`（同包，无需 import）；既有 `UpdateDialogState.Available(version)` 弹窗链路（`UpdateDialogs.kt` 的 Available → `viewModel.startUpdate()` 已存在，UI 零改动，design §4）；`UpdateController.checkForUpdate` 中 `asset == null` 的现状分支（`shared/src/commonMain/kotlin/com/github/ilife798/update/UpdateController.kt:69-73`）。
- Produces: iOS 检查更新发现新版本且无匹配资产 → `pendingReleasePage = true` 并弹 `Available(remoteVersion)`；确认更新 → `openReleasePage(RELEASES_PAGE_URL)` + 提示 + 关对话框；Android 路径逐字不变。

- **Step 1: 在 `AppUpdate.kt` 的 `object AppUpdate {` 内新增常量（置于首行成员位置）**

```kotlin
    // iOS 更新移交目标：项目发布页（design §4；https 不受 ATS 限制，Info.plist 无需新增）。
    const val RELEASES_PAGE_URL = "https://github.com/Jursin/ilife798/releases"
```

- **Step 2: 修改 `UpdateController.kt`（3 处）**

2a. 在 `private var pendingUpdateUrl = ""` 声明之后新增一行字段：

```kotlin
    private var pendingReleasePage = false
```

2b. `checkForUpdate` 中将：

```kotlin
                    val asset = AppUpdate.selectAsset(release, currentAbis())
                    if (asset == null || asset.browserDownloadUrl.isEmpty()) {
                        if (!silent) onToast("暂无本设备安装包")
                        return@launch
                    }
```

替换为：

```kotlin
                    val asset = AppUpdate.selectAsset(release, currentAbis())
                    if (asset == null || asset.browserDownloadUrl.isEmpty()) {
                        if (supportsReleasePageHandoff()) {
                            // iOS 无 APK 可装：移交发布页，复用既有 Available 弹窗（design §4）
                            pendingReleasePage = true
                            dialog = UpdateDialogState.Available(remoteVersion)
                        } else if (!silent) {
                            onToast("暂无本设备安装包")
                        }
                        return@launch
                    }
```

（Android 上 `supportsReleasePageHandoff()` 恒 `false`，else-if 与原 `if (!silent) onToast(...)` 语义逐字相同——Review Focus #1 的核对点。）

2c. `startUpdate` 开头将：

```kotlin
    fun startUpdate() {
        if (downloadingUpdate) return
        val url = pendingUpdateUrl
```

替换为：

```kotlin
    fun startUpdate() {
        if (downloadingUpdate) return
        if (pendingReleasePage) {
            // 移交路径：打开发布页后关闭对话框，不进入下载/安装流程（design §4）
            openReleasePage(AppUpdate.RELEASES_PAGE_URL)
            onToast("已打开发布页，请下载最新 IPA")
            pendingReleasePage = false
            dialog = UpdateDialogState.None
            return
        }
        val url = pendingUpdateUrl
```

（`pendingReleasePage` 无需在 dismiss/stop 路径重置：`startUpdate` 只能经 Available 弹窗的「更新」按钮到达，且每次 checkForUpdate 命中移交分支都会重设该标记。）

- **Step 3: 逐字 diff 核对 Android 路径（Review Focus #1 的钉子）**

核对 `git diff shared/src/commonMain/kotlin/com/github/ilife798/update/UpdateController.kt`：
Expected: 除上述 3 处外无其它改动；Android 分支（`else if (!silent) onToast("暂无本设备安装包")`）与原实现输出相同；下载、进度、安装、`onAppResumed`、`openInstallSettings` 等函数体零变化。

- **Step 4: 双门验证**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- **Step 5: 模拟器验证更新全流程（design §6：版本比较对 GitHub API 真实可用，跳转 Safari 打开 Releases 页）**

1. 强制走「发现新版本」路径：临时把 `iosApp/Configuration/Config.xcconfig` 的 `MARKETING_VERSION` 改为 `0.0.1`（本地验证用，不入库；`getAppVersion()` 读 CFBundleShortVersionString = 该值，任何线上 release 均大于它）。
2. 构建、安装、启动（命令同 Task 5 步骤 4，`plutil -lint` 不再需要）；进入「我的 → 检查更新」。
3. Expected: 对 GitHub API 的真实检查成功 → 弹出「检查到新版本 v…」对话框（既有 Available 弹窗）→ 点「更新」→ Safari 打开 `https://github.com/Jursin/ilife798/releases`（console 同时可见更新流程日志）；返回应用后对话框已关闭、无下载/安装类步骤可达、无崩溃。
4. 还原 `MARKETING_VERSION` 为原值（`git checkout -- iosApp/Configuration/Config.xcconfig`）。
5. 断网复验（替代路径）：关闭模拟器网络再检查更新 → 提示「检查更新失败」，不崩溃。
6. 记录：写入 `verification-evidence.md` 第 4 节。

- **Step 6: 提交**

```bash
./gradlew spotlessApply
git add shared/src/commonMain/kotlin/com/github/ilife798/update/AppUpdate.kt shared/src/commonMain/kotlin/com/github/ilife798/update/UpdateController.kt docs/openspec/changes/ios-platform-features/verification-evidence.md
git commit -m "feat: hand off iOS update flow to releases page"
```

---

### Task 7: 真机端到端验证与收尾回归【tasks 5.1】
<!-- comet-task-ref:d4826d18-1034-491d-86a7-c5bccb3357b1 -->

**Files:**
- Modify: `docs/openspec/changes/ios-platform-features/verification-evidence.md`（补全真机与回归结论）

**Interfaces:**
- Consumes: Task 1-6 全部改动；base-ref `f56e965`。
- Produces: 四能力真机端到端结论 + Android CI 全量回归结论 + 文件边界核对记录（tasks 5.1 收口）。

- **Step 1: iOS 真机（用户自签安装）端到端验证**

用户以自有证书自签安装（`iosApp/Configuration/Config.xcconfig` 填 `DEVELOPMENT_TEAM` 后经 Xcode 安装，或用既有未签名 IPA 重签）。逐项验证并记录（模拟器无法覆盖的项以真机为准）：
1. 扫码：对准有效二维码 → 识别内容回填设备编号流程、扫码页自动关闭，且仅回填一次（Review Focus #2）。
2. 通知：启动设备任务 → 前台横幅展示（delegate 生效）、退后台通知区可见；停止任务 → 通知清除。
3. 支付：真机已装支付宝发起真实充值 → 支付宝被唤起进入收银台（Review Focus #4：真实签名 orderInfo 跳转）；返回应用后按提示稍后刷新余额确认到账；若新版支付宝对 `orderSuffix` 跳转失败（design §7 风险 1），降级提示出现且不崩溃，把失败结论记入证据与 change 目录（后续换 universal link 的输入）。
4. 更新：检查更新 → 确认 → Safari 打开 Releases 页，可下载最新 IPA。

- **Step 2: Android CI 全量回归门**

Run: `./gradlew spotlessCheck :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: 全部 PASS（等价 android.yml validate 核心任务集）；推送分支后确认 PR 上 android.yml 与 ios.yml 双绿（`gh run watch`）。

- **Step 3: iOS 构建门与打包**

Run:
```bash
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --stacktrace
./iosApp/scripts/build-unsigned-ipa.sh
```
Expected: 编译与打包 PASS（沿用 ios.yml 链路）。

- **Step 4: 文件边界核对**

Run:
```bash
git diff --name-only f56e965...HEAD
git diff --name-only f56e965...HEAD | grep -c "^\.comet/" || echo 0
```
Expected: 文件清单仅落在「文件结构」小节列出的路径与计划/证据文档；`.comet/` 计数为 0；androidApp 与 `.github/workflows/android.yml` 不在清单中。结论记入证据文件第 5 节。

- **Step 5: 归档证据并提交**

```bash
git add docs/openspec/changes/ios-platform-features/verification-evidence.md
git commit -m "docs: record device verification evidence and final regression for ios-platform-features"
```

（若 Step 1-3 发现问题：回对应任务修复后重跑本任务全部步骤；修复单独提交。）

---

## 自查记录（Self-Review）

1. **Spec 覆盖**：ios-scanning 3 个 Scenario → Task 2（识别回填/无相机反馈/权限弹窗声明）+ Task 3（拒绝分支不崩溃）；ios-notifications 2 个 Scenario → Task 4（授权展示与清除 / 未授权不影响功能）；ios-payment-launch 2 个 Scenario → Task 5（跳转 + 结果确认语义 / 未安装明确失败）；ios-update-handoff 1 个 Scenario → Task 1 + Task 6（提示 + 跳转发布页，Android 步骤在 iOS 不可达且不报错）。design §5 工程增量（Info.plist 两项、iOSApp.swift、共享层扩展、权限声明同 PR）分别落 Task 2/4/5/6。tasks.md 6 项全部映射（见映射表）。proposal 的「不改动 Android 端与共享逻辑的行为」由 Task 1/6 的 actual 恒 false/no-op 与 Step 3 逐字 diff 核对保证。
2. **占位扫描**：无 TBD/TODO/「适当处理」类占位；所有代码步骤给出完整可提交内容；验证证据表格中的「待验证」是执行期结论栏位（与 Change 1 计划的冒烟清单同一形态），非实现占位。不确定的 platform 绑定均写明「以编译器提示为准修正、不改设计决策」并给出每个具体绑定点的校正说明（Task 2 步骤 1 末尾、Task 4 步骤 1 末尾、Task 5 步骤 1 末尾）。
3. **类型/命名一致性**：`supportsReleasePageHandoff`/`openReleasePage`（Task 1 产出 = Task 6 消费，签名逐字一致）；`AppUpdate.RELEASES_PAGE_URL`（Task 6 定义 = 同任务 `startUpdate` 消费）；`pendingReleasePage` 字段与两处分支（同文件）；通知 id `ilife798.device`/`ilife798.task` 与文案（Task 4 内部自洽，与 design §2 一致）；`QrMetadataDelegate(session, handled, onResult)`/`CameraPreviewView(session).syncFrame()/detach()`/`startCameraSession(session, delegate, onCameraAvailable)`（Task 2 文件内自洽）；`onResult` 回调签名 `(String) -> Unit` 与 commonMain expect 及 MainScaffold 调用方一致。
4. **Review Focus**：五类输入各有钉住步骤——#1 Task 1 步骤 4 + Task 6 步骤 3/4；#2 Task 2 步骤 1（compareAndSet + stopRunning）+ Task 7 步骤 1 第 1 项；#3 Task 4 步骤 4 第 2 项；#4 Task 5 步骤 1（全量非字母数字编码）+ Task 7 步骤 1 第 3 项；#5 Task 5 步骤 1（logDebug 观测 + 注释声明边界）与 Task 5/6 证据记录。
