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

## 5. 收尾回归与打包（tasks 5.1）

- Android CI 门：`spotlessCheck :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:testAndroidHostTest :androidApp:lintDebug` ✅
- iOS 双架构编译 ✅；无签名 IPA 打包 ✅（ARCHIVE SUCCEEDED）
- 文件边界：`.comet/` 0、androidApp 与 android.yml 0 次出现在变更清单 ✅
- 环境备注：本机 Xcode 工具链 strip shim 再次失效（shim 按调用路径分发导致定位失败），改链 CommandLineTools 实体 strip 后恢复；CI（macos runner）无此问题

## 6. 真机端到端（用户自测清单）

1. 扫码：识别一次回填、页面关闭（防重复回调验证）
2. 通知：前台横幅（delegate）+ 通知中心可见；停止后清除；拒绝权限任务照常
3. 支付：真实充值跳支付宝收银台；若跳转失败记录降级提示
4. 更新：检查更新弹窗 → 点更新 → Safari 打开 Jursin/ilife798/releases
