# Brainstorm Summary

- Change: ios-packaging-foundation
- Date: 2026-09-19

## 确认的技术方案

采用方案 A（官方 CMP 模板链路）：`iosApp/` Xcode 工程（SwiftUI 壳 + `ComposeUIViewController`）+ Run Script 调 `:shared:embedAndSignAppleFrameworkForXcode` 嵌入 framework；`shared` 声明 iosArm64/iosSimulatorArm64 target + `binaries.framework()`。iOS 最低部署目标 **iOS 15.0**（用户确认）。34 个 expect 的 iosMain actual 分级：基础工具类真实现（NSUserDefaults/UIPasteboard/NSDateFormatter/NSBundle/NSLog/Skia 解码/ktor-darwin/NetworkErrors/Sponsor 跳 SPONSOR_URL/DynamicColorKey/WindowBlur/Toast 静默），重交互安全 stub（QrScannerPage 占位页、RunNotifications、Alipay、AppUpdatePlatform、BatteryOptimization，归 Change 2 替换）。iOS 入口 `MainViewController.kt` 初始化序列对齐 MainActivity：AppStorage → ApiConfig.init → App()。配置注入：Gradle 任务从 `secrets.properties`（或环境变量 ILIFE798_API_GATEWAY/SIGN_SALT/CID）生成 `IosBuildConfig.kt` 仅入 iosMain，缺省空串。无签名打包：xcodebuild archive（CODE_SIGNING_ALLOWED=NO）→ Payload 组装 → `ILife798-v<version>-unsigned-ios.ipa`。CI `ios.yml` 镜像 android.yml 双 job，validate（PR/push，macos-latest，双架构编译+打包+IPA artifact）+ release（tag v*，版本校验+幂等 gh release 上传）；**CI 只做编译+打包，不加模拟器冒烟**（用户确认）。

## 关键取舍与风险

- 官方模板形态牺牲部分自动化简洁性，换上游 PR 接受度（关键诉求）
- AGP 9.4 `androidMultiplatformLibrary` 新插件与 iOS target 共存未验证 → 任务 1.1 spike 最前置
- Kotlin 2.4.20 模板 DSL 可能变动 → spike 按编译器提示校正
- miuix 0.9.4-rc01 iOS 渲染未验证 → 模拟器冒烟暴露，必要时升版
- tag 触发时 android/ios 两工作流并发创建 Release → `gh release view || create` + upload 重试幂等兜底
- macos runner Xcode 版本漂移 → 暂用默认版本，必要时钉版本
- Toast 静默为权宜（iOS 无系统 Toast 组件），Change 2 评估 HUD 方案

## 测试策略

编译矩阵（Android assembleDebug+testDebugUnitTest 零回归；iOS 双架构 compile）→ 模拟器手动冒烟清单（安装/登录/手动加设备/主要页面遍历/stub 入口不崩，证据存 change 目录）→ CI 断言（打包退出码、IPA 存在性、Payload 结构、release 版本一致性）→ 真机自签安装验证。

## Spec Patch

无——ios-build/ios-ci delta spec 已覆盖全部行为，不回写。
