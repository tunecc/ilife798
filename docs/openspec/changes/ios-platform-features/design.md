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
