## Purpose

定义 ILife798 的 iOS 目标构建能力：`shared` 模块必须能在 iOS 平台完整编译并承载共享 Compose UI，本地与 CI 均可产出结构有效、可供用户自行签名的未签名 IPA，且不影响 Android 构建。

## ADDED Requirements

### Requirement: shared 模块提供 iOS target

`shared` 模块 SHALL 声明 iOS 设备（iosArm64）与 iOS 模拟器（iosSimulatorArm64）编译目标，使 commonMain 的全部共享代码（UI、导航、ViewModel、数据层）可在 iOS 平台编译。

#### Scenario: iOS target 编译通过

- **WHEN** 在 macOS 上执行 shared 模块的 iOS 编译任务（含模拟器与设备架构）
- **THEN** commonMain 全部代码编译成功，无缺失 actual 的编译错误

#### Scenario: Android target 保持不变

- **WHEN** 查看或执行 Android 构建配置与任务
- **THEN** Android target 的配置、产物与变更前完全一致

### Requirement: iOS 平台 actual 完整且行为分级

commonMain 中每一个 expect 声明 SHALL 在 iosMain 中存在对应 actual，并按以下分级提供行为：

- **基础工具类 SHALL 真实现**：持久化存储（映射到系统键值存储）、剪贴板复制、时间获取与格式化、应用版本号（来自系统包信息）、日志输出、HTTP 客户端引擎（iOS 原生引擎）等基础能力在 iOS 上真实可用。
- **重交互能力 SHALL 安全空实现**：扫码页、运行通知、支付宝支付、应用内更新安装、电池优化、前台保活等能力在 iOS 上以空实现或"明确反馈暂不支持"的方式满足编译与运行，不得导致崩溃。

#### Scenario: 基础工具类真实可用

- **WHEN** iOS 应用写入一条持久化数据后重启应用
- **THEN** 该数据从系统键值存储中恢复，登录态与设置不丢失

#### Scenario: 空实现能力不崩溃

- **WHEN** iOS 用户触发扫码、支付、应用内更新等 Android 专属功能入口
- **THEN** 应用保持稳定（无操作、返回失败结果或给出"暂不支持"反馈），不发生崩溃

### Requirement: iOS 应用工程承载共享 UI 并注入配置

项目 SHALL 包含一个 iOS 应用工程，启动时展示 commonMain 的共享 `App()` 界面，并以与 Android 一致的方式注入 API 网关、签名盐值与客户端标识配置；配置值 SHALL 不硬编码提交到仓库。

#### Scenario: 共享 UI 正常展示

- **WHEN** 在 iOS 模拟器启动应用
- **THEN** 显示与 Android 一致的共享界面，可完成登录、手动输入设备编号添加设备、查看任务/账单/积分等核心流程

#### Scenario: 配置来源与 Android 对齐

- **WHEN** 本地或 CI 构建 iOS 应用时提供了网关配置（与 Android secrets.properties 同源）
- **THEN** iOS 应用使用注入的配置访问后端；未提供时构建仍可完成，应用侧按配置缺失处理

### Requirement: 产出未签名 IPA

项目 SHALL 提供无证书签名的打包方式：归档产物 SHALL 生成包含 iOS 应用目录结构（Payload 布局）的未签名 `.ipa` 文件，使持有证书的用户可以使用常规签名工具自行签名安装；打包过程 SHALL 不要求任何 Apple 签名身份。

#### Scenario: 本地打包产出有效 IPA

- **WHEN** 在具备 Xcode 的 macOS 上执行无签名打包
- **THEN** 产出未签名 `.ipa`，其内部 Payload 结构有效，可被签名工具接受并完成签名

#### Scenario: 无签名环境可打包

- **WHEN** 构建环境中不存在任何 Apple 签名证书与描述文件
- **THEN** 打包流程正常完成，不因签名缺失而失败

### Requirement: 打包结果可本地验证

无签名 IPA 的产出 SHALL 可在本地（模拟器）验证：构建出的应用 SHALL 可安装到 iOS 模拟器并启动，核心流程可用。

#### Scenario: 模拟器安装运行

- **WHEN** 将构建产物安装到 iOS 模拟器并启动
- **THEN** 应用正常启动且共享界面可用
