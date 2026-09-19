# expect/actual 覆盖核对清单（ios-packaging-foundation）

核对时间：2026-09-19。核对基准：`:shared:compileKotlinIosArm64` 与 `:shared:compileKotlinIosSimulatorArm64` 编译通过（34 expect 全部有 actual，编译器权威判定）+ 逐项人工核对。

| # | commonMain expect 文件 | 声明数 | iosMain actual 文件 | 分级 |
|---|---|---|---|---|
| 1 | PersistentStorage.kt | 1 | PersistentStorage.ios.kt | 真实现（NSUserDefaults，key 前缀 ilife798.） |
| 2 | Clipboard.kt | 1 | Clipboard.ios.kt | 真实现（UIPasteboard） |
| 3 | util/TimeUtils.kt | 5 | util/TimeUtils.ios.kt | 真实现（NSDateFormatter；周一=1..周日=7 语义对齐 Android） |
| 4 | Version.kt | 2 | Version.ios.kt | 真实现（NSBundle） |
| 5 | Logger.kt | 1 | Logger.ios.kt | 真实现（NSLog） |
| 6 | ImageConversion.kt | 1 | ImageConversion.ios.kt | 真实现（Skia 解码，失败占位不崩） |
| 7 | data/api/HttpClientProvider.kt | 1 | data/api/HttpClientProvider.ios.kt | 真实现（Ktor Darwin，JSON+默认头与 Android 对齐） |
| 8 | update/AppUpdatePlatform.kt | 12 | update/AppUpdatePlatform.ios.kt | 真实现（createUpdateHttpClient/HttpTimeout 60s）+ stub（其余 11 个安装/通知类） |
| 9 | data/viewmodel/NetworkErrors.kt | 1 | data/viewmodel/NetworkErrors.ios.kt | 真实现（NSURLErrorDomain 错误码映射，SSL 不重试） |
| 10 | ui/theme/DynamicColorKey.kt | 1 | ui/theme/DynamicColorKey.ios.kt | 真实现（key window tintColor；未就绪回退 null 静态主题） |
| 11 | ui/theme/WindowBlur.kt | 1 | ui/theme/WindowBlur.ios.kt | 真实现（契约 no-op，commonMain 注释「其它平台不处理」） |
| 12 | util/Sponsor.kt | 1 | util/Sponsor.ios.kt | 真实现（UIApplication.openURL + SPONSOR_URL） |
| 13 | ui/page/device/QrScannerPage.kt | 1 | ui/page/device/QrScannerPage.ios.kt | stub（占位页，明确提示 + 返回按钮） |
| 14 | RunNotifications.kt | 1 | RunNotifications.ios.kt | stub（空对象，commonMain 注释「其它平台为空实现」） |
| 15 | pay/Alipay.kt | 1 | pay/Alipay.ios.kt | stub（失败结果「iOS 端暂不支持应用内支付」） |
| 16 | Toast.kt | 2 | Toast.ios.kt | stub（静默 no-op） |
| 17 | util/BatteryOptimization.kt | 2 | util/BatteryOptimization.ios.kt | stub（恒 true + no-op，避免误导性引导 UI） |

合计：17 文件 / 34 声明，编译器核对通过。

Change 2（ios-platform-features）待替换项：#8 的 stub 部分（更新改为跳转发布页）、#13（AVFoundation 扫码）、#14（UNUserNotificationCenter 通知）、#15（支付宝 URL Scheme）、#16（Toast HUD 评估）。
