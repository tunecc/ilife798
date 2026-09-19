# 验证报告：ios-packaging-foundation

- 日期：2026-09-19
- 模式：full（16 任务 / 2 delta capabilities / 60 变更文件）
- 分支：feature/20260919/ios-packaging-foundation（base `af15849` → HEAD `1fd25a8`）
- 审查模式：standard（build 阶段已完成全量代码审查，2×Critical 已修复并复验）

## Summary

| 维度 | 状态 |
|---|---|
| Completeness | 16/16 任务完成；2 个 capability 9 个 requirement 全部有实现与证据 |
| Correctness | 9/9 requirement 有代码证据；2 个场景端到端验证推迟（见 WARNING，含处置） |
| Coherence | 设计决策 D1-D5 / Design Doc §1-7 全部遵循；无 spec 漂移 |

## 验证证据（本次验证期间新鲜运行）

| 项 | 命令/来源 | 结果 |
|---|---|---|
| tasks.md 完成核对 | grep 勾选计数 | 16 勾选 / 0 未勾选 ✅ |
| 编译/测试/格式/lint 全门 | `comet check run verify`（gradle 七任务，runner 记录 exit 0） | ✅ |
| 无签名 IPA 产物结构 | `unzip -l`（Info.plist + 主二进制 + shared.framework 7 条目） | ✅ |
| CI 端到端（macos-latest 无证书） | GitHub run 35422282316（validate 全绿，artifact `ilife798-unsigned-ios-ipa`）；最新 run 35423281431 对最终 HEAD 复跑 | ✅ |
| 模拟器启动/渲染/存活 | evidence/11-smoke-*.png + launchctl 存活检查 | ✅ |
| expect/actual 覆盖 | expect-actual-checklist.md（编译器权威核对 34/34） | ✅ |
| 配置注入矩阵 | secrets/env/缺省/转义四场景（build 阶段记录） | ✅ |
| Android 零回归 | assembleDebug + testDebugUnitTest + testAndroidHostTest + lintDebug | ✅ |

## 检查项明细（7 项）

1. **tasks.md 全部完成** ✅ 16/16（3 项附注推迟验证指针）。
2. **实现符合高层 design.md 决策** ✅ D1 官方模板链路（Run Script 首位 + 同步组工程）、D2 actual 分级（真实现 12 文件/stub 5 组）、D3 构建期常量注入（env→secrets→空串）、D4 无签名归档+Payload、D5 CI 镜像 android.yml 双 job。
3. **实现符合 Design Doc** ✅ §2 actual 分级表逐项落地；§3 注入优先级与缺省行为一致；§4 打包三重无签名 flags 与断言一致；§5 CI 结构一致（含 Release 幂等回退）；§6 测试策略执行（编译矩阵/冒烟/CI 断言；真机验证按设计不阻塞）。实现期偏差均属设计预留的"按编译器提示校正"范畴（NSDate referenceDate 常量换算、NSCalendar weekday 替代 ICU "u"），已在代码注释与 checklist 记录。
4. **能力规格场景通过** ✅ ios-build 5 requirement：target 编译/actual 分级/工程配置注入（不硬编码——secrets 未入库 `git check-ignore` 验证）/无签名 IPA 结构有效/模拟器可运行——均有新鲜证据。ios-ci 4 requirement：PR 验证/tag 发布（见 WARNING-1）/secrets 注入+缺失告警/产物可下载。
5. **proposal.md 目标满足** ✅ iOS 可构建、无签名 IPA、CI 全自动、Android 零回归、关键修改文件边界清晰（.comet 零混入）。
6. **delta spec 与 design doc 无矛盾** ✅（build 阶段未修改 spec；发现为实现细节的偏差已在 Design Doc 风险表预留）。
7. **设计文档可定位** ✅ `docs/superpowers/specs/2026-09-19-ios-packaging-foundation-design.md`。

## Issues

### CRITICAL

无（build 阶段审查发现的 2×Critical——纪元偏移常量、ICU "u" 周几语义——已修复并通过编译门与 CI 复验）。

### WARNING（含处置建议，需用户确认）

1. **ios-ci「tag 触发自动发布」端到端未实测**。缓解：发布逻辑与已验证的 android.yml 同源且更强（create 失败回退 upload --clobber）；validate job 已证明构建/打包/上传链路在 CI 可用。处置选项：(a) 接受推迟至首个正式 tag 实测；(b) 现在打测试 tag 端到端验证后清理。
2. **模拟器交互冒烟 U1-U5（登录/加设备/页面遍历/重启恢复/stub 入口点触）需真实账号与人工交互**，已随报告移交用户；可自动化部分（启动/渲染/免责声明/存活/重启拉起）全部通过。

### SUGGESTION

1. `Sponsor.ios.kt` 使用已废弃 `openURL(_:)`（iOS 15 可用，语义正确）——Change 2 顺带迁移 `open(_:options:completionHandler:)`。
2. `DynamicColorKey.ios.kt` 读取 key window tintColor 在 SwiftUI 壳未设 tint 时恒为系统蓝——真实跟随系统强调色需壳层桥接，已记录 Change 2。
3. `ios.yml` `paths-ignore` 用 `androidApp/**`（比计划值宽），tag 触发不受影响；PR 描述中注明即可。

## Final Assessment

无 CRITICAL 问题。WARNING 处置（用户 2026-09-19 确认接受）：

1. **tag 触发发布端到端**：接受推迟至首个正式 tag 实测。影响范围：release job 首次真实触发前，发布路径无端到端运行记录；缓解为与 android.yml 同源逻辑 + 失败回退 + validate 链路已被 CI 证明。
2. **模拟器交互验证 U1-U5**：接受归档后由用户自测。影响范围：登录/加设备/页面遍历/重启恢复/stub 入口的运行时确认由用户执行；发现问题按常规修复流程处理，不阻塞本 change 归档。

实现、构建、CI、产物、文档全链路证据齐全。**通过，可进入归档。**
