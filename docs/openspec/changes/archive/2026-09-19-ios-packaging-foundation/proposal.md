# 提案：iOS 打包构建基础（ios-packaging-foundation）

## Why

ILife798 是 Kotlin/Compose Multiplatform 项目，`shared` 模块已通过 expect/actual 将平台能力隔离在 `androidMain`，但目前只配置了 Android target——上游没有 iOS 构建环境，iOS 用户无法获得应用包。本 change 为项目补齐 iOS 打包构建能力：不依赖任何证书签名，产出未签名 IPA 交由用户自行签名安装（与社区常见分发方式一致），并由 GitHub Actions 在 macOS runner 上全自动构建，保证后续无需人工介入即可持续产出。本 change 是拆分确认后的第一个里程碑（批量清单：`.comet/batches/ios-two-milestones.json`），聚焦"能构建、能自动出包"；iOS 平台功能替代实现归第二个 change `ios-platform-features`。

## What Changes

- `shared` 模块新增 iOS target（iosArm64 / iosSimulatorArm64）与 `iosMain` source set。
- 为 commonMain 的 34 个 expect 声明提供 iOS actual：
  - **基础工具类直接真实现**（量小且 app 可用必需）：`PersistentStorage`→NSUserDefaults、`Clipboard`→UIPasteboard、`TimeUtils`、`Version`→NSBundle、`Logger`、`ImageConversion`→Skia 解码、`HttpClientProvider`/`createUpdateHttpClient`→Ktor Darwin 引擎、`NetworkErrors`、`DynamicColorKey`、`WindowBlur` 等按平台能力实现。
  - **重交互功能最小可编译 stub**（归 Change 2 替代实现）：`QrScannerPage`、`RunNotifications`、`Alipay`、`AppUpdatePlatform`（installApk 等）、`BatteryOptimization`、`Sponsor`、`Toast` 等空实现或明确反馈"暂不支持"。
- 新增 `iosApp/` Xcode 工程：承载共享 Compose UI（`ComposeUIViewController`），启动时调用 `ApiConfig.init` 注入网关配置（来源与 Android `secrets.properties` 对齐）。
- 新增无签名打包脚本：`xcodebuild` 归档（`CODE_SIGNING_ALLOWED=NO`）+ 组装 `Payload/*.app` 为未签名 IPA。
- 新增 `.github/workflows/ios.yml`，镜像 `android.yml` 结构：PR/push 验证构建；tag `v*` 触发时把未签名 IPA 发布到 GitHub Release。
- `gradle/libs.versions.toml` 按需新增依赖（如 `ktor-client-darwin`）。
- 不改动 Android 端任何构建配置与运行行为。

## Capabilities

### New Capabilities

- `ios-build`：iOS 目标构建能力——shared 模块 iOS target 与 iosMain actual 的完整性要求，iosApp 工程承载共享 UI，无签名 IPA 的产出与产物有效性要求。
- `ios-ci`：iOS 持续集成与自动发布能力——GitHub Actions 在 macOS runner 上自动验证构建、tag 触发自动发布未签名 IPA 到 GitHub Release 的要求。

### Modified Capabilities

（无——Android 端行为与需求不变）

## Impact

- **代码**：`shared/build.gradle.kts`（新增 iOS target 配置）；`shared/src/iosMain/**`（新增约 20 个 actual 文件）；`iosApp/**`（新增 Xcode 工程）；`.github/workflows/ios.yml`（新增）；`gradle/libs.versions.toml`（新增依赖项）。
- **依赖**：新增 `ktor-client-darwin`（Ktor iOS 引擎）；其余依赖（Compose Multiplatform 1.12、miuix 0.9.4、coil3、lifecycle 2.11）均已是多平台工件，无需变更版本。
- **CI 系统**：新增 macOS runner 工作流；Android 工作流零改动；两工作流共享同一 secrets（`API_GATEWAY`/`SIGN_SALT`/`API_CID`）。
- **发布物**：tag `v*` 时 GitHub Release 将同时包含 Android APK 与未签名 iOS IPA。
- **上游 PR**：本 change 的产物即"关键修改"主体，文件边界清晰（iosMain + iosApp + workflow + gradle 配置），不包含 Comet 工作流文件。
