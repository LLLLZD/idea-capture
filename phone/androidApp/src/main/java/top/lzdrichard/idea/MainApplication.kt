package top.lzdrichard.idea

import android.app.Application
import org.koin.core.context.startKoin
import top.lzdrichard.idea.di.platformModule
import top.lzdrichard.idea.di.sharedModule

/**
 * 应用入口：装配 Koin 容器（结构照搬 LoostoneCenter 的 MainApplication）。
 *
 * 先 AndroidApp.init 把 Context 交给平台层（Prefs/文件路径要用），再 startKoin，
 * 保证任何 ViewModel 创建时依赖都已就绪。
 */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.init(this)
        startKoin {
            modules(sharedModule, platformModule)
        }
    }
}
