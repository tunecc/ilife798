---
change: ios-packaging-foundation
design-doc: docs/superpowers/specs/2026-09-19-ios-packaging-foundation-design.md
base-ref: af15849e271391da91d578d3fe6090ccff5fcada
---

# iOS 打包构建基础（ios-packaging-foundation）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ILife798（Kotlin Multiplatform 项目）补齐 iOS 打包构建能力：shared 模块新增 iOS target 并提供分级 actual，新增 `iosApp/` Xcode 工程承载共享 Compose UI，产出未签名 IPA，并由 GitHub Actions（`ios.yml`）在 macOS runner 上自动验证构建与发布。

**Architecture:** 方案 A——官方 CMP 模板链路：SwiftUI 壳（`iosApp`）通过 Xcode Run Script 执行 `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 嵌入 Kotlin framework；`iosMain` 按设计分级实现（基础工具类真实现、重交互类安全 stub）；Gradle 任务 `generateIosBuildConfig` 生成 `IosBuildConfig` 常量注入网关配置；打包用 `xcodebuild` 无签名归档 + `Payload/` 组装 IPA（`CODE_SIGN_IDENTITY=""`）；CI 镜像 `android.yml` 双 job（validate / release），只做编译 + 打包，不做模拟器冒烟。

**Tech Stack:** Kotlin 2.4.20、Compose Multiplatform 1.12.0、AGP 9.4.0（`com.android.kotlin.multiplatform.library`）、Ktor 3.5.2（新增 `ktor-client-darwin`）、SwiftUI（iOS 15.0+）、xcodebuild/ditto、GitHub Actions（macos-latest）。

**Spec:** `docs/openspec/changes/ios-packaging-foundation/proposal.md`（canonical）+ `docs/superpowers/specs/2026-09-19-ios-packaging-foundation-design.md`（技术设计，delta spec：`docs/openspec/changes/ios-packaging-foundation/specs/ios-build/spec.md`、`specs/ios-ci/spec.md`）。计划与 spec 冲突时以 OpenSpec 文件为准。

## 全局约束（Global Constraints）

以下约束作用于每一个任务（数值原样取自 spec/design）：

- **版本冻结**：Kotlin 2.4.20 / CMP 1.12.0 / AGP 9.4.0 / Ktor 3.5.2 / miuix 0.9.4-rc01 / coil 3.6.2 / lifecycle 2.11.0，禁止升级；唯一新增依赖为 `io.ktor:ktor-client-darwin`（同 ktor 3.5.2）。
- **平台底线**：iOS 部署目标 `15.0`；bundle id `com.github.ilife798.iosApp`；framework 名 `shared`（动态框架）。
- **无签名**：全程不要求任何 Apple 签名身份；打包命令显式 `CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""`；xcconfig 中签名 TEAM 留空。
- **版本号一致性**：IPA 版本号取 androidApp `build.gradle.kts` 的 `versionName`（当前 `1.2.3`）；iOS 工程的 `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` 与其同步维护（当前 1.2.3 / 6）。
- **配置注入**：来源优先级 环境变量 `ILIFE798_API_GATEWAY`/`ILIFE798_SIGN_SALT`/`ILIFE798_API_CID` → 根目录 `secrets.properties` 的 `API_GATEWAY`/`SIGN_SALT`/`API_CID` → 空字符串；缺省时构建成功、应用按空网关处理；敏感值永不入库。
- **Android 零回归**：不改动 androidApp 构建配置与运行行为；每个改 Kotlin 的任务以 `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` 收尾。
- **CI 范围**：iOS CI 只做编译 + 打包；不引入 iOS 模拟器单元测试、不在 CI 做模拟器冒烟（模拟器验证全部留在本地，结论记录进 change 目录）。
- **不引入第三方 GitHub Action**：Xcode 用 runner 默认版本（漂移出问题再钉 `setup-xcode`）；action 版本沿用 android.yml 现值（checkout@v7 / setup-java@v6 / setup-android@v4 / setup-gradle@v6 / upload-artifact@v7）。
- **环境要求**：任务 1-16 中涉及 `./gradlew ...Ios...`、xcodebuild、模拟器的步骤只能在 macOS（Xcode 16+）上执行；其余 Gradle/JVM 步骤任意平台可执行。所有 Gradle 命令在仓库根目录执行。
- **代码风格**：新 Kotlin 文件提交前执行 `./gradlew spotlessApply`（ktlint 1.8.0 / ktlint_official，4 空格缩进 + 尾逗号）；iosMain 文件命名 `Xxx.ios.kt`（对应 androidMain 的 `Xxx.android.kt`）；NSUserDefaults key 统一加 `ilife798.` 前缀。
- **分支与基线**：从 base-ref `af15849e271391da91d578d3fe6090ccff5fcada` 之后的工作分支执行；每个任务单独提交，提交信息用约定式前缀（feat/docs/ci）。

## Review Focus

spec 暗示但任务测试未直接覆盖、最可能咬人的五类输入/条件（每行已在其归属任务中以步骤钉住）：

1. `secrets.properties` 或环境变量值含 `$`、`"`、`\` 等特殊字符时，生成代码不应损坏、构建不应失败 —— Task 9 步骤 4 的转义往返断言。
2. `ILIFE798_*` 环境变量与 `secrets.properties` 同时存在时，环境变量必须优先生效 —— Task 9 步骤 3 的对照断言。
3. 构建环境中不存在任何 Apple 签名证书与描述文件时，打包必须照常完成 —— Task 12 步骤 3 显式空签名身份验证；Task 14 在无证书的 CI runner 上复验等价行为。
4. iOS 用户触发扫码/支付/应用内更新/电池设置等 Android 专属入口时，应用必须不崩溃并给出「暂不支持」类反馈 —— Task 11 冒烟清单逐项断言。
5. 同一 tag 下 android/ios 两个工作流并发创建同一 GitHub Release 时，后到者必须增补资产而非失败 —— Task 15 的 `view || create` + create 失败回退 `upload --clobber` 步骤。

---

## 文件结构（本次改动落点）

```
gradle/libs.versions.toml                          ← 改：+ktor-client-darwin
shared/build.gradle.kts                            ← 改：+iOS target/framework/生成任务
shared/src/iosMain/kotlin/com/github/ilife798/     ← 新：17 个 actual 文件（34 个 expect 声明）+ MainViewController.kt
iosApp/                                            ← 新：Xcode 工程（官方 CMP 模板布局）
├── iosApp.xcodeproj/project.pbxproj
├── iosApp/iOSApp.swift / ContentView.swift / Info.plist / Assets.xcassets/
├── Configuration/Config.xcconfig
└── scripts/build-unsigned-ipa.sh
.github/workflows/ios.yml                          ← 新：validate + release 双 job
README.md                                          ← 改：+iOS 构建与自签说明
docs/openspec/changes/ios-packaging-foundation/    ← 新：expect-actual-checklist.md、simulator-smoke-checklist.md
```

不改动：`.github/workflows/android.yml`、androidApp 全部构建配置、`.comet/**` 工作流文件。

## 与 tasks.md 的映射与两处执行时点说明

| tasks.md | 本计划任务 |
|---|---|
| 1.1 | Task 1（spike 最前置） |
| 1.2 | Task 2 |
| 1.3 | Task 3 |
| 2.1 / 2.2 / 2.3 | Task 4 / 5 / 6 |
| 3.1 / 3.2 | Task 7 / 8 |
| 4.1 / 4.2 / 4.3 | Task 9 / 10 / 11 |
| 5.1 / 5.2 | Task 12 / 13 |
| 6.1 / 6.2 | Task 14 / 15 |
| 7.1 | Task 16 |

说明（按 design §6 的约束，属时点后移而非范围缩减）：
- tasks.md 2.1/2.3 的「模拟器验证写入读取恢复 / 登录请求可达后端」需要 iOS 应用壳存在（4.2 才建 `iosApp/`），且 design 明确不引入 iOS 单元测试，故统一并入 Task 11 的本地冒烟清单执行与记录；Task 4/6 各自在冒烟清单中登记对应验证项。
- `Sponsor` 在 design §2.1 列为真实现（`UIApplication.open`），tasks.md 将其归入组 3——本计划在 Task 7 按设计落地真实现。

---

### Task 1: shared 模块 iOS target（Spike，最前置）【tasks 1.1】

**Files:**
- Modify: `shared/build.gradle.kts`（kotlin 块内 + import 区）
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: 无（起点任务）。
- Produces: `shared` 模块两个编译目标 `iosArm64`、`iosSimulatorArm64`；名为 `shared` 的动态 framework（`binaries.framework { baseName = "shared" }`），供 Task 10 的 `import shared` 与 `embedAndSignAppleFrameworkForXcode` 消费；`libs.ktor.darwin` 版本目录条目，供 Task 2/6 的 iosMain 依赖。

- [x] **Step 1: 在 `gradle/libs.versions.toml` 的 `[libraries]` 末尾新增条目**

```toml
ktor-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
```

（Ktor iOS 原生引擎，与现有 `ktor-okhttp` 同版本 ref，不引入新版本。）

- [x] **Step 2: 在 `shared/build.gradle.kts` 的 `kotlin { }` 块内、`android { }` 块之后声明 iOS target 与 framework**

`kotlin { }` 块内追加：

```kotlin
iosArm64()
iosSimulatorArm64()

targets
    .withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>()
    .configureEach {
        binaries.framework {
            baseName = "shared"
        }
    }
```

`sourceSets { }` 块内（`androidMain.dependencies` 旁）追加：

```kotlin
iosMain.dependencies {
    implementation(libs.ktor.darwin)
}
```

注意：`targets.withType<...>` 用完整类名即可，不必新加 import；AGP 9.4 的 `androidMultiplatformLibrary` 插件与 iOS target 共存的 DSL 若与本文有出入（design §7 风险 2），以 Kotlin 2.4.20 编译器/IDE 提示为准校正 DSL 写法，**不改设计决策**（目标集合、framework 名、动态框架不变）。

- [x] **Step 3: 运行 iOS 编译 spike**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: 两个任务被正确创建并开始编译（首次会下载 Kotlin/Native 工具链，耐心等待）；编译**失败但所有错误均为 expect 缺 actual 类**（`Actual is missing` / `expect declaration ... has no corresponding actual` 等）。出现任何 DSL 解析错误、插件冲突错误、工具链错误都意味着 spike 未通过——按编译器提示修正 DSL 后重试；若 AGP 9.4 共存问题无法绕过，停止并回报（design §7 风险 1）。

- [x] **Step 4: 确认 Android 侧零影响**

Run: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS（产物与任务列表不变）。

- [x] **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: declare iOS targets and shared framework for KMP"
```

（此提交后 iOS 编译暂未通过属预期，Task 2 补齐 actual；Android 构建全程可编译。）

---

### Task 2: iosMain 分级 actual 骨架（34 个 expect / 17 个文件）【tasks 1.2】

**Files:**（全部新建，目录 `shared/src/iosMain/kotlin/com/github/ilife798/`）
- `PersistentStorage.ios.kt`、`Clipboard.ios.kt`、`TimeUtils.ios.kt`（在 `util/`）、`Version.ios.kt`、`Logger.ios.kt`、`ImageConversion.ios.kt`、`Toast.ios.kt`、`RunNotifications.ios.kt`、`pay/Alipay.ios.kt`、`update/AppUpdatePlatform.ios.kt`、`util/BatteryOptimization.ios.kt`、`util/Sponsor.ios.kt`、`data/api/HttpClientProvider.ios.kt`、`data/viewmodel/NetworkErrors.ios.kt`、`ui/theme/DynamicColorKey.ios.kt`、`ui/theme/WindowBlur.ios.kt`、`ui/page/device/QrScannerPage.ios.kt`

**Interfaces:**
- Consumes: Task 1 的 iOS targets 与 `libs.ktor.darwin`。
- Produces: commonMain 全部 34 个 expect 声明的可编译 actual（本任务为骨架，Task 4-7 逐个精修为最终实现）；后续任务依赖的签名与本文件逐字一致。

- [x] **Step 1: 新建 17 个骨架文件**（签名照抄 commonMain expect，逐字如下）

`PersistentStorage.ios.kt`（临时内存实现，Task 4 换 NSUserDefaults）：

```kotlin
package com.github.ilife798

// 骨架占位：Task 4（tasks 2.1）替换为 NSUserDefaults 真实现。
actual class PersistentStorage {
    private val memory = mutableMapOf<String, Any>()

    actual fun saveString(
        key: String,
        value: String,
    ) {
        memory[key] = value
    }

    actual fun getString(key: String): String? = memory[key] as? String

    actual fun saveBoolean(
        key: String,
        value: Boolean,
    ) {
        memory[key] = value
    }

    actual fun getBoolean(key: String): Boolean = memory[key] as? Boolean ?: false

    actual fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = memory[key] as? Boolean ?: defaultValue

    actual fun saveInt(
        key: String,
        value: Int,
    ) {
        memory[key] = value
    }

    actual fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = memory[key] as? Int ?: defaultValue
}
```

`Clipboard.ios.kt`：

```kotlin
package com.github.ilife798

// 骨架占位：Task 4（tasks 2.1）替换为 UIPasteboard 真实现。
actual fun copyTextToClipboard(text: String): Boolean = false
```

`util/TimeUtils.ios.kt`：

```kotlin
package com.github.ilife798.util

import platform.Foundation.NSDate

// 骨架占位：Task 5（tasks 2.2）替换为 NSDateFormatter 完整实现。
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentTimeFormatted(pattern: String): String = ""

actual fun formatTimestamp(
    timestamp: Long,
    pattern: String,
): String = ""

actual fun getDayOfWeek(): Int = 1

actual fun getTodayStart(now: Long): Long = now
```

`Version.ios.kt`：

```kotlin
package com.github.ilife798

// 骨架占位：Task 5（tasks 2.2）替换为 NSBundle 真实现。
actual fun getAppVersion(): String = "0"

actual fun getAppVersionCode(): String = "0"
```

`Logger.ios.kt`：

```kotlin
package com.github.ilife798

// 骨架占位：Task 5（tasks 2.2）替换为 NSLog 真实现。
actual fun logDebug(
    tag: String,
    message: String,
) {
    println("[$tag] $message")
}
```

`ImageConversion.ios.kt`：

```kotlin
package com.github.ilife798

import androidx.compose.ui.graphics.ImageBitmap

// 骨架占位：Task 5（tasks 2.2）替换为 Skia 解码真实现。
actual fun ByteArray.toImageBitmap(): ImageBitmap = ImageBitmap(1, 1)
```

`Toast.ios.kt`（最终形态，iOS 无系统 Toast 组件，按设计静默）：

```kotlin
package com.github.ilife798

// iOS 无系统 Toast 组件；按设计静默 no-op（Change 2 评估 HUD 替代）。
actual fun showToast(message: String) {}

actual fun dismissToast() {}
```

`RunNotifications.ios.kt`（最终形态）：

```kotlin
package com.github.ilife798

// iOS 无运行通知/前台服务；commonMain 注释已声明「其它平台为空实现」。
actual object RunNotifications {
    actual fun updateDevice(
        deviceId: String,
        deviceName: String,
        running: Boolean,
    ) {}

    actual fun updateTask(gained: Int) {}

    actual fun removeTask() {}
}
```

`pay/Alipay.ios.kt`（最终形态）：

```kotlin
package com.github.ilife798.pay

// iOS 端不支持应用内支付；Change 2 评估替代方案。
actual suspend fun payWithAlipay(orderInfo: String): AlipayPayResult =
    AlipayPayResult(success = false, message = "iOS 端暂不支持应用内支付")
```

`update/AppUpdatePlatform.ios.kt`（本文件骨架含 12 个 actual；`createUpdateHttpClient` 在 Task 6 补齐超时配置）：

```kotlin
package com.github.ilife798.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

// 更新检查用独立 HttpClient（Task 6 补齐与 Android 同等的超时语义）。
actual fun createUpdateHttpClient(): HttpClient = HttpClient(Darwin)

// iOS 端不支持 APK 安装/应用内更新；系列接口按设计提供安全空实现。
actual fun currentAbis(): List<String> = emptyList()

actual fun canInstallPackages(): Boolean = false

actual fun openInstallPermissionSettings() {}

actual fun installApk(filePath: String): Boolean = false

actual suspend fun downloadApkToFile(
    url: String,
    onProgress: (Float) -> Unit,
): String {
    onProgress(0f)
    return ""
}

actual suspend fun requestNotificationPermission() {}

actual fun showUpdateProgressNotification(progress: Float) {}

actual fun cancelUpdateProgressNotification() {}

actual fun deleteDownloadedApk() {}

actual fun downloadedApkPathIfValid(
    sha256: String?,
    size: Long,
): String? = null
```

`util/BatteryOptimization.ios.kt`（最终形态）：

```kotlin
package com.github.ilife798.util

// iOS 无电池优化限制概念；恒 true 避免误导性引导 UI（design §2.2）。
actual fun openBatteryOptimizationSettings() {}

actual fun isIgnoringBatteryOptimizations(): Boolean = true
```

`util/Sponsor.ios.kt`：

```kotlin
package com.github.ilife798.util

// 骨架占位：Task 7（tasks 3.1）替换为 UIApplication.open 真实现。
actual fun openSponsorPage(): Boolean = false
```

`data/api/HttpClientProvider.ios.kt`：

```kotlin
package com.github.ilife798.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

// 骨架占位：Task 6（tasks 2.3）补齐与 Android 同等的 JSON/请求头配置。
actual fun createHttpClient(): HttpClient = HttpClient(Darwin)
```

`data/viewmodel/NetworkErrors.ios.kt`：

```kotlin
package com.github.ilife798.data.viewmodel

// 骨架占位：Task 5（tasks 2.2）替换为 Darwin 错误码映射。
internal actual fun isTransientNetworkError(e: Throwable): Boolean = false
```

`ui/theme/DynamicColorKey.ios.kt`：

```kotlin
package com.github.ilife798.ui.theme

import androidx.compose.ui.graphics.Color

// 骨架占位：Task 5（tasks 2.2）补齐窗口 tintColor 读取；null 回退静态主题为设计允许路径。
actual fun systemDynamicColorKey(): Color? = null
```

`ui/theme/WindowBlur.ios.kt`（最终形态；commonMain 契约注释「其它平台不处理」）：

```kotlin
package com.github.ilife798.ui.theme

import androidx.compose.runtime.Composable

// iOS 无窗口级背景模糊 API；commonMain 契约注释「其它平台不处理」，直通 no-op。
@Composable
actual fun WindowBlurEffect(
    useBlur: Boolean,
    blurRadius: Int,
) {}
```

`ui/page/device/QrScannerPage.ios.kt`：

```kotlin
package com.github.ilife798.ui.page.device

import androidx.compose.runtime.Composable

// 骨架占位：Task 7（tasks 3.1）替换为带返回按钮的完整占位页。
@Composable
actual fun QrScannerPage(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {}
```

- [x] **Step 2: 运行 iOS 编译门（两架构）**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: PASS。若个别 platform.Foundation/UIKit 绑定名与记忆不符，按编译器提示修正 import（不改签名）。

- [x] **Step 3: Android 零回归门**

Run: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- [x] **Step 4: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain
git commit -m "feat: add tiered iosMain actual skeleton for all 34 expects"
```

---

### Task 3: Android 零回归验证门【tasks 1.3】

**Files:** 无新增/修改（纯验证任务）。

**Interfaces:**
- Consumes: Task 1/2 的全部改动。
- Produces: Android 构建与测试零回归的结论（写入任务备注）。

- [x] **Step 1: 执行完整 Android 门**

Run: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest :shared:spotlessCheck --stacktrace`
Expected: 全部 PASS。若有回归，用 `git stash` / 回滚定位到引入回归的任务并修复后重跑。

- [x] **Step 2: 记录结论**（无代码变更则无提交；若修了回归，单独提交修复）

---

### Task 4: PersistentStorage（NSUserDefaults）与 Clipboard（UIPasteboard）真实现【tasks 2.1】

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/PersistentStorage.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/Clipboard.ios.kt`

**Interfaces:**
- Consumes: commonMain `expect class PersistentStorage`（8 个方法签名见 Task 2 骨架）与 `expect fun copyTextToClipboard(text: String): Boolean`。
- Produces: iOS 持久化读写真实落盘（NSUserDefaults standard suite，key 前缀 `ilife798.`）、剪贴板复制；Task 11 冒烟依赖「登录态重启恢复」由本任务实现支撑。

- [x] **Step 1: 用以下内容替换 `PersistentStorage.ios.kt`**

```kotlin
package com.github.ilife798

import platform.Foundation.NSUserDefaults

// iOS 持久化：系统键值存储（标准 suite）。key 统一加前缀防与其它存储碰撞；
// 首次读取缺失返回 null/默认值；值域为 String/Boolean/Int 封装（design §2.1）。
actual class PersistentStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveString(
        key: String,
        value: String,
    ) {
        defaults.setObject(value, forKey = "$KEY_PREFIX$key")
    }

    actual fun getString(key: String): String? = defaults.stringForKey("$KEY_PREFIX$key")

    actual fun saveBoolean(
        key: String,
        value: Boolean,
    ) {
        defaults.setBool(value, forKey = "$KEY_PREFIX$key")
    }

    actual fun getBoolean(key: String): Boolean = defaults.boolForKey("$KEY_PREFIX$key")

    actual fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean =
        if (defaults.objectForKey("$KEY_PREFIX$key") == null) {
            defaultValue
        } else {
            defaults.boolForKey("$KEY_PREFIX$key")
        }

    actual fun saveInt(
        key: String,
        value: Int,
    ) {
        defaults.setInteger(value.toLong(), forKey = "$KEY_PREFIX$key")
    }

    actual fun getInt(
        key: String,
        defaultValue: Int,
    ): Int {
        // 本类只经 saveInt 写入；经字符串读取回避 NSNumber 绑定的 API 差异。
        val raw = defaults.stringForKey("$KEY_PREFIX$key") ?: return defaultValue
        return raw.toIntOrNull() ?: defaultValue
    }

    private companion object {
        const val KEY_PREFIX = "ilife798."
    }
}
```

- [x] **Step 2: 用以下内容替换 `Clipboard.ios.kt`**

```kotlin
package com.github.ilife798

import platform.UIKit.UIPasteboard

// iOS 剪贴板：UIPasteboard；写入失败/非字符串环境返回 false（design §2.1）。
actual fun copyTextToClipboard(text: String): Boolean =
    runCatching {
        UIPasteboard.generalPasteboard.string = text
        true
    }.getOrDefault(false)
```

- [x] **Step 3: 编译门**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: PASS（绑定名如有出入按编译器提示修正；不改 key 前缀与默认值语义）。

- [x] **Step 4: Android 零回归门**

Run: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- [x] **Step 5: 在冒烟清单登记验证项**

在 Task 11 将创建的 `docs/openspec/changes/ios-packaging-foundation/simulator-smoke-checklist.md` 待验列表中登记：「登录 → 杀掉应用重启 → 登录态与设置项恢复（NSUserDefaults 持久化）」与「设置页复制任意文本 → 粘贴板可见内容一致」。（运行时验证统一在 Task 11 执行，理由见前文映射说明。）

- [x] **Step 6: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain/kotlin/com/github/ilife798/PersistentStorage.ios.kt shared/src/iosMain/kotlin/com/github/ilife798/Clipboard.ios.kt
git commit -m "feat: implement iOS PersistentStorage via NSUserDefaults and Clipboard via UIPasteboard"
```

---

### Task 5: 基础工具类真实现（TimeUtils/Version/Logger/ImageConversion/NetworkErrors/DynamicColorKey）【tasks 2.2】

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/util/TimeUtils.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/Version.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/Logger.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/ImageConversion.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/data/viewmodel/NetworkErrors.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/ui/theme/DynamicColorKey.ios.kt`
- （`WindowBlur` 已在 Task 2 为最终 no-op 形态，无需改动）

**Interfaces:**
- Consumes: Task 2 骨架签名（逐字不变）。
- Produces: 与 Android 语义一致的时间/版本/日志/图片解码/错误分类/动态色读取；Task 11 冒烟依赖「页面时间显示正常、版本号正确、错误提示归类正确」。

- [x] **Step 1: 用以下内容替换 `util/TimeUtils.ios.kt`**

```kotlin
package com.github.ilife798.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

// iOS 时间工具：NSDateFormatter 默认使用设备本地时区与本地化设置，
// 与 Android SimpleDateFormat(Locale.getDefault()) 行为对齐（design §2.1）。
private fun newFormatter(pattern: String): NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = pattern
    }

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentTimeFormatted(pattern: String): String = newFormatter(pattern).stringFromDate(NSDate())

actual fun formatTimestamp(
    timestamp: Long,
    pattern: String,
): String = newFormatter(pattern).stringFromDate(NSDate(timeIntervalSince1970 = timestamp / 1000.0))

// Android 端语义：Calendar.DAY_OF_WEEK(周日=1) 转 ISO 周几（周一=1..周日=7）；Unicode "u" 与该语义一致。
actual fun getDayOfWeek(): Int = newFormatter("u").stringFromDate(NSDate()).toIntOrNull() ?: 1

actual fun getTodayStart(now: Long): Long {
    val day = newFormatter("yyyy-MM-dd").stringFromDate(NSDate(timeIntervalSince1970 = now / 1000.0))
    val midnight = newFormatter("yyyy-MM-dd HH:mm:ss").dateFromString("$day 00:00:00") ?: return now
    return (midnight.timeIntervalSince1970 * 1000).toLong()
}
```

- [x] **Step 2: 用以下内容替换 `Version.ios.kt`**

```kotlin
package com.github.ilife798

import platform.Foundation.NSBundle

// 版本号来自 App 包信息；缺失返回 "0"（design §2.1）。
actual fun getAppVersion(): String =
    NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "0"

actual fun getAppVersionCode(): String =
    NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String ?: "0"
```

- [x] **Step 3: 用以下内容替换 `Logger.ios.kt`**

```kotlin
package com.github.ilife798

import platform.Foundation.NSLog

// 日志：NSLog（含 tag 前缀）；走 %@ 占位避免消息内 % 被当作格式符。
actual fun logDebug(
    tag: String,
    message: String,
) {
    NSLog("%@", "[$tag] $message")
}
```

- [x] **Step 4: 用以下内容替换 `ImageConversion.ios.kt`**

```kotlin
package com.github.ilife798

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Image

// 图片解码：Skia；解码失败返回占位空 bitmap，不抛异常（design §2.1）。
actual fun ByteArray.toImageBitmap(): ImageBitmap =
    runCatching {
        Image.makeFromEncoded(this).asComposeImageBitmap()
    }.getOrElse {
        ImageBitmap(1, 1)
    }
```

- [x] **Step 5: 用以下内容替换 `data/viewmodel/NetworkErrors.ios.kt`**

```kotlin
package com.github.ilife798.data.viewmodel

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorResourceUnavailable
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorTimedOut

// 与 Android 分类对齐：DNS/连接失败、超时、断网视为可重试；
// SSL/TLS 错误不重试；未知异常归通用失败（design §2.1）。
internal actual fun isTransientNetworkError(e: Throwable): Boolean =
    when (e) {
        is ConnectTimeoutException, is SocketTimeoutException -> true
        is DarwinHttpRequestException -> e.origin.isTransientUrlError()
        else -> e.cause?.let { cause -> cause !== e && isTransientNetworkError(cause) } ?: false
    }

private fun NSError.isTransientUrlError(): Boolean {
    if (domain != NSURLErrorDomain) return false
    return when (code) {
        NSURLErrorTimedOut,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorResourceUnavailable,
        NSURLErrorNotConnectedToInternet,
        NSURLErrorNetworkConnectionLost,
        -> true
        NSURLErrorSecureConnectionFailed,
        NSURLErrorServerCertificateHasBadDate,
        NSURLErrorServerCertificateUntrusted,
        -> false
        else -> false
    }
}
```

- [x] **Step 6: 用以下内容替换 `ui/theme/DynamicColorKey.ios.kt`**

```kotlin
package com.github.ilife798.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSURL
import platform.UIKit.CGColorCopyAlpha
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene

// iOS 不公开用户全局强调色 API（Material You 无对应物）。
// 尝试读取当前前台 key window 的 tintColor（跟随系统强调色设置），
// 不可用/未就绪时返回 null 走静态主题——这是设计允许的回退路径（design §2.1）。
@kotlinx.cinterop.ExperimentalForeignApi
actual fun systemDynamicColorKey(): Color? =
    runCatching {
        val window =
            UIApplication.sharedApplication.connectedScenes.allObjects
                .filterIsInstance<UIWindowScene>()
                .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
                ?.windows
                ?.firstOrNull { it.isKeyWindow }
                ?: return null
        val tint: UIColor = window.tintColor ?: return null
        memScoped {
            val r = alloc<platform.UIKit.CGFloatVar>()
            val g = alloc<platform.UIKit.CGFloatVar>()
            val b = alloc<platform.UIKit.CGFloatVar>()
            val a = alloc<platform.UIKit.CGFloatVar>()
            if (!tint.getRed(r.ptr, g.ptr, b.ptr, a.ptr)) return null
            Color(
                red = r.value.toFloat(),
                green = g.value.toFloat(),
                blue = b.value.toFloat(),
                alpha = a.value.toFloat(),
            )
        }
    }.getOrNull()

// 供编译器消歧的显式类型引用（如与实际绑定不符，按编译器提示删除/修正本行与上方 import）。
@Suppress("unused")
private val iosColorRef: NSURL? = null

private typealias CGColorCopyAlphaAlias = CGColorCopyAlpha
```

注意：上例末尾的 `iosColorRef`/`typealias` 两行**仅为占位说明，实际提交时必须删除**——它们的目的是提醒执行者：`CGFloatVar`/`getRed` 的确切绑定名（`platform.UIKit` 与 `kotlinx.cinterop` 之间）以编译器提示为准修正，若绑定摩擦过大，允许将实现退化为恒 `return null`（设计明确允许的回退路径），并把该决定记录进 Task 11 冒烟结论。最终提交文件中**不得残留**这两行。

- [x] **Step 7: 编译门 + Android 门**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: 全部 PASS。

- [x] **Step 8: 在冒烟清单登记验证项**

向 Task 11 的冒烟清单登记：「任务/账单页时间文案与 Android 显示一致；设置/关于页版本号显示 1.2.3；断网触发接口错误时提示分类与 Android 一致；图片（若有远程图）展示正常或占位不崩」。

- [x] **Step 9: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain
git commit -m "feat: implement iOS base utilities (time/version/log/image/network-error/dynamic-color)"
```

---

### Task 6: HttpClientProvider 与 createUpdateHttpClient（Ktor Darwin）【tasks 2.3】

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/data/api/HttpClientProvider.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/update/AppUpdatePlatform.ios.kt`（仅 `createUpdateHttpClient` 一个函数）

**Interfaces:**
- Consumes: Task 1 的 `libs.ktor.darwin`；commonMain `ApiConfig.USER_AGENT`/`VERSION_CODE`。
- Produces: 与 Android OkHttp 实现同语义的业务 HTTP 客户端（JSON 配置 + 默认请求头）与更新检查客户端（60s 请求超时）；Task 11 冒烟依赖「模拟器登录请求可达后端」。

- [x] **Step 1: 用以下内容替换 `data/api/HttpClientProvider.ios.kt`**

```kotlin
package com.github.ilife798.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// 与 Android OkHttp 实现保持同等语义：同一 JSON 配置与默认请求头；引擎为 iOS 原生 Darwin（design §2.1）。
actual fun createHttpClient(): HttpClient =
    HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            header("Accept-Language", "zh-Hans-CN;q=1")
            header("User-Agent", ApiConfig.USER_AGENT)
            header("VersionCode", ApiConfig.VERSION_CODE)
        }
    }
```

- [x] **Step 2: 在 `update/AppUpdatePlatform.ios.kt` 中将 `createUpdateHttpClient` 替换为**

```kotlin
actual fun createUpdateHttpClient(): HttpClient =
    HttpClient(Darwin) {
        install(HttpTimeout) {
            // 与 Android 端 requestTimeoutMillis=60s 同语义；
            // Darwin 引擎不支持独立的 connect/socket 超时（design §2.1 边界）。
            requestTimeoutMillis = 60_000
        }
    }
```

（同时在文件顶部补 import：`io.ktor.client.plugins.HttpTimeout`。）

- [x] **Step 3: 编译门 + Android 门**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- [x] **Step 4: 在冒烟清单登记验证项**

向 Task 11 的冒烟清单登记：「填入有效账号登录 → 登录成功（Darwin 引擎请求可达后端）；断网登录 → 呈现网络错误文案而非崩溃」。

- [x] **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain
git commit -m "feat: implement iOS HTTP clients with Ktor Darwin engine"
```

---

### Task 7: 重交互 stub 精修（QrScannerPage 占位页 + Sponsor 真实现）【tasks 3.1】

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/ui/page/device/QrScannerPage.ios.kt`
- Modify: `shared/src/iosMain/kotlin/com/github/ilife798/util/Sponsor.ios.kt`
- 复核（不改代码，逐项对照 design §2.2）: `RunNotifications.ios.kt`、`pay/Alipay.ios.kt`、`update/AppUpdatePlatform.ios.kt`、`util/BatteryOptimization.ios.kt`、`Toast.ios.kt`（均已是最终形态）

**Interfaces:**
- Consumes: commonMain `SPONSOR_URL`（`com.github.ilife798.util`，值 `https://afdian.com/a/jursin`）、miuix 组件（`top.yukonga.miuix.kmp.basic.Text/Button`、`MiuixTheme`）。
- Produces: 扫码占位页（明确提示 + 返回按钮，导航不悬空）、Sponsor 真实现；Task 11 冒烟的 stub 入口断言全部由本任务后的代码状态支撑。

- [x] **Step 1: 用以下内容替换 `ui/page/device/QrScannerPage.ios.kt`**

```kotlin
package com.github.ilife798.ui.page.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// iOS 端扫码占位页：明确提示暂不支持并保持导航可用（返回按钮不悬空，design §2.2）。
@Composable
actual fun QrScannerPage(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "iOS 端扫码暂不支持，请手动输入设备编号",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "返回",
                color = Color.White,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
```

（若 `MiuixTheme.colorScheme.primary`/`textStyles.body2` 字段名与实际不符，按 IDE 提示改用 `MiuixTheme` 中等价字段；不引入新依赖。）

- [x] **Step 2: 用以下内容替换 `util/Sponsor.ios.kt`**

```kotlin
package com.github.ilife798.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// 打开赞助页：UIApplication.open；URL 为 commonMain 常量 SPONSOR_URL（design §2.1）。
actual fun openSponsorPage(): Boolean {
    val url = NSURL(string = SPONSOR_URL) ?: return false
    return runCatching { UIApplication.sharedApplication.open(url) }.getOrDefault(false)
}
```

- [x] **Step 3: 编译门 + Android 门**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS。

- [x] **Step 4: 在冒烟清单登记验证项**

登记：「扫码入口 → 占位页出现 + 点返回可回退；支付入口 → 弹出『iOS 端暂不支持应用内支付』；更新入口 → 无崩溃（安装/下载类操作空转）；电池优化设置入口 → 无跳转无崩溃；赞助入口 → Safari 打开 afdian 链接；Toast 场景静默不崩」。

- [x] **Step 5: 提交**

```bash
./gradlew spotlessApply
git add shared/src/iosMain
git commit -m "feat: add iOS qr-scanner placeholder page and sponsor link opener"
```

---

### Task 8: expect/actual 覆盖完整性核对清单【tasks 3.2】

**Files:**
- Create: `docs/openspec/changes/ios-packaging-foundation/expect-actual-checklist.md`

**Interfaces:**
- Consumes: Task 1-7 的 iosMain 全部文件；commonMain 17 个含 expect 的文件。
- Produces: 覆盖核对清单文档（17 文件 / 34 声明全表）；后续 PR review 依据。

- [x] **Step 1: 用编译器做权威核对**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: PASS（iOS 编译通过 = 每个 expect 均有 actual，这是权威判定）。

Run: `grep -rn "expect " shared/src/commonMain --include="*.kt" | wc -l && grep -rln "actual" shared/src/iosMain --include="*.kt" | wc -l`
Expected: `34` 与 `17`。

- [x] **Step 2: 写入核对清单 `expect-actual-checklist.md`**（内容照抄下表）

```markdown
# expect/actual 覆盖核对清单（ios-packaging-foundation）

核对时间：执行时填写。核对基准：`:shared:compileKotlinIosArm64` 与 `:shared:compileKotlinIosSimulatorArm64` 编译通过 + 本表逐项人工核对。

| # | commonMain expect 文件 | 声明数 | iosMain actual 文件 | 分级 |
|---|---|---|---|---|
| 1 | PersistentStorage.kt | 1 | PersistentStorage.ios.kt | 真实现（NSUserDefaults） |
| 2 | Clipboard.kt | 1 | Clipboard.ios.kt | 真实现（UIPasteboard） |
| 3 | util/TimeUtils.kt | 5 | util/TimeUtils.ios.kt | 真实现（NSDateFormatter） |
| 4 | Version.kt | 2 | Version.ios.kt | 真实现（NSBundle） |
| 5 | Logger.kt | 1 | Logger.ios.kt | 真实现（NSLog） |
| 6 | ImageConversion.kt | 1 | ImageConversion.ios.kt | 真实现（Skia） |
| 7 | data/api/HttpClientProvider.kt | 1 | data/api/HttpClientProvider.ios.kt | 真实现（Ktor Darwin） |
| 8 | update/AppUpdatePlatform.kt | 12 | update/AppUpdatePlatform.ios.kt | 真实现（createUpdateHttpClient/HttpTimeout）+ stub（其余 11） |
| 9 | data/viewmodel/NetworkErrors.kt | 1 | data/viewmodel/NetworkErrors.ios.kt | 真实现（NSError 映射） |
| 10 | ui/theme/DynamicColorKey.kt | 1 | ui/theme/DynamicColorKey.ios.kt | 真实现（window tint；null 回退） |
| 11 | ui/theme/WindowBlur.kt | 1 | ui/theme/WindowBlur.ios.kt | 真实现（契约 no-op） |
| 12 | util/Sponsor.kt | 1 | util/Sponsor.ios.kt | 真实现（UIApplication.open） |
| 13 | ui/page/device/QrScannerPage.kt | 1 | ui/page/device/QrScannerPage.ios.kt | stub（占位页） |
| 14 | RunNotifications.kt | 1 | RunNotifications.ios.kt | stub（空对象） |
| 15 | pay/Alipay.kt | 1 | pay/Alipay.ios.kt | stub（失败结果） |
| 16 | Toast.kt | 2 | Toast.ios.kt | stub（静默） |
| 17 | util/BatteryOptimization.kt | 2 | util/BatteryOptimization.ios.kt | stub（恒 true + no-op） |

合计：17 文件 / 34 声明。Change 2（ios-platform-features）待替换项：#8 stub 部分、#13-#17。
```

- [x] **Step 3: 提交**

```bash
git add docs/openspec/changes/ios-packaging-foundation/expect-actual-checklist.md
git commit -m "docs: record expect/actual coverage checklist for iosMain"
```

---

### Task 9: Gradle 配置注入 generateIosBuildConfig【tasks 4.1】

**Files:**
- Modify: `shared/build.gradle.kts`
- （.gitignore 无需改动：根 `.gitignore` 已有 `build/` 规则覆盖生成目录，见 Step 5 验证）

**Interfaces:**
- Consumes: 根目录 `secrets.properties`（可选存在）与 `ILIFE798_*` 环境变量。
- Produces: Gradle 任务 `generateIosBuildConfig`；生成 `shared/build/generated/iosBuildConfig/kotlin/com/github/ilife798/buildConfig/IosBuildConfig.kt`，其中 `object IosBuildConfig { const val API_GATEWAY: String; const val SIGN_SALT: String; const val API_CID: String }`，仅注册进 iosMain sourceSet——Task 10 的 `MainViewController.kt` 依赖该 object 的这三个常量名。

- [x] **Step 1: 在 `shared/build.gradle.kts` 文件头部（plugins 之前）添加 import，并在 `kotlin { }` 块之前添加生成任务**

文件头新增：

```kotlin
import java.util.Properties
```

`kotlin { }` 块之前新增（与 aboutLibraries 配置平级）：

```kotlin
// —— iOS 构建期常量注入（与 Android secrets.properties 同源；缺省空字符串）——
// 来源优先级：环境变量 ILIFE798_* → 根目录 secrets.properties → 空字符串（design §3）。
fun resolveIosBuildConfig(
    envKey: String,
    propKey: String,
): String {
    System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        val props = Properties()
        secretsFile.inputStream().use { props.load(it) }
        props.getProperty(propKey)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

// Kotlin 字符串字面量转义：保证网关/盐值中的 $、"、\ 不破坏生成代码。
fun escapeKotlinStringLiteral(raw: String): String =
    raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")

val iosApiGateway = resolveIosBuildConfig("ILIFE798_API_GATEWAY", "API_GATEWAY")
val iosSignSalt = resolveIosBuildConfig("ILIFE798_SIGN_SALT", "SIGN_SALT")
val iosApiCid = resolveIosBuildConfig("ILIFE798_API_CID", "API_CID")

val generateIosBuildConfig =
    tasks.register("generateIosBuildConfig") {
        val generatedDir = layout.buildDirectory.dir("generated/iosBuildConfig/kotlin")
        val apiGateway = iosApiGateway
        val signSalt = iosSignSalt
        val apiCid = iosApiCid
        inputs.property("apiGateway", apiGateway)
        inputs.property("signSalt", signSalt)
        inputs.property("apiCid", apiCid)
        outputs.dir(generatedDir)
        doLast {
            val packageDir = generatedDir.get().dir("com/github/ilife798/buildConfig").asFile
            packageDir.mkdirs()
            packageDir.resolve("IosBuildConfig.kt").writeText(
                """
                |package com.github.ilife798.buildConfig
                |
                |// 由 Gradle 任务 generateIosBuildConfig 生成，勿手动修改，不入库。
                |object IosBuildConfig {
                |    const val API_GATEWAY: String = "${escapeKotlinStringLiteral(apiGateway)}"
                |    const val SIGN_SALT: String = "${escapeKotlinStringLiteral(signSalt)}"
                |    const val API_CID: String = "${escapeKotlinStringLiteral(apiCid)}"
                |}
                """.trimMargin(),
            )
        }
    }
```

- [x] **Step 2: 将生成目录注册进 iosMain sourceSet，并挂接 iOS 编译任务依赖**

`kotlin { sourceSets { } }` 块内追加：

```kotlin
iosMain {
    kotlin.srcDir(layout.buildDirectory.dir("generated/iosBuildConfig/kotlin"))
}
```

`kotlin { }` 块之后追加：

```kotlin
// 保证增量正确性：iOS 编译任务依赖生成任务（design §3）。
tasks
    .withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>()
    .configureEach {
        if (name.contains("Ios")) dependsOn(generateIosBuildConfig)
    }
```

- [x] **Step 3: 验证注入矩阵（三种来源场景）**

Run（场景 A，有 secrets.properties）:
```bash
printf 'API_GATEWAY=https://gw.example.com\nSIGN_SALT=salt-abc\nAPI_CID=ilife798\n' > secrets.properties
./gradlew :shared:generateIosBuildConfig
cat shared/build/generated/iosBuildConfig/kotlin/com/github/ilife798/buildConfig/IosBuildConfig.kt
```
Expected: 三个常量值与文件一致。

Run（场景 B，环境变量覆盖）:
```bash
ILIFE798_API_GATEWAY=https://env-override.example.com ./gradlew :shared:generateIosBuildConfig --rerun-tasks
grep API_GATEWAY shared/build/generated/iosBuildConfig/kotlin/com/github/ilife798/buildConfig/IosBuildConfig.kt
```
Expected: 值为 `https://env-override.example.com`（环境变量优先于 secrets.properties——Review Focus #2 的钉子）。

Run（场景 C，无任何来源）:
```bash
rm secrets.properties && unset ILIFE798_API_GATEWAY ILIFE798_SIGN_SALT ILIFE798_API_CID
./gradlew :shared:generateIosBuildConfig :shared:compileKotlinIosSimulatorArm64 --stacktrace
```
Expected: 构建成功，生成文件三个常量均为 `""`（与 Android 缺省行为一致）。

- [x] **Step 4: 特殊字符转义往返验证（Review Focus #1 的钉子）**

Run:
```bash
printf 'API_GATEWAY=https://a$b"c\\d\n' > secrets.properties
./gradlew :shared:generateIosBuildConfig :shared:compileKotlinIosSimulatorArm64 --stacktrace
```
Expected: 编译 PASS（生成代码中该值以 `\$`、`\"`、`\\` 形式安全转义）。验证后还原 `rm secrets.properties`。

- [x] **Step 5: 确认生成目录不入库 + Android 门**

Run: `git check-ignore -v shared/build/generated/iosBuildConfig/kotlin/com/github/ilife798/buildConfig/IosBuildConfig.kt`
Expected: 命中根 `.gitignore` 的 `build/` 规则（design §3 的「加入 .gitignore」已由既有规则满足，无需改 .gitignore）。

Run: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest --stacktrace`
Expected: PASS（注入只影响 iosMain）。

- [x] **Step 6: 提交**

```bash
./gradlew spotlessApply
git add shared/build.gradle.kts
git commit -m "feat: generate IosBuildConfig constants from secrets.properties or env"
```

---

### Task 10: iosApp Xcode 工程与 MainViewController【tasks 4.2】

**Files:**
- Create: `shared/src/iosMain/kotlin/com/github/ilife798/MainViewController.kt`
- Create: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Create: `iosApp/iosApp/iOSApp.swift`
- Create: `iosApp/iosApp/ContentView.swift`
- Create: `iosApp/iosApp/Info.plist`
- Create: `iosApp/iosApp/Assets.xcassets/Contents.json`
- Create: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`
- Create: `iosApp/Configuration/Config.xcconfig`

**Interfaces:**
- Consumes: Task 1 的 framework `shared` 与 `embedAndSignAppleFrameworkForXcode` 任务；Task 9 的 `IosBuildConfig.API_GATEWAY/SIGN_SALT/API_CID`；commonMain `App()`、`AppLifecycle.notifyResumed()/notifyStopped()`。
- Produces: 可被 xcodebuild 构建的 iOS 应用工程（scheme `iosApp`，bundle id `com.github.ilife798.iosApp`，部署目标 15.0，无签名构建）；Task 12 打包脚本与 Task 14 CI 依赖该工程的路径与 scheme 名。

- [x] **Step 1: 新建 `shared/src/iosMain/kotlin/com/github/ilife798/MainViewController.kt`**（对齐 MainActivity.onCreate 初始化序列，design §2.3）

```kotlin
package com.github.ilife798

import androidx.compose.ui.window.ComposeUIViewController
import com.github.ilife798.buildConfig.IosBuildConfig
import com.github.ilife798.data.api.ApiConfig
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    AppStorage.instance = PersistentStorage()
    ApiConfig.init(
        gateway = IosBuildConfig.API_GATEWAY,
        salt = IosBuildConfig.SIGN_SALT,
        clientId = IosBuildConfig.API_CID,
    )
    return ComposeUIViewController { App() }
}
```

（`DeviceTile.controller` 不设置——commonMain 注释已定义「未设置 = 不支持」语义。）

- [x] **Step 2: 新建 `iosApp/Configuration/Config.xcconfig`**

```
// iOS 应用构建配置。签名团队留空：本 change 产出未签名构建；用户自行签名时在此填写 DEVELOPMENT_TEAM。
BUNDLE_ID = com.github.ilife798.iosApp
DEPLOYMENT_TARGET = 15.0
// 与 androidApp versionName/versionCode 保持同步（更新版本时两处一起改）。
MARKETING_VERSION = 1.2.3
CURRENT_PROJECT_VERSION = 6
DEVELOPMENT_TEAM =
```

- [x] **Step 3: 新建 `iosApp/iosApp/iOSApp.swift`**

```swift
import SwiftUI
import shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

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

- [x] **Step 4: 新建 `iosApp/iosApp/ContentView.swift`**

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

- [x] **Step 5: 新建 `iosApp/iosApp/Info.plist`**（基础声明；相机权限归 Change 2，不在此添加）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>$(DEVELOPMENT_LANGUAGE)</string>
	<key>CFBundleDisplayName</key>
	<string>ILife798</string>
	<key>CFBundleExecutable</key>
	<string>$(EXECUTABLE_NAME)</string>
	<key>CFBundleIdentifier</key>
	<string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>$(PRODUCT_NAME)</string>
	<key>CFBundlePackageType</key>
	<string>$(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
	<key>CFBundleShortVersionString</key>
	<string>$(MARKETING_VERSION)</string>
	<key>CFBundleVersion</key>
	<string>$(CURRENT_PROJECT_VERSION)</string>
	<key>LSRequiresIPhoneOS</key>
	<true/>
	<key>UIApplicationSupportsIndirectInputEvents</key>
	<true/>
	<key>UILaunchScreen</key>
	<dict/>
	<key>UISupportedInterfaceOrientations</key>
	<array>
		<string>UIInterfaceOrientationPortrait</string>
	</array>
</dict>
</plist>
```

- [x] **Step 6: 新建 Asset Catalog 占位**

`iosApp/iosApp/Assets.xcassets/Contents.json`：

```json
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`：

```json
{
  "images" : [
    {
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

（空 iconset 构建仅有警告，允许；正式图标后续补。）

- [x] **Step 7: 新建 `iosApp/iosApp.xcodeproj/project.pbxproj`**（objectVersion 77 / 文件系统同步组布局，与 Xcode 16 官方模板一致）

```
// !$*UTF8*$!
{
	archiveVersion = 1;
	classes = {
	};
	objectVersion = 77;
	objects = {

/* Begin PBXFileSystemSynchronizedRootGroup section */
		AA0000000000000000000001 /* iosApp */ = {
			isa = PBXFileSystemSynchronizedRootGroup;
			exceptions = (
				AA0000000000000000000013 /* Exceptions for "iosApp" folder in "iosApp" target */,
			);
			path = iosApp;
			sourceTree = "<group>";
		};
/* End PBXFileSystemSynchronizedRootGroup section */

/* Begin PBXFileSystemSynchronizedBuildFileExceptionSet section */
		AA0000000000000000000013 /* Exceptions for "iosApp" folder in "iosApp" target */ = {
			isa = PBXFileSystemSynchronizedBuildFileExceptionSet;
			membershipExceptions = (
				Info.plist,
			);
			target = AA0000000000000000000008 /* iosApp */;
		};
/* End PBXFileSystemSynchronizedBuildFileExceptionSet section */

/* Begin PBXFileReference section */
		AA0000000000000000000005 /* iosApp.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = iosApp.app; sourceTree = BUILT_PRODUCTS_DIR; };
		AA0000000000000000000006 /* Config.xcconfig */ = {isa = PBXFileReference; lastKnownFileType = text.xcconfig; path = Config.xcconfig; sourceTree = "<group>"; };
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
		AA0000000000000000000007 /* Frameworks */ = {
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		AA0000000000000000000002 = {
			isa = PBXGroup;
			children = (
				AA0000000000000000000001 /* iosApp */,
				AA0000000000000000000004 /* Configuration */,
				AA0000000000000000000003 /* Products */,
			);
			sourceTree = "<group>";
		};
		AA0000000000000000000003 /* Products */ = {
			isa = PBXGroup;
			children = (
				AA0000000000000000000005 /* iosApp.app */,
			);
			name = Products;
			sourceTree = "<group>";
		};
		AA0000000000000000000004 /* Configuration */ = {
			isa = PBXGroup;
			children = (
				AA0000000000000000000006 /* Config.xcconfig */,
			);
			path = Configuration;
			sourceTree = "<group>";
		};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		AA0000000000000000000008 /* iosApp */ = {
			isa = PBXNativeTarget;
			buildConfigurationList = AA0000000000000000000009 /* Build configuration list for PBXNativeTarget "iosApp" */;
			buildPhases = (
				AA0000000000000000000011 /* Embed Kotlin framework (Gradle) */,
				AA0000000000000000000007 /* Frameworks */,
				AA0000000000000000000012 /* Sources */,
				AA0000000000000000000010 /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			fileSystemSynchronizedGroups = (
				AA0000000000000000000001 /* iosApp */,
			);
			name = iosApp;
			productName = iosApp;
			productReference = AA0000000000000000000005 /* iosApp.app */;
			productType = "com.apple.product-type.application";
		};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		AA000000000000000000000C /* Project object */ = {
			isa = PBXProject;
			attributes = {
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1600;
				LastUpgradeCheck = 1600;
				TargetAttributes = {
					AA0000000000000000000008 = {
						CreatedOnToolsVersion = 16.0;
					};
				};
			};
			buildConfigurationList = AA000000000000000000000D /* Build configuration list for PBXProject "iosApp" */;
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
				"zh-Hans",
			);
			mainGroup = AA0000000000000000000002;
			minimizedProjectReferenceProxies = 1;
			preferredProjectObjectVersion = 77;
			productRefGroup = AA0000000000000000000003 /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				AA0000000000000000000008 /* iosApp */,
			);
		};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
		AA0000000000000000000010 /* Resources */ = {
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXResourcesBuildPhase section */

/* Begin PBXShellScriptBuildPhase section */
		AA0000000000000000000011 /* Embed Kotlin framework (Gradle) */ = {
			isa = PBXShellScriptBuildPhase;
			alwaysOutOfDate = 1;
			buildActionMask = 2147483647;
			files = (
			);
			inputPaths = (
			);
			name = "Embed Kotlin framework (Gradle)";
			outputPaths = (
			);
			runOnlyForDeploymentPostprocessing = 0;
			shellPath = /bin/sh;
			shellScript = "if [ -z \"${JAVA_HOME:-}\" ]; then\n    export JAVA_HOME=\"$(/usr/libexec/java_home)\"\nfi\ncd \"$SRCROOT/..\"\n./gradlew :shared:embedAndSignAppleFrameworkForXcode\n";
		};
/* End PBXShellScriptBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
		AA0000000000000000000012 /* Sources */ = {
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
		AA000000000000000000000E /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = NO;
				GCC_C_LANGUAGE_STANDARD = gnu17;
				GCC_DYNAMIC_NO_PIC = NO;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = 0;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LOCALIZATION_PREFERS_STRING_CATALOGS = YES;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				ONLY_ACTIVE_ARCH = YES;
				SDKROOT = iphoneos;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
				SWIFT_VERSION = 5.0;
			};
			name = Debug;
		};
		AA000000000000000000000F /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = NO;
				GCC_C_LANGUAGE_STANDARD = gnu17;
				GCC_NO_COMMON_BLOCKS = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LOCALIZATION_PREFERS_STRING_CATALOGS = YES;
				MTL_ENABLE_DEBUG_INFO = NO;
				SDKROOT = iphoneos;
				SWIFT_COMPILATION_MODE = wholemodule;
				SWIFT_VERSION = 5.0;
				VALIDATE_PRODUCT = YES;
			};
			name = Release;
		};
		AA000000000000000000000A /* Debug */ = {
			isa = XCBuildConfiguration;
			baseConfigurationReference = AA0000000000000000000006 /* Config.xcconfig */;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				CODE_SIGN_STYLE = Automatic;
				ENABLE_PREVIEWS = YES;
				FRAMEWORK_SEARCH_PATHS = (
					"$(inherited)",
					"$(BUILT_PRODUCTS_DIR)",
				);
				GENERATE_INFOPLIST_FILE = NO;
				INFOPLIST_FILE = iosApp/Info.plist;
				IPHONEOS_DEPLOYMENT_TARGET = "$(DEPLOYMENT_TARGET)";
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				PRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID)";
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Debug;
		};
		AA000000000000000000000B /* Release */ = {
			isa = XCBuildConfiguration;
			baseConfigurationReference = AA0000000000000000000006 /* Config.xcconfig */;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				CODE_SIGN_STYLE = Automatic;
				ENABLE_PREVIEWS = YES;
				FRAMEWORK_SEARCH_PATHS = (
					"$(inherited)",
					"$(BUILT_PRODUCTS_DIR)",
				);
				GENERATE_INFOPLIST_FILE = NO;
				INFOPLIST_FILE = iosApp/Info.plist;
				IPHONEOS_DEPLOYMENT_TARGET = "$(DEPLOYMENT_TARGET)";
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				PRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID)";
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Release;
		};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		AA000000000000000000000D /* Build configuration list for PBXProject "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				AA000000000000000000000E /* Debug */,
				AA000000000000000000000F /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
		AA0000000000000000000009 /* Build configuration list for PBXNativeTarget "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				AA000000000000000000000A /* Debug */,
				AA000000000000000000000B /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
/* End XCConfigurationList section */
	};
	rootObject = AA000000000000000000000C /* Project object */;
}
```

排错提示（按需逐个排查，不改设计决策）：① Swift 报 `No such module 'shared'`——确认 Run Script 阶段先于 Sources 执行且 Gradle 输出无错误；必要时在 FRAMEWORK_SEARCH_PATHS 增补 `$(BUILT_PRODUCTS_DIR)/$(FRAMEWORKS_FOLDER_PATH)` 或 Gradle framework 产物的实际目录。② `java not found`——脚本内 `/usr/libexec/java_home` 兜底已处理；CI 由 setup-java 提供 JAVA_HOME。③ pbxproj 语法问题——`plutil -lint` 与 `xcodebuild -list` 会立刻暴露。

- [x] **Step 8: 工程可解析 + 无签名模拟器构建**

Run:
```bash
plutil -lint iosApp/iosApp/Info.plist
xcodebuild -list -project iosApp/iosApp.xcodeproj
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build/sim-dd build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
```
（模拟器名按 `xcrun simctl list devices available` 输出调整；下同。）

Expected: Info.plist 校验通过；scheme 列表含 `iosApp`；模拟器构建 PASS（其中 Run Script 会先执行 `:shared:embedAndSignAppleFrameworkForXcode` 编译 Kotlin framework）。

- [x] **Step 9: 提交**

```bash
git add shared/src/iosMain/kotlin/com/github/ilife798/MainViewController.kt iosApp
git commit -m "feat: add iosApp Xcode shell hosting shared Compose UI with config injection"
```

---

### Task 11: 本地模拟器冒烟与记录【tasks 4.3】

**Files:**
- Create: `docs/openspec/changes/ios-packaging-foundation/simulator-smoke-checklist.md`

**Interfaces:**
- Consumes: Task 10 构建出的 `.app`；Task 4/5/6/7 登记的验证项。
- Produces: 冒烟结论记录（change 目录内，PR 依据之一）。

- [x] **Step 1: 构建并安装到模拟器**

Run:
```bash
xcrun simctl boot "iPhone 16" 2>/dev/null || true
xcrun simctl install booted iosApp/build/sim-dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.github.ilife798.iosApp
```
Expected: launch 命令退出码 0，模拟器中出现共享界面。

- [x] **Step 2: 执行冒烟清单并记录**（每项标 通过/失败/受阻 + 结论；允许附截图路径）

清单（来自 design §6.2 与 Task 4/5/6/7 的登记项）：
1. 启动无崩溃，显示共享界面。
2. 登录成功（Darwin 引擎请求可达后端）；断网时呈现网络错误文案而非崩溃。
3. 手动输入设备编号添加设备成功。
4. 任务 / 账单 / 积分 / 设置页遍历可用，时间与版本号显示正常。
5. 杀掉应用重启 → 登录态与设置项恢复（NSUserDefaults）。
6. 扫码入口 → 占位页出现 + 返回可回退。
7. 支付入口 → 「iOS 端暂不支持应用内支付」反馈。
8. 更新入口 → 无崩溃（安装/下载空转）。
9. 电池优化设置入口 → 无跳转无崩溃。
10. 赞助入口 → Safari 打开 afdian 链接。
11. Toast 场景静默不崩。

全部通过方可进入 Task 12；miuix 渲染异常按 design §7 风险 3 处理（记录到 Change 2，不阻塞本 change 除非崩溃级）。

- [x] **Step 3: 提交**

```bash
git add docs/openspec/changes/ios-packaging-foundation/simulator-smoke-checklist.md
git commit -m "docs: record iOS simulator smoke test results"
```

---

### Task 12: 无签名 IPA 打包脚本【tasks 5.1】

**Files:**
- Create: `iosApp/scripts/build-unsigned-ipa.sh`（`chmod +x`）

**Interfaces:**
- Consumes: Task 10 的 `iosApp/iosApp.xcodeproj`（scheme `iosApp`）；androidApp `versionName`。
- Produces: `iosApp/build/dist/ILife798-v$VERSION-unsigned-ios.ipa`；脚本可被 Task 14/15 的 CI 直接调用（无参数；`IPA_VERSION` 环境变量可覆盖版本）。

- [x] **Step 1: 编写脚本**

```bash
#!/usr/bin/env bash
# 无签名 IPA 打包：xcodebuild 归档（不使用任何签名身份）→ Payload 组装 → zip 为 .ipa。
# 用法：./iosApp/scripts/build-unsigned-ipa.sh
# 环境变量：IPA_VERSION 可覆盖版本号（默认取 androidApp 的 versionName）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

VERSION="${IPA_VERSION:-$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts | head -n 1)}"
if [ -z "$VERSION" ]; then
  echo "错误：无法确定版本号（androidApp/build.gradle.kts 的 versionName）" >&2
  exit 1
fi

ARCHIVE_PATH="$ROOT_DIR/iosApp/build/iosApp.xcarchive"
DIST_DIR="$ROOT_DIR/iosApp/build/dist"

xcodebuild \
  -project "$ROOT_DIR/iosApp/iosApp.xcodeproj" \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  archive \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY=""

APP_PATH="$ARCHIVE_PATH/Products/Applications/iosApp.app"
test -d "$APP_PATH" || { echo "错误：归档中未找到 $APP_PATH" >&2; exit 1; }

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
mkdir -p "$STAGING/Payload"
cp -R "$APP_PATH" "$STAGING/Payload/"

mkdir -p "$DIST_DIR"
IPA_PATH="$DIST_DIR/ILife798-v$VERSION-unsigned-ios.ipa"
(cd "$STAGING" && ditto -c -k --keepParent Payload "$IPA_PATH")

# 产物校验：Payload 结构必须包含 Info.plist 与主二进制（design §4）。
unzip -l "$IPA_PATH" | grep -q "Payload/iosApp.app/Info.plist"
unzip -l "$IPA_PATH" | grep -q "Payload/iosApp.app/iosApp"
unzip -t "$IPA_PATH" > /dev/null

echo "已生成: $IPA_PATH"
```

Run: `chmod +x iosApp/scripts/build-unsigned-ipa.sh`

- [x] **Step 2: 本地打包**

Run: `./iosApp/scripts/build-unsigned-ipa.sh`
Expected: 退出码 0，输出 `iosApp/build/dist/ILife798-v1.2.3-unsigned-ios.ipa`。

- [x] **Step 3: 无签名环境验证（Review Focus #3 的钉子）**

Run:
```bash
security find-identity -v -p codesigning | grep -c "iPhone Developer\|Apple Development" || true
./iosApp/scripts/build-unsigned-ipa.sh
```
Expected: 即使本机无任何签名身份（计数为 0），打包仍然成功退出 0——归档命令显式 `CODE_SIGN_IDENTITY=""` 保证这一点。产物可被签名工具接受：`unzip -l` 断言 `Payload/iosApp.app/Info.plist` 与主二进制 `Payload/iosApp.app/iosApp` 存在（脚本内已断言），`unzip -t` 完整性通过。

- [x] **Step 4: 提交**

```bash
git add iosApp/scripts/build-unsigned-ipa.sh
git commit -m "feat: add unsigned IPA packaging script via xcodebuild archive"
```

---

### Task 13: README 的 iOS 构建与自签说明【tasks 5.2】

**Files:**
- Modify: `README.md`（在现有 Android 构建说明之后追加一节）

**Interfaces:**
- Consumes: Task 12 脚本的路径与行为。
- Produces: 面向用户的本地构建 + 自签安装指引（design §7 风险 6：避免未签名 IPA 被误认为损坏）。

- [x] **Step 1: 在 `README.md` 追加以下小节（Markdown，二级标题位置按现有文档层级并入）**

````markdown
## iOS 构建（未签名）

要求：macOS + Xcode 16+ + JDK 21。

```sh
# 编译 iOS 双架构（CI 亦执行）
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64

# 产出未签名 IPA：iosApp/build/dist/ILife798-v<版本>-unsigned-ios.ipa
./iosApp/scripts/build-unsigned-ipa.sh

# 本地模拟器验证
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build/sim-dd build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
xcrun simctl install booted iosApp/build/sim-dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.github.ilife798.iosApp
```

### 未签名 IPA 安装说明

产出的 `.ipa` **未经签名，不能直接安装到 iPhone**，这是预期行为（本仓库不持有任何签名证书）。请自行签名后安装：

- **图形工具（推荐）**：用 [Sideloadly](https://sideloadly.io) 或 AltStore 将 IPA 用你的 Apple ID 签名并侧载（个人免费账号 7 天有效期）。
- **命令行手动签名**（需已有 Apple 开发证书与配置文件）：

```sh
unzip ILife798-v<版本>-unsigned-ios.ipa -d /tmp/ios-sign
codesign -f -s "Apple Development: <你的证书名>" /tmp/ios-sign/Payload/iosApp.app/Frameworks/shared.framework
codesign -f -s "Apple Development: <你的证书名>" /tmp/ios-sign/Payload/iosApp.app
cd /tmp/ios-sign && zip -qry ../ILife798-signed.ipa Payload
```

签名后的 IPA 通过 Xcode（Devices & Simulators）或 Apple Configurator 安装到设备。
````

- [x] **Step 2: 提交**

```bash
git add README.md
git commit -m "docs: add iOS build and self-signing installation guide"
```

---

### Task 14: ios.yml validate job【tasks 6.1】

**Files:**
- Create: `.github/workflows/ios.yml`

**Interfaces:**
- Consumes: Task 12 的打包脚本；仓库 secrets `API_GATEWAY`/`SIGN_SALT`/`API_CID`（与 android.yml 同源）。
- Produces: iOS 验证工作流（PR/push(main) + workflow_dispatch，macos-latest，JDK 21 + Gradle 缓存 + secrets 注入 + 双架构编译 + 打包 + IPA artifact）。Task 15 在同文件追加 release job。

- [x] **Step 1: 创建 `.github/workflows/ios.yml`（先只含 validate job）**

```yaml
name: iOS CI and Release

on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
  push:
    branches: [main]
    tags:
      - 'v*'
    paths-ignore:
      - 'androidApp/build.gradle.kts'
      - '.github/workflows/**'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ios-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

env:
  APP_MODULE: androidApp

jobs:
  validate:
    name: Validate iOS build
    if: ${{ !startsWith(github.ref, 'refs/tags/') }}
    runs-on: macos-latest

    steps:
      - uses: actions/checkout@v7

      - name: Set up JDK 21
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4
        with:
          packages: 'platform-tools'

      - name: Install Android platform
        run: sdkmanager 'platforms;android-37.0'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Generate secrets.properties
        env:
          API_GATEWAY: ${{ secrets.API_GATEWAY }}
          SIGN_SALT: ${{ secrets.SIGN_SALT }}
          API_CID: ${{ secrets.API_CID }}
        run: |
          printf 'API_GATEWAY=%s\n' "$API_GATEWAY" > secrets.properties
          printf 'SIGN_SALT=%s\n' "$SIGN_SALT" >> secrets.properties
          printf 'API_CID=%s\n' "$API_CID" >> secrets.properties

      - name: Compile iOS targets
        run: ./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --no-daemon --stacktrace

      - name: Build unsigned IPA
        run: ./iosApp/scripts/build-unsigned-ipa.sh

      - name: Upload unsigned IPA
        uses: actions/upload-artifact@v7
        with:
          name: ilife798-unsigned-ios-ipa
          path: iosApp/build/dist/*.ipa
          if-no-files-found: error
```

说明：macOS runner 需显式安装 Android SDK（shared 模块含 Android target，Gradle 配置阶段即需要；镜像 android.yml 步骤）。CI 只做编译 + 打包，不跑测试、不做模拟器冒烟（全局约束）。

- [x] **Step 2: push 分支并在 PR 上验证 CI**

Run:
```bash
git push -u origin HEAD
gh run watch $(gh run list --workflow=ios.yml --limit 1 --json databaseId -q '.[0].databaseId')
```
Expected: validate job 绿色；运行页面可下载 `ilife798-unsigned-ios-ipa` 产物，`unzip -l` 该产物结构正确（Review Focus：无证书 CI runner 等价于本机无签名环境）。若 Xcode 大版本漂移导致失败，按 design §7 钉 `setup-xcode`（届时再引入）。

- [x] **Step 3: 提交（随分支推送生效）**

```bash
git add .github/workflows/ios.yml
git commit -m "ci: add iOS validate workflow on macOS runner"
```

---

### Task 15: ios.yml release job 与 tag 端到端验证【tasks 6.2】

**Files:**
- Modify: `.github/workflows/ios.yml`（jobs 下追加 release）

**Interfaces:**
- Consumes: Task 12 脚本；android.yml 的版本一致性写法与 `view || create` 发布写法。
- Produces: tag `v*` 触发的自动发布：版本一致性校验 → 构建 IPA → 幂等发布到 GitHub Release。

- [x] **Step 1: 在 `.github/workflows/ios.yml` 的 `jobs:` 下追加 release job**

```yaml
  release:
    name: Build and publish iOS release
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    runs-on: macos-latest
    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v7

      - name: Set up JDK 21
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4
        with:
          packages: 'platform-tools'

      - name: Install Android platform
        run: sdkmanager 'platforms;android-37.0'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Verify release tag matches app version
        run: |
          version_name="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' "$APP_MODULE/build.gradle.kts" | head -n 1)"
          test "$GITHUB_REF_NAME" = "v$version_name"

      - name: Generate secrets.properties
        env:
          API_GATEWAY: ${{ secrets.API_GATEWAY }}
          SIGN_SALT: ${{ secrets.SIGN_SALT }}
          API_CID: ${{ secrets.API_CID }}
        run: |
          printf 'API_GATEWAY=%s\n' "$API_GATEWAY" > secrets.properties
          printf 'SIGN_SALT=%s\n' "$SIGN_SALT" >> secrets.properties
          printf 'API_CID=%s\n' "$API_CID" >> secrets.properties

      - name: Build unsigned IPA
        run: ./iosApp/scripts/build-unsigned-ipa.sh

      - name: Upload unsigned IPA artifact
        uses: actions/upload-artifact@v7
        with:
          name: ilife798-${{ github.ref_name }}-unsigned-ios-ipa
          path: iosApp/build/dist/*.ipa
          if-no-files-found: error

      - name: Publish GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          assets=(iosApp/build/dist/*.ipa)
          if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
            gh release upload "$GITHUB_REF_NAME" "${assets[@]}" --clobber
          else
            gh release create "$GITHUB_REF_NAME" "${assets[@]}" --generate-notes \
              || gh release upload "$GITHUB_REF_NAME" "${assets[@]}" --clobber
          fi
```

（release 的 `view || create` + create 失败回退 `upload --clobber` 兜底 android/ios 双工作流在同一个 tag 上并发创建 Release 的竞争——Review Focus #5 的钉子。）

- [x] **Step 2: 端到端验证一次 tag 触发**

Run:
```bash
git tag -l v1.2.3   # 先确认 tag 是否已存在
```
- 若不存在：`git tag v1.2.3 && git push origin v1.2.3`，`gh run watch` 至 release job 结束。Expected: Release `v1.2.3` 出现且资产含 `ILife798-v1.2.3-unsigned-ios.ipa`。验证后清理测试产物：`gh release delete v1.2.3 --yes && git push origin :refs/tags/v1.2.3`（若 tag 同时被 android.yml 使用则保留，二选一说明于任务备注）。
- 若已存在（上游已发布）：改为验证「已存在 Release 时 upload --clobber 增补」路径——`gh release view v1.2.3` 确认后重推同名 tag（`git push origin v1.2.3 --force` 需维护者同意）或延后到下一次真实版本发布时验证，并在任务备注记录偏差。

- [x] **Step 3: 提交**

```bash
git add .github/workflows/ios.yml
git commit -m "ci: publish unsigned iOS IPA to GitHub Release on version tags"
```

---

### Task 16: 全量回归与文件边界核对【tasks 7.1】

**Files:** 无新增/修改（纯验证任务）。

**Interfaces:**
- Consumes: 全部前序任务；base-ref `af15849e271391da91d578d3fe6090ccff5fcada`。
- Produces: Android CI 门 + iOS 构建门全绿的最终结论；PR 关键修改文件清单核对记录。

- [x] **Step 1: Android CI 全量门（与 android.yml validate 相同任务集）**

Run: `./gradlew spotlessCheck testDebugUnitTest testAndroidHostTest lintDebug --no-daemon --stacktrace`
Expected: 全部 PASS。

- [x] **Step 2: iOS 构建门**

Run:
```bash
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --no-daemon --stacktrace
./iosApp/scripts/build-unsigned-ipa.sh
```
Expected: 全部 PASS（打包断言内建于脚本）。

- [x] **Step 3: PR 文件边界核对（与 Comet 工作流文件完全分离）**

Run:
```bash
git diff --name-only af15849e271391da91d578d3fe6090ccff5fcada...HEAD
git diff --name-only af15849e271391da91d578d3fe6090ccff5fcada...HEAD | grep -c "^\.comet/" || echo 0
```
Expected: 文件清单仅落在 `shared/src/iosMain/**`、`shared/build.gradle.kts`、`gradle/libs.versions.toml`、`iosApp/**`、`.github/workflows/ios.yml`、`README.md`、`docs/`（计划/设计/change 文档）；`.comet/` 计数为 0；`.github/workflows/android.yml` 与 androidApp 构建配置不在清单中。把核对结论记录进任务备注/PR 描述。

- [x] **Step 4: 无代码变更则无提交；若核对发现问题，回对应任务修复后重跑 Step 1-3**

---

## 自查记录（Self-Review）

1. **Spec 覆盖**：ios-build spec 四条 Requirement → Task 1/2（iOS target）、Task 2/4-7（actual 分级）、Task 9/10（工程与配置注入）、Task 12（未签名 IPA）、Task 11（模拟器验证）；ios-ci spec 四条 Requirement → Task 14（PR 验证 + 产物可下载）、Task 15（tag 发布 + secrets 注入）、Task 12/14（无签名环境可发布）。tasks.md 15 项全部映射（见映射表）。
2. **占位扫描**：无 TBD/TODO/「适当处理」类占位；所有代码步骤给出完整内容；不确定的 platform 绑定均写明「以编译器提示为准修正、不改设计决策」并给出退化路径（DynamicColorKey 的 null 回退为设计允许）。
3. **类型/命名一致性**：`IosBuildConfig.API_GATEWAY/SIGN_SALT/API_CID`（Task 9 产出 = Task 10 消费）；framework `shared`（Task 1 产出 = Task 10 `import shared`）；脚本输出路径 `iosApp/build/dist/*.ipa`（Task 12 = Task 14/15 artifact path）；`IPA_VERSION` 环境变量；冒烟清单文件名与各任务登记项一致。
4. **Review Focus**：五类输入各自有钉住步骤（Task 9 步骤 3/4、Task 12 步骤 3、Task 11 清单 6-9、Task 15 步骤 1/2）。
