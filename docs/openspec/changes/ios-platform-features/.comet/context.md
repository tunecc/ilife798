# Comet Design Handoff

- Change: ios-platform-features
- Phase: design
- Mode: compact
- Context hash: 2d21876200c1577b64fe457d91350460cfbc25800af2274739be1b632f9f7541

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/ios-platform-features/proposal.md

- Source: docs/openspec/changes/ios-platform-features/proposal.md
- Lines: 1-34
- SHA256: d6823cdc43efa5912b16eb87c146d3d7c92d7e8debdb317c1866d41bd96766ff

```md
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

```

## docs/openspec/changes/ios-platform-features/design.md

- Source: docs/openspec/changes/ios-platform-features/design.md
- Lines: 1-57
- SHA256: 253c44244648377414d23d2d8e9cc19df9776a7b36bb159cb7eb54a06f85981d

```md
# 设计（高层框架）：iOS 平台功能替代实现

## Context

`ios-packaging-foundation` 已提供 iOS target、iosMain 骨架与 iosApp 工程；扫码/通知/支付/更新四个能力在 iOS 上是预留的安全 stub。Android 侧对应实现（CameraX+ZXing 扫码页、前台服务通知、支付宝 SDK、APK 下载安装）明确了需对齐的行为与接口形状（`AlipayPayResult`、扫码结果回填流程、通知更新/清除接口）。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**

- 四个能力的 iOS 原生替代实现，接口行为与 Android 语义对齐
- Info.plist 权限声明齐备（相机、通知按需）
- iOS 模拟器/真机可验证

**Non-Goals:**

- 前台服务/常驻保活（iOS 平台无此概念）
- 支付结果的自动回查（SDK 内才支持；跳转方式下以"确认中"语义反馈）
- Android 行为改动

## Decisions

### D1. 扫码：AVCaptureSession + Compose UIKitView

- iOS 扫码页用 `AVCaptureSession` + `AVCaptureMetadataOutput`（qr 类型）识别，视频预览层经 Compose `UIKitView` 嵌入共享导航的扫码页骨架；扫码交互（对焦提示、关闭按钮）在 Compose 层实现，与 Android 页面视觉对齐。
- 备选：iOS 16 `DataScannerViewController`（受限版本门槛与交互样式）；跳转系统相机（无法回传内容）。AVFoundation 全版本可用且可控。
- 备选 ZXing-KMP 纯解码（仍需自建相机流，无收益）。

### D2. 通知：UNUserNotificationCenter 单一通知槽

- `RunNotifications` iOS actual 映射为：设备状态 → 一条设备运行通知（更新内容），积分任务 → 一条积分进度通知；`removeTask`/停止 → 移除对应通知。首次上报前请求通知权限（授权回调内存记录，避免重复弹窗）。
- 备选：仿 Android 多通道多通知（放弃——iOS 通知组语义不同，单槽刷新更贴近 iOS 习惯且实现聚焦）。

### D3. 支付：URL Scheme 跳转

- 从 `orderInfo` 拼装支付宝标准跳转参数，`UIApplication.open` 唤起；唤起前用 `canOpenURL` 判安装，未安装/失败返回与 Android 相同结构的 `AlipayPayResult(false, ...)`。
- 备选：集成 AlipaySDK-iOS（放弃——引入重量级 SDK 与回调配置，违背关键修改最小化；URL Scheme 是社区通行轻量做法）。

### D4. 更新：版本提示 + 跳转发布页

- 复用共享层的版本比较与更新检测；iOS 分支把"下载安装"替换为打开项目 GitHub Releases 页（`UIApplication.open`）。`canInstallPackages`/`installApk` 等在 iOS 返回不支持语义。
- 备选：iTunes Lookup API 检查（不适用——应用不上架 App Store）。

## Risks / Trade-offs

- [Compose `UIKitView` 嵌入相机预览层的生命周期（进入/退出页面）处理不当会泄漏相机] → 统一在 Composable 销毁路径停止 session，模拟器+真机双验证
- [URL Scheme 跳转无法拿到支付结果] → 明确"确认中"反馈语义并写入 spec，充值余额以后端刷新为准
- [真机验证依赖用户签名安装] → 模拟器验证相机/通知主流程，真机流程文档化
- [iOS 通知权限弹窗时机打扰用户] → 仅在首次实际需要展示运行通知时请求

## Migration Plan

纯 iOS 侧行为替换（stub → 实现），无数据迁移；回滚即恢复对应 stub 文件。

## Open Questions

无阻塞问题；支付宝跳转参数细节、通知文案等实现级参数在设计阶段确定。

```

## docs/openspec/changes/ios-platform-features/tasks.md

- Source: docs/openspec/changes/ios-platform-features/tasks.md
- Lines: 1-20
- SHA256: 5667f1f343d1e31b63faf7d3f1ffac03c17c953c7240549d20ed533958d5eaeb

```md
## 1. 扫码（ios-scanning）

- [ ] 1.1 实现 iOS 扫码页：AVCaptureSession 相机流 + MetadataOutput 二维码识别，经 Compose `UIKitView` 嵌入 `QrScannerPage` iOS actual，扫码结果回填设备编号流程；验证模拟器（虚拟场景）编译运行与页面关闭路径
- [ ] 1.2 处理相机权限：Info.plist 声明 + 权限被拒时的明确提示路径；验证权限拒绝分支不崩溃

## 2. 运行通知（ios-notifications）

- [ ] 2.1 实现 `RunNotifications` iOS actual：UNUserNotificationCenter 设备状态/积分进度通知更新与清除，首次展示前请求权限；验证授权与未授权两条路径下任务运行不受影响

## 3. 支付跳转（ios-payment-launch）

- [ ] 3.1 实现 `payWithAlipay` iOS actual：canOpenURL 判安装 + orderInfo 拼装 alipays:// 跳转，返回与 Android 对齐的 `AlipayPayResult` 语义；验证已安装/未安装两分支反馈

## 4. 更新移交（ios-update-handoff）

- [ ] 4.1 实现 `AppUpdatePlatform` iOS actual：新版本提示跳转项目 Releases 页，`canInstallPackages`/`installApk` 等返回不支持语义；验证更新入口在 iOS 上可用且不报错

## 5. 收尾验证

- [ ] 5.1 iOS 真机（用户自签安装）端到端验证四个能力，Android CI 全量回归通过；记录验证证据于 change 目录

```

## docs/openspec/changes/ios-platform-features/.openspec.yaml

- Source: docs/openspec/changes/ios-platform-features/.openspec.yaml
- Lines: 1-2
- SHA256: e461a5cd9c263851dbb46471f9e3afd45a09338ec7b15a042283644a5ef6972b

```md
schema: spec-driven
created: 2026-09-19

```

## docs/openspec/changes/ios-platform-features/specs/ios-notifications/spec.md

- Source: docs/openspec/changes/ios-platform-features/specs/ios-notifications/spec.md
- Lines: 1-19
- SHA256: 80eb3b15b1fa767ce8f29b0dcbd1b8cb341eaf18e178b6f0e9ad81b38542567d

```md
## Purpose

在 iOS 上用系统通知展示设备运行与积分任务状态，对齐 Android 端"运行期间常驻展示状态"的用户可见行为（iOS 平台限制下以通知替代前台服务）。

## ADDED Requirements

### Requirement: iOS 运行状态通知展示与清除

iOS 端 SHALL 支持请求通知权限；获得权限后，设备开始/停止运行与积分任务积分变化时 SHALL 通过系统通知更新展示状态，任务结束后 SHALL 清除对应通知；未授权时 SHALL 静默跳过且不影响任务执行。

#### Scenario: 设备运行状态上报展示通知

- **WHEN** 已授权通知的 iOS 用户启动设备任务
- **THEN** 系统通知区展示设备运行中状态，设备停止后对应通知被清除

#### Scenario: 未授权不影响功能

- **WHEN** 用户拒绝通知权限
- **THEN** 设备与积分任务照常运行，仅无通知展示，应用不崩溃

```

## docs/openspec/changes/ios-platform-features/specs/ios-payment-launch/spec.md

- Source: docs/openspec/changes/ios-platform-features/specs/ios-payment-launch/spec.md
- Lines: 1-19
- SHA256: 928c33cb9cee770263290143eb5987db461dadac76482d776283a18851a6dd29

```md
## Purpose

iOS 上通过支付宝 URL Scheme 跳转完成充值支付，替代 Android SDK 调起方式，并向用户返回与 Android 语义一致的支付结果反馈。

## ADDED Requirements

### Requirement: iOS 通过支付宝跳转发起支付并反馈结果语义

iOS 端收到支付订单信息后 SHALL 拼装支付宝跳转地址并尝试唤起支付宝；成功唤起时 SHALL 以"支付结果确认中，请稍后刷新余额"类语义反馈（无法自动回查支付结果）；设备未安装支付宝或唤起失败时 SHALL 返回明确的失败信息，不崩溃。

#### Scenario: 跳转支付宝支付

- **WHEN** iOS 用户在充值流程发起支付且设备已安装支付宝
- **THEN** 支付宝被唤起进入支付流程，应用侧反馈支付结果确认中

#### Scenario: 未安装支付宝

- **WHEN** iOS 用户发起支付但设备未安装支付宝
- **THEN** 应用返回明确的支付失败提示，支付流程可安全重试

```

## docs/openspec/changes/ios-platform-features/specs/ios-scanning/spec.md

- Source: docs/openspec/changes/ios-platform-features/specs/ios-scanning/spec.md
- Lines: 1-24
- SHA256: 109403e3d9450e9913feec05c8fb657e222daa782d71611b8550713b56328f8d

```md
## Purpose

让 iOS 用户能在应用内直接扫描二维码完成设备添加，替代 Android 相机栈的 iOS 原生实现，行为与 Android 扫码页对齐。

## ADDED Requirements

### Requirement: iOS 扫码页提供相机取景与二维码识别

iOS 端扫码页 SHALL 调用系统相机进行实时取景并识别二维码，识别成功后 SHALL 将二维码内容回传给共享添加设备流程并关闭扫码页；相机不可用时 SHALL 给出明确的失败反馈。

#### Scenario: 扫码成功添加设备

- **WHEN** iOS 用户在添加设备页点击"扫一扫"并对准有效二维码
- **THEN** 二维码内容被识别并回填设备编号流程，扫码页关闭

#### Scenario: 相机权限被拒绝

- **WHEN** 用户拒绝相机权限后进入扫码页
- **THEN** 页面给出无法使用相机的明确提示，不崩溃

#### Scenario: 权限声明完整

- **WHEN** 首次触发扫码
- **THEN** 系统弹出相机权限申请且文案来自应用的权限声明配置

```

## docs/openspec/changes/ios-platform-features/specs/ios-update-handoff/spec.md

- Source: docs/openspec/changes/ios-platform-features/specs/ios-update-handoff/spec.md
- Lines: 1-14
- SHA256: c7cb10ad5ffa0f0ab639d010b42a3ad3562f928c13bff4bf40e430095f28c990

```md
## Purpose

iOS 无法安装 APK，本能力将"应用内更新"在 iOS 上改为新版本提示 + 跳转项目发布页下载，复用现有版本比较与更新检测流程。

## ADDED Requirements

### Requirement: iOS 更新提示跳转发布页

iOS 端检测到新版本时 SHALL 提示用户并支持跳转到项目发布页面下载新版本；"下载安装包到本地安装"等 Android 专属步骤 SHALL 在 iOS 上不可达且不报错。

#### Scenario: 发现新版本跳转下载

- **WHEN** iOS 应用检测到高于当前版本的发布版本且用户确认更新
- **THEN** 应用跳转到项目发布页面，用户可自行下载并签名安装新 IPA

```
