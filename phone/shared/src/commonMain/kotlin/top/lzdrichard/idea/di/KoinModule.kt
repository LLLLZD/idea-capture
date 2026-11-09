package top.lzdrichard.idea.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.lzdrichard.feature.asr.platformHttpClient
import top.lzdrichard.idea.data.Api
import top.lzdrichard.idea.data.AsrGateway
import top.lzdrichard.idea.data.AuthRepository
import top.lzdrichard.idea.data.ConfigRepository
import top.lzdrichard.idea.data.PendingQueue
import top.lzdrichard.idea.data.PlatformStore
import top.lzdrichard.idea.data.Prefs
import top.lzdrichard.idea.data.RecordRepository
import top.lzdrichard.idea.data.RecordingController
import top.lzdrichard.idea.presentation.AppViewModel
import top.lzdrichard.idea.presentation.feature.detail.DetailViewModel
import top.lzdrichard.idea.presentation.feature.home.HomeViewModel
import top.lzdrichard.idea.presentation.feature.login.LoginViewModel
import top.lzdrichard.idea.presentation.feature.settings.SettingsViewModel
import top.lzdrichard.idea.presentation.feature.templates.TemplatesViewModel

/**
 * Koin 依赖注入模块（结构照搬 LoostoneCenter 的 shared/di/KoinModule.kt）
 *
 * 定义所有跨平台共享的依赖关系；平台特有依赖（录音器等）登记在各平台的
 * [platformModule] 里，由壳工程启动时一起装配。
 *
 * 约定：
 * - 仓储/网关用 single（跨页面唯一，持有配置与队列）；
 * - 页面状态用 viewModel（随导航栈销毁）。
 */
val sharedModule = module {
    single { platformHttpClient() }
    single { PlatformStore() }
    single { Prefs(get()) }
    single { Api(get()) }
    single { ConfigRepository(get()) }
    single { AuthRepository(get(), get(), get()) }
    single { RecordRepository(get()) }
    single { AsrGateway(get(), get(), get()) }
    single { PendingQueue(get(), get(), get()) }
    single { RecordingController(get(), get(), get(), get()) }

    viewModel { AppViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { TemplatesViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { DetailViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
}

/** 平台特有依赖（录音器在 Android 是 MediaRecorder、iOS 是 AVAudioRecorder） */
expect val platformModule: Module
