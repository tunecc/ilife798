# 全量回归与 PR 边界核对（tasks 7.1）

## 本地全量门（2026-09-19，JAVA_HOME=JBR 21）

| 门 | 命令 | 结果 |
|---|---|---|
| Android 门（与 android.yml validate 同口径） | `spotlessCheck :androidApp:testDebugUnitTest :shared:testAndroidHostTest :androidApp:lintDebug` | ✅ BUILD SUCCESSFUL |
| iOS 双架构编译 | `:shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64` | ✅ BUILD SUCCESSFUL |
| 无签名 IPA 打包 | `./iosApp/scripts/build-unsigned-ipa.sh` | ✅ ARCHIVE SUCCEEDED + 产物结构断言通过 |
| 模拟器运行 | 见 simulator-smoke-checklist.md | ✅ 启动/渲染/存活通过 |

## CI 端到端

- draft PR（fork 内）：tunecc/ilife798#1，run 35420876034，validate job 于 macos-latest 实跑（结果见 PR CI 状态；release job 的 tag 触发路径待首个正式 tag 验证——`view || create` + `upload --clobber` 幂等逻辑与 android.yml 同源）。

## PR 文件边界核对（base af15849）

- 改动仅落在：`shared/`（build.gradle.kts + iosMain 18 文件）、`iosApp/`（Xcode 工程 + 脚本）、`.github/workflows/ios.yml`、`gradle/libs.versions.toml`、`README.md`、`docs/openspec/changes/ios-packaging-foundation/`（change 文档与证据）
- `.comet/` 混入数：0；`androidApp/**`、`.github/workflows/android.yml`：零改动 ✓
- 正式 PR 上游建议：以本分支 cherry-pick 或只合并以下关键路径，排除 Comet/OpenSpec 工作流文档（用户决定 PR 拆分粒度）：
  - shared/build.gradle.kts、shared/src/iosMain/**、gradle/libs.versions.toml
  - iosApp/**、.github/workflows/ios.yml、README.md（可按需）
