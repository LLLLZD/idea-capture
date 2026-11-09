package top.lzdrichard.idea.di

import org.koin.core.module.Module
import org.koin.dsl.module
import top.lzdrichard.idea.audio.AudioRecorder

/** Android 平台特有依赖 */
actual val platformModule: Module = module {
    single { AudioRecorder() }
}
