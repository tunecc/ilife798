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
