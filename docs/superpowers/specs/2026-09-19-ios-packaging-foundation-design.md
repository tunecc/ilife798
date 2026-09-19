---
comet_change: ios-packaging-foundation
role: technical-design
canonical_spec: openspec
archived-with: 2026-09-19-ios-packaging-foundation
status: final
---

# 深度技术设计：iOS 打包构建基础

需求与验收标准见 OpenSpec change `ios-packaging-foundation`（proposal/specs 为 canonical spec），本文档细化实现方案、技术风险、测试策略与边界条件。

## 1. 架构与模块拓扑

```
iosApp/                                ← 新增（CMP 官方模板布局）
├── iosApp.xcodeproj/
├── iosApp/
│   ├── iOSApp.swift                   ← @main SwiftUI App；scenePhase → AppLifecycle
│   ├── ContentView.swift              ← UIViewControllerRepresentable → ComposeUIViewController
│   ├── Info.plist                     ← 基础声明（相机权限归 Change 2）
│   └── Assets.xcassets/               ← AppIcon 占位
├── Configuration/Config.xcconfig      ← BUNDLE_ID / DEPLOYMENT_TARGET=15.0；签名 TEAM 留空
└── scripts/build-unsigned-ipa.sh      ← 无签名 IPA 打包

shared/
├── build.gradle.kts                   ← +iosArm64()/iosSimulatorArm64()/binaries.framework()
└── src/iosMain/kotlin/com/github/ilife798/
    ├── MainViewController.kt          ← 平台入口：初始化 + ComposeUIViewController { App() }
    ├── (约 23 个 actual 文件，镜像 androidMain 包结构)
    └── buildConfig/                   ← Gradle 生成的 IosBuildConfig.kt（build 目录，不入库）
```

依赖方向：`iosApp`（Swift/ObjC 壳）→ Kotlin framework（shared iosMain + commonMain）→ 系统框架。Xcode Run Script 阶段执行 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`——该任务仅生成并嵌入 framework，与签名身份无关，`CODE_SIGNING_ALLOWED=NO` 下正常工作；这是官方模板链路，上游接受度最高。

备选被否：无 Xcode 工程纯命令行组装（不可维护）；Kotlin 2.4 SPM/Swift Export（实验性，CI 自动化风险高）。

## 2. iosMain actual 分级实现

### 2.1 真实现（基础工具类）

| expect | iOS actual 方案 | 边界条件 |
|---|---|---|
| `PersistentStorage` | `NSUserDefaults`（标准 suite），key 加 `ilife798.` 前缀防碰撞 | 首次读缺失返回 null；值为 String/Boolean/Int/Long 封装 |
| `Clipboard` | `UIPasteboard.general.string` | 非字符串环境返回 false |
| `TimeUtils` | `NSDateFormatter` / `Date.timeIntervalSince1970` | pattern 直传；时区用设备本地 |
| `Version` | `NSBundle.main` 的 `CFBundleShortVersionString`/version | 缺失返回 "0" |
| `Logger` | `NSLog`（含 tag 前缀） | — |
| `ImageConversion` | Skia `Image.makeFromEncoded(bytes)` → `asSkiaBitmap().asComposeImageBitmap()` | 解码失败返回占位空 bitmap，不抛异常 |
| `HttpClientProvider` / `createUpdateHttpClient` | `ktor-client-darwin` 引擎 | 与 Android OkHttp 引擎配置项保持同等超时语义 |
| `NetworkErrors` | Darwin 引擎异常映射为与 Android 相同的错误分类文案 | 未知异常归通用失败 |
| `Sponsor` | `UIApplication.open(SPONSOR_URL)`；URL 为 commonMain 常量 | 返回是否成功唤起 |
| `DynamicColorKey` | 读取系统全局强调色（`UITraitCollection` 当前色）转 Compose Color；取不到返回 null 走静态主题 | 色彩空间转换经 Skia |
| `WindowBlur` | 映射到 Compose `Modifier.blur()` 近似实现；参数为 0 时直通 | 性能敏感页面降级为无模糊 |
| `Toast` | 静默 no-op（iOS 无系统 Toast 组件；Change 2 评估 HUD） | — |

### 2.2 安全 stub（Change 2 替换）

- `QrScannerPage`：完整 Compose 占位页，提示「iOS 端扫码暂不支持，请手动输入设备编号」+ 返回按钮（导航不悬空）。
- `RunNotifications`：空对象（androidMain 注释已声明「其它平台为空实现」，属上游预留设计）。
- `Alipay.payWithAlipay`：返回 `AlipayPayResult(false, "iOS 端暂不支持应用内支付")`。
- `AppUpdatePlatform` 系列：`canInstallPackages=false`、`installApk=false`、`downloadApkToFile=false`、`currentAbis=空列表`、设置/通知/清理类 no-op。
- `BatteryOptimization`：`isIgnoringBatteryOptimizations()=true`（避免误导性引导 UI）、打开设置 no-op。

### 2.3 iOS 入口初始化序列

`MainViewController.kt`（对齐 `MainActivity.onCreate`）：

```kotlin
fun MainViewController(): UIViewController {
    AppStorage.instance = PersistentStorage()          // NSUserDefaults actual
    ApiConfig.init(IosBuildConfig.API_GATEWAY, IosBuildConfig.SIGN_SALT, IosBuildConfig.API_CID)
    return ComposeUIViewController { App() }
}
```

`DeviceTile.controller` 不设置（commonMain 注释已定义「未设置 = 不支持」语义）；`AppLifecycle` 由 `iOSApp.swift` 的 `scenePhase` 桥接（active → `notifyResumed()`，background → `notifyStopped()`）。

## 3. 配置注入

Gradle 任务 `generateIosBuildConfig`（shared 模块注册）：

1. 来源优先级：环境变量 `ILIFE798_API_GATEWAY` / `ILIFE798_SIGN_SALT` / `ILIFE798_API_CID` → 根目录 `secrets.properties` 的 `API_GATEWAY` / `SIGN_SALT` / `API_CID` → 空字符串。
2. 生成 `IosBuildConfig.kt`（object 常量）到 `shared/build/generated/iosBuildConfig/kotlin`，仅注册进 iosMain sourceSet；生成目录加入 `.gitignore`。
3. 任务挂接 iOS 编译任务依赖，保证增量正确性。

行为与 Android 一致：配置缺失时构建成功，应用侧按空网关处理；敏感值永不入库。

## 4. 无签名 IPA 打包

`iosApp/scripts/build-unsigned-ipa.sh`（`set -euo pipefail`）：

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath iosApp/build/iosApp.xcarchive archive \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
cp -R iosApp/build/iosApp.xcarchive/Products/Applications/iosApp.app "$TMP/Payload/"
ditto -c -k --keepParent Payload dist/ILife798-v"$VERSION"-unsigned-ios.ipa
```

- `$VERSION` 取自 androidApp `versionName`（与 Android 发布物同版本号）。
- `CODE_SIGN_IDENTITY=""` 显式置空，保证无任何签名身份的环境可打包。
- 产物校验：`unzip -l` 断言 `Payload/iosApp.app/Info.plist` 与主二进制存在。
- 无签名 IPA 设备不可直接安装（属预期，用户自行签名，README 提供指引）；模拟器验证用 `.app` 产物。

## 5. CI 设计（.github/workflows/ios.yml）

镜像 `android.yml` 结构：

| | validate | release |
|---|---|---|
| 触发 | PR/push(main) + workflow_dispatch | push tag `v*` |
| runner | `macos-latest`（公共仓库免费，预装 Xcode 16.x） | 同左 |
| 步骤 | JDK21 → Gradle 缓存 → secrets 生成 secrets.properties → 双架构编译 → 打包脚本 → IPA artifact | 同左 + 版本一致性校验（tag == `v$versionName`）→ `gh release view \|\| create` 幂等上传 |
| 权限 | `contents: read` | `contents: write` |

- concurrency 组 `ios-${{ github.workflow }}-...`，`cancel-in-progress: true`（同 android.yml）。
- Release 并发兜底：tag 触发时 android/ios 两工作流可能并发创建同一 Release；延续 `view || create` 写法，`create` 失败回退 `upload`，upload 带 `--clobber` 重试一次。
- 不引入第三方 action（Xcode 用 runner 默认版本；如漂移出问题再钉 `setup-xcode`）。

## 6. 测试与验证策略

1. **编译矩阵门**：`./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64`（spike 最前置，验证 AGP 9.4 新插件共存）+ `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest`（Android 零回归）。
2. **模拟器手动冒烟**（清单与结论记录进 change 目录）：安装 `.app` → 启动无崩溃 → 登录 → 手动输入设备编号添加设备 → 任务/账单/积分/设置页遍历 → 逐个触发 stub 入口（扫码/支付/更新/电池设置）确认不崩且有反馈。
3. **CI 断言**：打包脚本退出码；IPA 存在性与 Payload 结构；release job 版本一致性 `test "$GITHUB_REF_NAME" = "v$versionName"`。
4. **真机验证**：用户自签安装（端到端真实链路），不阻塞本 change 交付。
5. 明确不引入：iOS 模拟器单元测试、CI 模拟器冒烟（用户确认留在本地）。

## 7. 风险与回退

| 风险 | 缓解 |
|---|---|
| AGP 9.4 `androidMultiplatformLibrary` 与 iOS target 共存配置未知 | 任务 1.1 spike 最前置；必要时参考 KMP 兼容矩阵调整 DSL |
| Kotlin 2.4.20 模板 DSL 与既有模板记忆不符 | 以编译器提示为准校正，design 决策不受影响 |
| miuix 0.9.4-rc01 iOS 渲染异常 | 冒烟暴露后升版或局部降级，记录到 Change 2 |
| macos runner Xcode 大版本漂移 | 暂用默认；失败时钉版本 action |
| Release 并发创建竞争 | `view \|\| create` + upload 重试（§5） |
| 无签名 IPA 被误认为损坏 | README 写明签名指引（任务 5.2） |

回退：移除 iosMain/iosApp/ios.yml 与 gradle 增量即回到 Android-only；无数据迁移。
