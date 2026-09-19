## 1. iOS Target 最小可编译（Spike 优先）

- [x] 1.1 `shared/build.gradle.kts` 声明 `iosArm64()` + `iosSimulatorArm64()` target 与 framework 导出配置，`gradle/libs.versions.toml` 按需新增依赖；执行 `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64` 验证 commonMain 在 iOS 编译通过（若报缺 actual 属预期，转入 1.2） <!-- comet-task:2b1aefdd-eea5-4b88-830a-1de3ae22a0be -->
- [x] 1.2 按 commonMain 的 34 个 expect 清单在 `shared/src/iosMain` 建立分级 actual 骨架（基础工具类暂以可编译最简实现占位、重交互类空实现），验证 1.1 的编译任务全部通过 <!-- comet-task:eee405d9-9fac-499f-8043-7559e068f280 -->
- [x] 1.3 执行 `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` 验证 Android 构建与测试零回归 <!-- comet-task:aa93ea45-8fc6-47a4-a503-a01a36e623b1 -->

## 2. 基础工具类 iOS 真实现

- [x] 2.1 实现 `PersistentStorage`（NSUserDefaults）与 `Clipboard`（UIPasteboard），并用 iOS 模拟器单元测试或手动验证写入/读取/重启恢复 <!-- comet-task:ba34eeb1-912a-4b7a-a07c-23a8dbd2185f -->
- [x] 2.2 实现 `TimeUtils`、`Version`（NSBundle）、`Logger`、`ImageConversion`（Skia 解码）、`NetworkErrors`、`DynamicColorKey`、`WindowBlur` 的 iOS actual，验证编译通过且各功能在模拟器可用 <!-- comet-task:535f9eae-29a1-4210-9260-133149a361ef -->
- [x] 2.3 引入 `ktor-client-darwin` 并实现 `HttpClientProvider`、`createUpdateHttpClient`，验证模拟器上登录请求可达后端 <!-- comet-task:80f05ba9-74e6-4c29-9860-774f638ef926 -->

## 3. 重交互功能安全 stub

- [x] 3.1 实现 `QrScannerPage` iOS 占位页（明确提示暂不支持）、`RunNotifications` 空对象、`Alipay` 返回失败结果、`AppUpdatePlatform` 系列/`BatteryOptimization` 空实现、`Sponsor` 打开网页链接、`Toast` 静默，验证编译与模拟器运行中触发这些入口不崩溃 <!-- comet-task:f3e4bfc4-86d4-48af-96af-47449d3f53be -->
- [x] 3.2 核对 expect/actual 覆盖完整性（commonMain 每个 expect 在 iosMain 均有 actual），记录核对清单于 change 目录 <!-- comet-task:5d4a473e-01fc-46a6-a8b1-22ae843d4a77 -->

## 4. 配置注入与 iosApp 工程

- [x] 4.1 实现 Gradle 配置注入：从 `secrets.properties`（或环境变量）生成 iOS 构建期常量（缺省空字符串，与 Android 行为一致），验证有/无配置两种构建均成功 <!-- comet-task:450c0e8c-470d-49c3-b720-72d57f527ca2 -->
- [x] 4.2 按 CMP 官方模板新建 `iosApp/` Xcode 工程（xcodeproj + xcconfig + Gradle 框架嵌入脚本 + Info.plist），`ComposeUIViewController` 承载共享 `App()`，启动时注入网关配置 <!-- comet-task:686d03b1-908f-4e84-a071-0b6cef6537cb -->
- [x] 4.3 本地模拟器安装运行：登录、手动输入设备编号添加设备、任务/账单/积分页面可用；记录验证截图或结论于 change 目录 <!-- comet-task:f862ac93-17c4-4d1a-90b2-ba7b7b84e45c -->

## 5. 无签名 IPA 打包

- [x] 5.1 编写打包脚本（`xcodebuild archive` 无签名归档 → Payload 组装 → 未签名 IPA），验证本地产出 IPA 且 Payload 结构有效（可被签名工具接受） <!-- comet-task:d40472ed-fb67-4e7b-8417-0d16ea81a7c0 -->
- [x] 5.2 在 README 补充 iOS 本地构建与用户自行签名安装说明 <!-- comet-task:6a5674cb-3d96-4df3-80a7-d09f4ef77f5b -->

## 6. GitHub Actions 全自动打包

- [x] 6.1 新增 `.github/workflows/ios.yml`：validate job（PR/push，macos runner，JDK 21 + Gradle 缓存 + secrets 注入 + iOS 构建验证 + IPA artifact 上传）；验证 push 后 Actions 运行成功且产物可下载 <!-- comet-task:79fd51a7-7e07-4c1e-b309-c67cc99a467f -->
- [x] 6.2 实现 release job（tag `v*`：版本一致性校验、构建 IPA、`gh release view || create` 幂等发布到 GitHub Release），验证一次 tag 触发的端到端发布（可用测试 tag） <!-- comet-task:a2808944-fb3c-4ce1-ac89-eb8f8532bd25 -->

## 7. 收尾验证

- [x] 7.1 全量回归：Android CI 任务（spotlessCheck/test/lint）与 iOS 构建验证在本仓通过；确认 PR 关键修改文件清单（shared/iosMain、iosApp、ios.yml、gradle 配置）与 Comet 工作流文件完全分离 <!-- comet-task:ab66c304-ea4c-418c-b99d-3d07a166f9f5 -->
