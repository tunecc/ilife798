## Purpose

iOS 无法安装 APK，本能力将"应用内更新"在 iOS 上改为新版本提示 + 跳转项目发布页下载，复用现有版本比较与更新检测流程。

## ADDED Requirements

### Requirement: iOS 更新提示跳转发布页

iOS 端检测到新版本时 SHALL 提示用户并支持跳转到项目发布页面下载新版本；"下载安装包到本地安装"等 Android 专属步骤 SHALL 在 iOS 上不可达且不报错。

#### Scenario: 发现新版本跳转下载

- **WHEN** iOS 应用检测到高于当前版本的发布版本且用户确认更新
- **THEN** 应用跳转到项目发布页面，用户可自行下载并签名安装新 IPA
