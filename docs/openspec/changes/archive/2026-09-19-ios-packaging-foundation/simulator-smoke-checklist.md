# iOS 模拟器冒烟清单与结论（ios-packaging-foundation）

- 环境：macOS（darwin arm64）/ Xcode 16.4 / iPhone 14 Pro 模拟器（iOS 18.x runtime）/ Debug 模拟器构建
- 证据：`evidence/11-smoke-launch.png`（启动后首页 + 免责声明）、`evidence/11-smoke-still-alive.png`（30s 后仍存活）

## 已验证（本 change 范围，agent 可自动执行部分）

| # | 项目 | 结果 | 说明 |
|---|---|---|---|
| 1 | 启动无崩溃，共享界面渲染 | ✅ 通过 | 首次启动修复 `CADisableMinimumFrameDurationOnPhone` Info.plist 缺失（CMP 1.12 PlistSanityCheck 强制要求）后正常；首页统计卡、设备区、底部导航（首页/任务/我的）与 miuix 控件渲染正常 |
| 2 | 首次启动免责声明弹窗 | ✅ 通过 | 说明 commonMain `App()` + AppViewModel + PersistentStorage（NSUserDefaults 真实现）初始化链路工作 |
| 3 | 进程稳定存活 | ✅ 通过 | 启动 30s 后 launchctl 仍存活、前后台无崩溃日志 |
| 4 | 杀掉重启可再次启动 | ✅ 通过 | `simctl terminate` + `launch` 重新拉起正常 |
| 5 | iPhone 14 Pro 模拟器构建（Debug） | ✅ 通过 | xcodebuild 无签名构建 `** BUILD SUCCEEDED **`，`Frameworks/shared.framework` 已嵌入 |

## 用户验证项（需真实账号/交互，移交 verify 阶段由用户执行）

| # | 项目 | 依据 |
|---|---|---|
| U1 | 登录成功（Darwin 引擎请求可达后端）；断网时呈现网络错误文案而非崩溃 | 需要 API_GATEWAY 真实值与账号 |
| U2 | 手动输入设备编号添加设备成功；任务/账单/积分/设置页遍历，时间与版本号显示正常 | 需交互与账号 |
| U3 | 登录 → 杀掉应用重启 → 登录态与设置项恢复（NSUserDefaults 持久化） | 需先登录 |
| U4 | 扫码入口 → 占位页出现 + 返回可回退；支付入口反馈「iOS 端暂不支持应用内支付」；更新入口无崩溃；电池优化入口无跳转无崩溃；赞助入口 Safari 打开 afdian 链接；Toast 场景静默不崩 | stub 入口逐个点触 |
| U5 | 设置页复制任意文本 → 粘贴板内容一致（UIPasteboard） | 需交互 |

## 结论

本 change 交付范围内的可自动化验证全部通过；启动崩溃根因（Info.plist 缺 `CADisableMinimumFrameDurationOnPhone`）已修复并回归。U1-U5 不阻塞本 change 进入 verify 阶段，随 verify 一并请用户确认。
