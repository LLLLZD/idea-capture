# phone/ 生产级对齐计划（以 LoostoneCenter 为唯一参照）

> 依据：`D:/LoostoneCenter` 的 AGENTS.md、scripts/token-guard.mjs、shared/presentation/theme/{AppTheme,ColorScheme,Typography,AppSpacing}.kt、shared/di/KoinModule.kt、androidApp/build.gradle.kts、iosApp/（iOSApp.swift、Config/App.xcconfig、scripts/*.sh、xcodeproj）。
> 原则：能照搬就照搬，不另辟路线；与 LoostoneCenter 的差异必须写在本文件里。

## 目标

1. 视觉值全部走主题体系：presentation/theme 四件套 + token-guard 构建期门禁，违规即编译失败。
2. 依赖注入走 Koin 4.2.2：shared 的 `di/KoinModule.kt` + `expect val platformModule`，Android/iOS 各自 startKoin。
3. 界面按 ViewModel + State 组织，不再用单例 god object（现在的 AppState）。
4. 编译/测试/装机一律走 IDE MCP（android-build-via-ide）：`ide_sync_files`/`ide_reload_project` → `ide_build_project` → 运行配置 `androidAppForAgent`。
5. iOS 建 `phone/iosApp` Xcode 壳（参照 LoostoneCenter/iosApp），用 `ios-remote-build` 在 iMac 编译。

## 一、主题层（照搬结构，换掉品牌值）

| 新建文件（phone/shared/src/commonMain/kotlin/top/lzdrichard/idea/…） | 参照 |
| --- | --- |
| `presentation/theme/ColorScheme.kt` | LoostoneCenter ColorScheme.kt（Dark/Light + AppSemantic 语义色 + AppDecor 装饰色 + LocalAppDecor） |
| `presentation/theme/Typography.kt` | 五档 10/12/14/16/20，同名槽位 |
| `presentation/theme/AppSpacing.kt` | 9 档 xxs…xlLg + LocalAppSpacing + AppSpacing.current + LocalTopBarInset |
| `presentation/theme/AppTheme.kt` | AppShapes（五槽 + mini/listCard）、AppThemeMode、`IdeaCaptureTheme(content)` 包 CompositionLocalProvider + MaterialTheme |

品牌值：保留「随记」现有暖沙底 + 墨绿主色（Light 为默认），Dark 只做结构（后续设计稿再校准）；所有页面禁止再写 Color(0x…)/RoundedCornerShape(数字)/fontSize = 数字/sp(数字)。

## 二、token 门禁

- 复制 `D:/LoostoneCenter/scripts/token-guard.mjs` → `phone/scripts/token-guard.mjs`（扫描目录改 `shared/src`）。
- 复制根 build.gradle.kts 里的 `tokenGuard` Exec 任务与 `subprojects { tasks.matching {...}.configureEach { dependsOn(tokenGuard) } }`，工作目录/路径指向 `phone/`。
- 验收：故意在页面写 `Color(0xFF000000)` → 构建失败并给出 e: file:/// 行号；删掉后恢复绿。
- 豁免用法与参照一致：行尾 `// token-guard:allow <理由>`；胶囊 `RoundedCornerShape(50)`。

## 三、Koin

- 新增依赖（catalog）：`koin-core`、`koin-compose`、`koin-compose-viewmodel`、`lifecycle-viewmodel`、`lifecycle-viewmodel-compose`、`kotlinx-coroutines-android`。
- `shared/src/commonMain/…/di/KoinModule.kt`：`sharedModule = module { single { Prefs() }; single { Api(get()) }; single { AsrService(NativeAsrEngine(), ApiAsrRouter(get())) }; viewModel { LoginViewModel(get(), get()) } … }`；`expect val platformModule: Module`。
- androidMain `di/PlatformModule.android.kt`：`actual val platformModule = module { single { AudioRecorder() }; single { PlatformStore() } }`。
- iOS `di/PlatformModule.ios.kt` 同上。
- 壳启动：androidApp `MainApplication.onCreate()` → `startKoin { androidContext(this@MainApplication); modules(sharedModule, platformModule) }`（参照 LoostoneCenter 镜像）；iOS `iOSApp.init()` 调 Kotlin 侧 `initializeIosApp()`。
- 单测：`shared/src/commonTest/…/di/KoinAssemblyTest.kt` 只验证装配（koin-test 的 verify，或用 `KoinPlatformTools` 简化），参照 LoostoneCenter 同名测试。

## 四、界面重构（AppState → ViewModel）

- `presentation/AppHolder.kt`（等价 base/common 的 AppHolder）：`applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`。
- `presentation/feature/login/LoginViewModel.kt`、`presentation/feature/templates/TemplatesViewModel.kt`、`presentation/feature/home/HomeViewModel.kt`（录音 + 排队 + 列表）、`presentation/feature/detail/DetailViewModel.kt`。
- 每个 VM 暴露 `data class XxxUiState` + `StateFlow`，UI 用 `collectAsState()`；写操作走 VM 方法。
- `presentation/components/`：`PrimaryButton`（无水波纹）、`AppCard`（主题圆角/间距）、`LoadingBlock` 等复用组件；禁止每个页面各写一份相似组件。
- `presentation/App.kt`：`IdeaCaptureTheme { AppNavHost() }`，导航用 `navigation-compose 2.9.2`（与参照同版本）或简单的 Screen 状态机——**默认照搬 navigation-compose**。
- 删除 `ui/AppState.kt`、把 `ui/Screens.kt` 拆成 `presentation/feature/*/XxxScreen.kt`。

## 五、平台层归位

- `data/Platform.kt`（现在混在 data 包的 expect）拆成 `platform/`：`PlatformAudio`、`PlatformFile`、`PlatformStore`、`platformHttpClient`，Android/iOS actual 同名分目录（参照 base/network + base/common 的分层思路，但不新建 Gradle 模块）。
- 说明：不新建 `base:log`/`base:common`/`base:network`/`base:permission` 四个模块——我们只有 3 个模块、无多渠道，拆模块的收益低于维护成本；如要照搬模块级结构，告诉我再拆。

## 六、编译链路（一律 IDE）

- `.idea/runConfigurations/androidAppForAgent.xml`（Android App 类型，参照 android-build-via-ide 的 references/run-configs.md）：构建+安装+启动一次完成。
- 每次改 Gradle/依赖 → `ide_reload_project`；只改源码 → `ide_sync_files`；编译 → `ide_build_project`；装机 → `ide_run_tests(target="androidAppForAgent") `。
- 命令行 gradle 只在 IDE 工具明确不可用时使用，且必须先告知用户。

## 七、iOS 壳（参照 LoostoneCenter/iosApp）

- 新建 `phone/iosApp/`：`Config/App.xcconfig`（`#include? "Secrets.xcconfig"`）、`iosApp/{iOSApp.swift, Info.plist, Assets.xcassets, iosApp.entitlements}`、`iosApp.xcodeproj`（单 target、单 scheme `iosApp`）、`scripts/{build-ios-app.sh, build-kotlin-framework.sh}`。
- Xcode 构建阶段脚本调 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`（无 node 时自动 `-PskipTokenGuard`）。
- `iOSApp.swift`：`IosAppKt.initializeIosApp()` + `ComposeView` 承载 `IosAppKt.mainViewController()`（我们在 shared/iosMain 已有 MainViewController，改名为 mainViewController 对齐）。
- DEVELOPMENT_TEAM 用 iMac 证书 OU 的真实团队号（证书 CN: Apple Development: Xingwei Ni (T3LHAKCU8H)，团队号从 subject OU 取）。
- 构建：真机 `iosApp/scripts/build-ios-app.sh`（device 模式经 `sudo launchctl asuser`），Intel iMac 只能 device；模拟器需补 `iosX64` target。
- 一次性 sudoers（launchctl asuser）需要在 iMac 上配，配置命令写进 docs。

## 八、验收

1. `ide_build_project` success:true；`ide_run_tests` 跑 `androidAppForAgent` 装机启动，进程换新。
2. token-guard 故意违规能卡住构建，恢复后绿。
3. 单测：AsrServiceTest 6 条 + KoinAssemblyTest 全过。
4. 三星真机：登录 → 首登选体裁 → 录音 → 转写 → 出稿 → 渲染 mermaid → 断网排队 → 恢复补送（与上一轮同样的回归路径）。
5. iMac：`build-ios-app.sh` 出真机包（或模拟器包，视 target 补充情况），签名核对 `codesign -dv`。
## 九、落地进展（2026-11-09）

### 已完成

| 项 | 结果 | 证据 |
| --- | --- | --- |
| 主题四件套 | ✅ | `shared/src/commonMain/kotlin/top/lzdrichard/idea/presentation/theme/{AppTheme,ColorScheme,Typography,AppSpacing}.kt` |
| token-guard 门禁 | ✅ | `phone/scripts/token-guard.mjs` + 根 build 的 `tokenGuard` 任务；实测抓到 Mermaid WebView 的 `setBackgroundColor(0x00000000)`，加行内豁免后通过 |
| Koin DI | ✅ | `shared/di/KoinModule.kt`（10 个 single + 6 个 viewModel）+ android/ios 两个 `platformModule` actual；android `MainApplication.onCreate` startKoin，iOS `IosAppKt.initializeIosApp()` |
| AppState → ViewModel + 状态 | ✅ | AppViewModel / LoginViewModel / TemplatesViewModel / HomeViewModel / DetailViewModel / SettingsViewModel，页面只读 StateFlow |
| 组件化 + 无水波纹 | ✅ | `presentation/components/{AppButton,AppCard,AppTextField,StatusBadge,LoadingBlock,MarkdownView,Mermaid}.kt`，一律 `indication = null` |
| 导航 | ✅ | `presentation/navigation/{Routes,AppNavHost}.kt`，nav-compose 2.9.2 |
| 装配完整性测试 | ✅ | `shared/src/commonTest/.../di/KoinAssemblyTest.kt` 2 条全过（tests=2 failures=0） |
| Android 编译/装机改回 IDE MCP | ✅ | 运行配置 `assembleDebug`（Gradle）、`sharedUnitTest`（Gradle）、`androidAppForAgent`（Android App）；`ide_build_project` success、`ide_run_tests` 装机启动 |
| 三星回归（新架构） | ✅ | 录音 20s → 转写 → 提交 → 出稿 → 详情渲染；DB 最新记录 `e8c595e5-331d-424a-866e-c58855020329`，asr_provider=funasr / asr_model=fun-asr-nano / status=done |
| iOS 工程与构建链 | ✅（未签名） | `phone/iosApp/{iosApp.xcodeproj,iosApp/iOSApp.swift,iosApp/Info.plist,scripts/build-kotlin-framework.sh,scripts/build-ios-app.sh}`；iMac 上 `** BUILD SUCCEEDED **`，产物 `build/ios/final/Build/Products/Debug-iphoneos/iosApp.app`（arm64，74MB debug dylib，compose-resources 已同步） |

### 与 LoostoneCenter 的差异（有意保留）

1. **不建 `base:*` 与 `feature:*` 的多模块拆分**：只有 `shared` / `androidApp` / `feature:asr` 三个模块。业务规模还没到需要横向拆契约/实现的程度，拆了只增加同步成本。
2. **不建 `iosAppShell` / `iosChinaAppShell`**：只有一个渠道，iOS 壳直接由 `shared` 导出 `MainViewController()` + `initializeIosApp()`；渠道拆分等真要发两个包时再做。
3. **iOS 单 target**：`iosApp`，bundle id `top.lzdrichard.idea.ios`，DEVELOPMENT_TEAM=FPB3EQQ2AD（证书 OU），部署目标 15.0（无 auth0-kmp，不需要 17.4）。
4. **删除 `gradle/gradle-daemon-jvm.properties`**：它把 daemon 钉在 JDK 25，iMac 只有 17，会触发 foojay 下载与锁冲突；删掉后两端都用本机 JVM。

### 未完成 / 待条件

1. **iOS 真机装机**：iMac 上没有 iPhone 配对、`~/Library/MobileDevice/Provisioning Profiles` 为空、`sudo launchctl asuser` 免密未配置（真机签名会报 errSecInternalComponent）。只做了 `CODE_SIGNING_ALLOWED=NO` 的完整编译+链接验证。
2. **iOS 模拟器**：工程只配 iosArm64 / iosSimulatorArm64，iMac 是 Intel，模拟器没有可用切片（与 LoostoneCenter 一致）。
3. **iMac 上没有 node**：Xcode 构建阶段按设计自动带 `-PskipTokenGuard`；token 门禁只在 Android/Windows 侧生效。
4. **未 commit / 未 push**：等用户授权。
