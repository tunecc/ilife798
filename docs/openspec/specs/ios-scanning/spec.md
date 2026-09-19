# ios-scanning Specification

## Purpose
让 iOS 用户能在应用内直接扫描二维码完成设备添加，替代 Android 相机栈的 iOS 原生实现，行为与 Android 扫码页对齐。

## Requirements

### Requirement: iOS 扫码页提供相机取景与二维码识别

iOS 端扫码页 SHALL 调用系统相机进行实时取景并识别二维码，识别成功后 SHALL 将二维码内容回传给共享添加设备流程并关闭扫码页；相机不可用时 SHALL 给出明确的失败反馈。

#### Scenario: 扫码成功添加设备

- **WHEN** iOS 用户在添加设备页点击"扫一扫"并对准有效二维码
- **THEN** 二维码内容被识别并回填设备编号流程，扫码页关闭

#### Scenario: 相机权限被拒绝

- **WHEN** 用户拒绝相机权限后进入扫码页
- **THEN** 页面给出无法使用相机的明确提示，不崩溃

#### Scenario: 权限声明完整

- **WHEN** 首次触发扫码
- **THEN** 系统弹出相机权限申请且文案来自应用的权限声明配置
