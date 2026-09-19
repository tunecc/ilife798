# ios-ci Specification

## Purpose
定义 ILife798 的 iOS 持续集成与自动发布能力：GitHub Actions 在 macOS runner 上全自动完成 iOS 构建验证与未签名 IPA 发布，全程无需证书签名与人工介入。

## Requirements

### Requirement: PR 与主分支推送自动验证 iOS 构建

仓库 SHALL 提供 iOS 持续集成工作流：PR 与主分支推送时在 macOS runner 上自动执行 iOS 构建验证（编译与未签名打包），构建失败时工作流失败。

#### Scenario: PR 触发构建验证

- **WHEN** 提交或更新一个 Pull Request
- **THEN** macOS runner 自动执行 iOS 构建验证，结果反映在该 PR 的 CI 状态上

#### Scenario: 构建失败阻断

- **WHEN** iOS 构建验证中出现编译错误或打包失败
- **THEN** 工作流失败并暴露失败日志

### Requirement: tag 触发自动发布未签名 IPA

当推送 `v*` 格式的版本 tag 时，工作流 SHALL 自动构建并发布未签名 IPA，上传到对应的 GitHub Release；已存在的 Release SHALL 增补资产而不覆盖既有资产。

#### Scenario: 版本 tag 发布 IPA

- **WHEN** 推送符合版本规范的 tag
- **THEN** 该 tag 的 GitHub Release 中包含本次构建的未签名 IPA 文件

#### Scenario: 无签名环境可发布

- **WHEN** CI 构建环境中不存在任何 Apple 签名证书与描述文件
- **THEN** 发布流程正常完成并产出未签名 IPA

### Requirement: 敏感配置通过仓库密钥注入

iOS 工作流 SHALL 使用仓库 secrets 生成构建期配置（与 Android 工作流同源），SHALL NOT 将敏感配置值硬编码在工作流或仓库文件中；secrets 未配置时 SHALL 呈现明确的失败或可诊断行为。

#### Scenario: secrets 齐全时构建成功

- **WHEN** 仓库已配置网关相关 secrets 并触发 iOS 工作流
- **THEN** 工作流使用注入的配置完成构建并产出 IPA

### Requirement: 构建产物可获取

每次 iOS 工作流构建 SHALL 将未签名 IPA 上传为可下载的工作流产物，供用户在发布前获取测试。

#### Scenario: 验证构建产物可下载

- **WHEN** 一次 PR/push 触发的 iOS 构建验证成功
- **THEN** 该次运行页面上可下载未签名 IPA 产物
