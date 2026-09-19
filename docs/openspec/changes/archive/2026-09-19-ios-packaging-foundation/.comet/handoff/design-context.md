# Comet Design Handoff

- Change: ios-packaging-foundation
- Phase: design
- Mode: compact
- Context hash: d86f17d509efd0ad75485f2eb655a650623a81e0d62acb7370fa4a243357bef6

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/ios-packaging-foundation/proposal.md

- Source: docs/openspec/changes/ios-packaging-foundation/proposal.md
- Lines: 1-36
- SHA256: 9cc74c1a7f3d031e8f669f3300319faa6f1d45c78fbb75f57a720ffab3d245c4

```md
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

```

## docs/openspec/changes/ios-packaging-foundation/design.md

- Source: docs/openspec/changes/ios-packaging-foundation/design.md
- Lines: 1-67
- SHA256: 9c4e3eb94c50bdc86a9eaa9376f788491e339819561e165adbfe4a4bef9eb70f

```md
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

```

## docs/openspec/changes/ios-packaging-foundation/tasks.md

- Source: docs/openspec/changes/ios-packaging-foundation/tasks.md
- Lines: 1-36
- SHA256: 92e4efba911a339f35d6d66157dfd8a0b209a8dd937735a7e1ffe86b1b88cc97

```md
## 1. iOS Target 最小可编译（Spike 优先）

- [ ] 1.1 `shared/build.gradle.kts` 声明 `iosArm64()` + `iosSimulatorArm64()` target 与 framework 导出配置，`gradle/libs.versions.toml` 按需新增依赖；执行 `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64` 验证 commonMain 在 iOS 编译通过（若报缺 actual 属预期，转入 1.2）
- [ ] 1.2 按 commonMain 的 34 个 expect 清单在 `shared/src/iosMain` 建立分级 actual 骨架（基础工具类暂以可编译最简实现占位、重交互类空实现），验证 1.1 的编译任务全部通过
- [ ] 1.3 执行 `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` 验证 Android 构建与测试零回归

## 2. 基础工具类 iOS 真实现

- [ ] 2.1 实现 `PersistentStorage`（NSUserDefaults）与 `Clipboard`（UIPasteboard），并用 iOS 模拟器单元测试或手动验证写入/读取/重启恢复
- [ ] 2.2 实现 `TimeUtils`、`Version`（NSBundle）、`Logger`、`ImageConversion`（Skia 解码）、`NetworkErrors`、`DynamicColorKey`、`WindowBlur` 的 iOS actual，验证编译通过且各功能在模拟器可用
- [ ] 2.3 引入 `ktor-client-darwin` 并实现 `HttpClientProvider`、`createUpdateHttpClient`，验证模拟器上登录请求可达后端

## 3. 重交互功能安全 stub

- [ ] 3.1 实现 `QrScannerPage` iOS 占位页（明确提示暂不支持）、`RunNotifications` 空对象、`Alipay` 返回失败结果、`AppUpdatePlatform` 系列/`BatteryOptimization` 空实现、`Sponsor` 打开网页链接、`Toast` 静默，验证编译与模拟器运行中触发这些入口不崩溃
- [ ] 3.2 核对 expect/actual 覆盖完整性（commonMain 每个 expect 在 iosMain 均有 actual），记录核对清单于 change 目录

## 4. 配置注入与 iosApp 工程

- [ ] 4.1 实现 Gradle 配置注入：从 `secrets.properties`（或环境变量）生成 iOS 构建期常量（缺省空字符串，与 Android 行为一致），验证有/无配置两种构建均成功
- [ ] 4.2 按 CMP 官方模板新建 `iosApp/` Xcode 工程（xcodeproj + xcconfig + Gradle 框架嵌入脚本 + Info.plist），`ComposeUIViewController` 承载共享 `App()`，启动时注入网关配置
- [ ] 4.3 本地模拟器安装运行：登录、手动输入设备编号添加设备、任务/账单/积分页面可用；记录验证截图或结论于 change 目录

## 5. 无签名 IPA 打包

- [ ] 5.1 编写打包脚本（`xcodebuild archive` 无签名归档 → Payload 组装 → 未签名 IPA），验证本地产出 IPA 且 Payload 结构有效（可被签名工具接受）
- [ ] 5.2 在 README 补充 iOS 本地构建与用户自行签名安装说明

## 6. GitHub Actions 全自动打包

- [ ] 6.1 新增 `.github/workflows/ios.yml`：validate job（PR/push，macos runner，JDK 21 + Gradle 缓存 + secrets 注入 + iOS 构建验证 + IPA artifact 上传）；验证 push 后 Actions 运行成功且产物可下载
- [ ] 6.2 实现 release job（tag `v*`：版本一致性校验、构建 IPA、`gh release view || create` 幂等发布到 GitHub Release），验证一次 tag 触发的端到端发布（可用测试 tag）

## 7. 收尾验证

- [ ] 7.1 全量回归：Android CI 任务（spotlessCheck/test/lint）与 iOS 构建验证在本仓通过；确认 PR 关键修改文件清单（shared/iosMain、iosApp、ios.yml、gradle 配置）与 Comet 工作流文件完全分离

```

## docs/openspec/changes/ios-packaging-foundation/.openspec.yaml

- Source: docs/openspec/changes/ios-packaging-foundation/.openspec.yaml
- Lines: 1-2
- SHA256: e461a5cd9c263851dbb46471f9e3afd45a09338ec7b15a042283644a5ef6972b

```md
schema: spec-driven
created: 2026-09-19

```

## docs/openspec/changes/ios-packaging-foundation/specs/ios-build/spec.md

- Source: docs/openspec/changes/ios-packaging-foundation/specs/ios-build/spec.md
- Lines: 1-73
- SHA256: d228956c982884e680ef8829b8b2537b163797a531ae5cc7cc5d7f3e055261cd

```md
## Purpose

定义 ILife798 的 iOS 目标构建能力：`shared` 模块必须能在 iOS 平台完整编译并承载共享 Compose UI，本地与 CI 均可产出结构有效、可供用户自行签名的未签名 IPA，且不影响 Android 构建。

## ADDED Requirements

### Requirement: shared 模块提供 iOS target

`shared` 模块 SHALL 声明 iOS 设备（iosArm64）与 iOS 模拟器（iosSimulatorArm64）编译目标，使 commonMain 的全部共享代码（UI、导航、ViewModel、数据层）可在 iOS 平台编译。

#### Scenario: iOS target 编译通过

- **WHEN** 在 macOS 上执行 shared 模块的 iOS 编译任务（含模拟器与设备架构）
- **THEN** commonMain 全部代码编译成功，无缺失 actual 的编译错误

#### Scenario: Android target 保持不变

- **WHEN** 查看或执行 Android 构建配置与任务
- **THEN** Android target 的配置、产物与变更前完全一致

### Requirement: iOS 平台 actual 完整且行为分级

commonMain 中每一个 expect 声明 SHALL 在 iosMain 中存在对应 actual，并按以下分级提供行为：

- **基础工具类 SHALL 真实现**：持久化存储（映射到系统键值存储）、剪贴板复制、时间获取与格式化、应用版本号（来自系统包信息）、日志输出、HTTP 客户端引擎（iOS 原生引擎）等基础能力在 iOS 上真实可用。
- **重交互能力 SHALL 安全空实现**：扫码页、运行通知、支付宝支付、应用内更新安装、电池优化、前台保活等能力在 iOS 上以空实现或"明确反馈暂不支持"的方式满足编译与运行，不得导致崩溃。

#### Scenario: 基础工具类真实可用

- **WHEN** iOS 应用写入一条持久化数据后重启应用
- **THEN** 该数据从系统键值存储中恢复，登录态与设置不丢失

#### Scenario: 空实现能力不崩溃

- **WHEN** iOS 用户触发扫码、支付、应用内更新等 Android 专属功能入口
- **THEN** 应用保持稳定（无操作、返回失败结果或给出"暂不支持"反馈），不发生崩溃

### Requirement: iOS 应用工程承载共享 UI 并注入配置

项目 SHALL 包含一个 iOS 应用工程，启动时展示 commonMain 的共享 `App()` 界面，并以与 Android 一致的方式注入 API 网关、签名盐值与客户端标识配置；配置值 SHALL 不硬编码提交到仓库。

#### Scenario: 共享 UI 正常展示

- **WHEN** 在 iOS 模拟器启动应用
- **THEN** 显示与 Android 一致的共享界面，可完成登录、手动输入设备编号添加设备、查看任务/账单/积分等核心流程

#### Scenario: 配置来源与 Android 对齐

- **WHEN** 本地或 CI 构建 iOS 应用时提供了网关配置（与 Android secrets.properties 同源）
- **THEN** iOS 应用使用注入的配置访问后端；未提供时构建仍可完成，应用侧按配置缺失处理

### Requirement: 产出未签名 IPA

项目 SHALL 提供无证书签名的打包方式：归档产物 SHALL 生成包含 iOS 应用目录结构（Payload 布局）的未签名 `.ipa` 文件，使持有证书的用户可以使用常规签名工具自行签名安装；打包过程 SHALL 不要求任何 Apple 签名身份。

#### Scenario: 本地打包产出有效 IPA

- **WHEN** 在具备 Xcode 的 macOS 上执行无签名打包
- **THEN** 产出未签名 `.ipa`，其内部 Payload 结构有效，可被签名工具接受并完成签名

#### Scenario: 无签名环境可打包

- **WHEN** 构建环境中不存在任何 Apple 签名证书与描述文件
- **THEN** 打包流程正常完成，不因签名缺失而失败

### Requirement: 打包结果可本地验证

无签名 IPA 的产出 SHALL 可在本地（模拟器）验证：构建出的应用 SHALL 可安装到 iOS 模拟器并启动，核心流程可用。

#### Scenario: 模拟器安装运行

- **WHEN** 将构建产物安装到 iOS 模拟器并启动
- **THEN** 应用正常启动且共享界面可用

```

## docs/openspec/changes/ios-packaging-foundation/specs/ios-ci/spec.md

- Source: docs/openspec/changes/ios-packaging-foundation/specs/ios-ci/spec.md
- Lines: 1-51
- SHA256: 04edadb0243266cd142ad6ac58055c69fc3ada2c2f2f05d1304d8c54ed60c775

```md
## Purpose

定义 ILife798 的 iOS 持续集成与自动发布能力：GitHub Actions 在 macOS runner 上全自动完成 iOS 构建验证与未签名 IPA 发布，全程无需证书签名与人工介入。

## ADDED Requirements

### Requirement: PR 与主分支推送自动验证 iOS 构建

仓库 SHALL 提供 iOS 持续集成工作流：PR 与主分支推送时在 macOS runner 上自动执行 iOS 构建验证（编译与未签名打包），构建失败时工作流失败。

#### Scenario: PR 触发构建验证

- **WHEN** 提交或更新一个 Pull Request
- **THEN** macOS runner 自动执行 iOS 构建验证，结果反映在该 PR 的 CI 状态上

#### Scenario: 构建失败阻断

- **WHEN** iOS 构建验证中出现编译错误或打包失败
- **THEN** 工作流失败并暴露失败日志

### Requirement: tag 触发自动发布未签名 IPA

当推送 `v*` 格式的版本 tag 时，工作流 SHALL 自动构建并发布未签名 IPA，上传到对应的 GitHub Release；已存在的 Release SHALL 增补资产而不覆盖既有资产。

#### Scenario: 版本 tag 发布 IPA

- **WHEN** 推送符合版本规范的 tag
- **THEN** 该 tag 的 GitHub Release 中包含本次构建的未签名 IPA 文件

#### Scenario: 无签名环境可发布

- **WHEN** CI 构建环境中不存在任何 Apple 签名证书与描述文件
- **THEN** 发布流程正常完成并产出未签名 IPA

### Requirement: 敏感配置通过仓库密钥注入

iOS 工作流 SHALL 使用仓库 secrets 生成构建期配置（与 Android 工作流同源），SHALL NOT 将敏感配置值硬编码在工作流或仓库文件中；secrets 未配置时 SHALL 呈现明确的失败或可诊断行为。

#### Scenario: secrets 齐全时构建成功

- **WHEN** 仓库已配置网关相关 secrets 并触发 iOS 工作流
- **THEN** 工作流使用注入的配置完成构建并产出 IPA

### Requirement: 构建产物可获取

每次 iOS 工作流构建 SHALL 将未签名 IPA 上传为可下载的工作流产物，供用户在发布前获取测试。

#### Scenario: 验证构建产物可下载

- **WHEN** 一次 PR/push 触发的 iOS 构建验证成功
- **THEN** 该次运行页面上可下载未签名 IPA 产物

```
