# KMP 迁移与 asr 模块（实施计划）

目标：把 `phone/` 从单模块安卓应用迁成 KMP（shared + androidApp），并新增 `feature/asr`。

## 步骤

1. 工具链对齐 LoostoneCenter：Gradle 9.1.0、AGP 8.10.1、Kotlin 2.3.20、CMP 1.11.1、Ktor 3.5.2、coroutines 1.11.0、serialization 1.11.0。✅
2. 目录：`phone/{settings.gradle.kts, gradle/libs.versions.toml, shared/, androidApp/, feature/asr/}`；旧 `phone/:app` 删除。✅
3. `shared`：CMP 界面 + 状态机 + Ktor 客户端 + 本地排队；`expect` 出录音、键值存储、文件、mermaid 渲染、录音权限。✅
4. `androidApp`：MainActivity、清单与主题、mermaid 资源。✅（权限与 MediaRecorder 实际实现放在 shared/androidMain，因为它们是 shared 的 actual）
5. `feature/asr`：`AsrService.transcribe(audio, route)`；`ApiAsr` 走 `/asr/v1/audio/transcriptions`；`NativeAsr` 为 expect/actual，Android 恒不支持，iOS 用 SFSpeechRecognizer。✅（iOS 实际实现待 iMac 编译验证）
6. 设置页加 route 三选一（默认 Auto），提交带 `asr_provider` / `asr_model`。✅
7. iOS 侧：`iosApp` 薄壳 + iOS 录音与 mermaid 渲染实现，在 iMac 上编译验证。⏳（shared/asr 的 iosMain 已写，2026-11-09 在 iMac 上用 iOS-target 校验工程编译通过 iosSimulatorArm64 与 iosArm64；`iosApp` Xcode 壳未建）

## 验收

- 三星真机：登录 → 录音 → 转写（Auto 落 api）→ 提交 → 成稿渲染；断网排队仍可用。✅ 2026-11-08 23:59 实测成稿（`通用（兜底）`），断网排队沿用旧逻辑未回归重测。
- 提交记录里 `asr_provider=funasr`、`asr_model=fun-asr-nano`。✅ 库中 31f35806 已带两列。
- iMac：`compileKotlinIosSimulatorArm64` / `compileKotlinIosArm64` 通过。✅ 2026-11-09（Xcode 26.3，Kotlin/Native 2.3.20）
- 复制到设备的界面与旧版一致：录音、列表、详情、mermaid 渲染。✅ 截图 `.dsh-tmp/kmp1.png`、`.dsh-tmp/rec3.png`

## 已知偏差

- `shared` 用显式 Maven 坐标（`org.jetbrains.compose.*`）而不是 `compose.*` DSL，与 LoostoneCenter 保持一致。
- Android 上录音权限与 MediaRecorder 是 shared 的 `actual`，放 `shared/androidMain`；`androidApp` 只负责入口与清单，比计划里的「薄壳放平台实现」更薄。
- iMac 上没有 Android SDK，平台插件无法配置，iOS 编译验证用一个只含 iOS target 的临时校验工程（源码与仓库同源），不是完整仓库构建。

## 风险

- Windows 上不能编译 iOS target，iOS 侧只能靠 iMac 验证。
- CMP 1.11 与 Xcode 版本匹配需在 iMac 上确认。
- 迁移期间功能等价性要逐屏对照，避免界面回退。
