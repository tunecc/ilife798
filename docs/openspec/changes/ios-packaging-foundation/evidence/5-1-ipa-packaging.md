# 无签名 IPA 打包验证（tasks 5.1）

- 命令：`./iosApp/scripts/build-unsigned-ipa.sh`
- 结果：`** ARCHIVE SUCCEEDED **`，产物 `iosApp/build/dist/ILife798-v1.2.3-unsigned-ios.ipa`（14.6MB）
- 结构校验：`Payload/iosApp.app/Info.plist` 与 `Payload/iosApp.app/iosApp` 存在；`Payload/iosApp.app/Frameworks/shared.framework/` 已嵌入；`unzip -t` 完整性通过
- 签名状态：归档显式 `CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""`，产物不含签名（本机虽有 1 个身份，但 flags 显式禁用；CI 无签名环境为最终验证）

## 环境修复记录（Xcode 16.4 已移除 toolchain 内 strip）

- 现象：`linkReleaseFrameworkIosArm64` 失败——K/N 以绝对路径调用 `<toolchain>/usr/bin/strip`，Xcode 16.4 工具链已不再包含该二进制
- 本机修复：`ln -s /usr/bin/strip /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/strip`（本机 Xcode 由 Xcodes.app 安装，目录属主为当前用户，无需 sudo）
- CI 策略：不在脚本中预置系统修改；Task 14 于 GitHub macos runner 实测，若同因失败再在工作流中加入针对性缓解
