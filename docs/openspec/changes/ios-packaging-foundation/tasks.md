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
