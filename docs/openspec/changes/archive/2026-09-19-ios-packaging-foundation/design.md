# 设计（高层框架）：iOS 打包构建基础

## Context

项目为标准 KMP + Compose Multiplatform 结构：`shared`（commonMain 共享 UI/逻辑，androidMain 承载 34 个 expect 的 actual）+ `androidApp`。commonMain 无 Android 泄漏，依赖（CMP 1.12、miuix、coil3、ktor、lifecycle）均为多平台工件。现有 CI `android.yml` 采用 validate（PR/push）+ release（tag `v*`）双 job 结构，敏感配置经 secrets 生成 `secrets.properties`。本地 macOS + Xcode 16.4 可验证。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**

- iOS 设备 + 模拟器双 target 可编译；iosMain actual 分级实现（基础工具真实现 / 重交互安全 stub）
- `iosApp` Xcode 工程承载共享 UI，构建期注入网关配置（与 Android secrets 同源）
- 无签名归档 → 组装 Payload → 未签名 IPA 的可复用打包脚本
- `ios.yml` 镜像 `android.yml` 结构：PR/push 验证 + tag 发布到 GitHub Release
- Android 侧零改动

**Non-Goals:**

- 重交互功能的 iOS 原生替代实现（Change 2：扫码/通知/支付/更新）
- 证书签名、描述文件、TestFlight/App Store
- macOS/Windows desktop target

## Decisions

### D1. iOS target 声明与框架接入：官方 KMP 模板结构

- 在 `shared/build.gradle.kts` 中声明 `iosArm64()` + `iosSimulatorArm64()`（Kotlin 2.4 hierarchy template 默认提供 iosMain 中间源集），配置 framework 导出。
- 新增顶层 `iosApp/` Xcode 工程（CMP 官方模板布局：`iosApp.xcodeproj` + `Configuration/Config.xcconfig`），iOS 侧通过 `ComposeUIViewController` 展示共享 `App()`；Xcode 构建阶段调用 Gradle 任务生成/嵌入 Kotlin framework。
- 备选：无 Xcode 工程纯命令行打包（放弃——需要 Info.plist、图标、启动生命周期管理，Xcode 工程是上游社区可维护形态）；或 SwiftUI 重写壳（放弃——违背共享 UI 初衷）。

### D2. actual 分级实现（遵循上游设计意图）

- **真实现**（基础工具类，量小且 app 可用必需）：`PersistentStorage`→NSUserDefaults、`Clipboard`→UIPasteboard、`TimeUtils`→纯 Kotlin/平台时间、`Version`→NSBundle、`Logger`→os_log/NSLog、`ImageConversion`→Skia 解码、`HttpClientProvider` 与 `createUpdateHttpClient`→ktor-client-darwin、`NetworkErrors`→通用错误映射、`DynamicColorKey`/`WindowBlur`→按平台能力实现或优雅降级。
- **安全 stub**（Change 2 替代实现）：`QrScannerPage`→占位页提示暂不支持、`RunNotifications`→空对象（androidMain 注释已声明"其它平台为空实现"，属上游预留设计）、`Alipay`→返回失败结果、`AppUpdatePlatform` 系列→空实现/返回不支持、`BatteryOptimization`→返回已忽略、`Sponsor`→尝试打开网页链接（可低成本真实现）、`Toast`→静默。
- 备选：全部 stub（拒绝——持久化为空实现会导致登录态丢失，app 不可用）。

### D3. 构建期配置注入：Gradle 生成 Kotlin 常量

- 用 Gradle 任务从 `secrets.properties`（本地，与 Android 同文件）或环境变量（CI secrets）生成 `BuildKonfig` 风格的 Kotlin 常量文件到 build 目录，仅加入 iOS source set；缺省值与 Android 行为一致（空字符串，应用侧按配置缺失处理）。
- 备选：Info.plist + NSBundle 运行时读取（放弃——iOS 配置需经 Xcode 配置层传递，链路更长且与 Android 的 BuildConfig 同构性差）。

### D4. 无签名 IPA 组装：xcodebuild archive + Payload 打包

- `xcodebuild archive -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO` 归档后，从 `.xcarchive/Products/Applications` 取 `.app`，按 `Payload/` 布局压缩为 `.ipa`。
- 备选：`xcodebuild build` 直出 `.app`（拒绝——设备架构产物路径依赖 Xcode 内部布局，归档流程更稳定且脚本化明确）；`exportArchive`（拒绝——需要签名身份与导出 plist，违背无签名前提）。

### D5. CI 结构：镜像 android.yml

- `ios.yml` 两个 job：`validate`（PR/push，macos runner，JDK 21 + Gradle 缓存 + secrets 生成 + iOS 构建验证 + IPA artifact 上传）与 `release`（tag `v*`，构建 IPA 并 `gh release upload` 到既有 Release，不存在则创建）。
- runner 用 `macos-latest`（公共仓库免费且预装 Xcode 16.x）；版本 tag 与 `versionName` 的一致性校验同 Android。

## Risks / Trade-offs

- [AGP 9.4 的 `androidMultiplatformLibrary` 新插件与 iOS target 共存的配置细节未经项目验证] → 第一个实施任务即做最小 iOS target 编译 spike，尽早暴露配置问题
- [miuix 0.9.4-rc01 为 rc 版本，iOS 行为未经项目验证] → 模拟器验证共享 UI 渲染，问题记录并评估升级
- [tag 触发时 android.yml 与 ios.yml 并发创建 GitHub Release 存在竞争] → release job 延续 android.yml 的 `view || create` 幂等写法，create 失败回退为 upload
- [Kotlin/Native 编译使 CI 时长增加] → setup-gradle 缓存 + 仅在 iOS 工作流中触发 Native 编译
- [iOS deployment target 需与 CMP/KMP 最低要求匹配] → 设计阶段按依赖矩阵校准（预估 iOS 15）
- [无签名 IPA 在设备上无法直接安装，用户需自行签名] → README/Release 说明中写明签名指引

## Migration Plan

纯新增能力，无数据迁移。回滚 = 移除 iosMain/iosApp/ios.yml 及 gradle 配置增量，恢复到 Android-only 状态。

## Open Questions

无阻塞问题；deployment target 精确版本、xcconfig 细节等实现级参数在设计阶段校准，不影响 spec 与任务拆分。
