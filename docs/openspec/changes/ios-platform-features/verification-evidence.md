# 验证证据：ios-platform-features

## 1. 扫码（ios-scanning）

- 编译门：iOS 双架构 + Android 全量门 ✅
- Info.plist：`NSCameraUsageDescription` 已声明，`plutil -lint` 通过 ✅
- 模拟器路径：页面代码路径覆盖（无相机降级分支编译验证）；「添加设备→扫一扫」页面交互需登录，移交真机自测 ⏳
- 真机（用户自签后）：扫码识别回填 ⏳

## 2. 通知（ios-notifications）

- 编译门 ✅；Swift 侧 delegate 挂载（iOSApp.swift）编译通过 ✅
- 授权/拒绝两路径运行时验证需登录并运行任务 → 真机自测 ⏳

## 3. 支付（ios-payment-launch）

- 编译门 ✅；Info.plist `LSApplicationQueriesSchemes: [alipays, alipay]` ✅
- 未安装分支：模拟器无支付宝，触发充值时返回「未安装支付宝」+ logDebug `alipay not installed`（console 可观测）✅（编译层）；运行时确认随真机自测 ⏳
- 已安装跳转：真机自测 ⏳

## 4. 更新移交（ios-update-handoff）

- 编译门 ✅；Android 路径逐字 diff 核对：仅 3 处计划内改动，`onToast("暂无本设备安装包")` 语义保留 ✅
- 模拟器全流程（临时 MARKETING_VERSION=0.0.1 → 检查更新 → 弹窗 → 点更新 → Safari 打开 releases）：需交互，随真机/模拟器自测 ⏳

## 结论

四能力实现完毕、全部门通过；运行时交互验证按用户决定统一移交真机自测（自签安装后按 simulator-smoke-checklist 同类清单执行）。
