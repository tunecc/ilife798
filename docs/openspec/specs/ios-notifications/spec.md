# ios-notifications Specification

## Purpose
在 iOS 上用系统通知展示设备运行与积分任务状态，对齐 Android 端"运行期间常驻展示状态"的用户可见行为（iOS 平台限制下以通知替代前台服务）。

## Requirements

### Requirement: iOS 运行状态通知展示与清除

iOS 端 SHALL 支持请求通知权限；获得权限后，设备开始/停止运行与积分任务积分变化时 SHALL 通过系统通知更新展示状态，任务结束后 SHALL 清除对应通知；未授权时 SHALL 静默跳过且不影响任务执行。

#### Scenario: 设备运行状态上报展示通知

- **WHEN** 已授权通知的 iOS 用户启动设备任务
- **THEN** 系统通知区展示设备运行中状态，设备停止后对应通知被清除

#### Scenario: 未授权不影响功能

- **WHEN** 用户拒绝通知权限
- **THEN** 设备与积分任务照常运行，仅无通知展示，应用不崩溃
