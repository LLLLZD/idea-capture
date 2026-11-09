package top.lzdrichard.idea.di

import org.koin.core.context.startKoin
import top.lzdrichard.idea.core.AppLog

/**
 * iOS 壳初始化（镜像 androidApp/MainApplication.onCreate）。
 *
 * Swift 在 @main init 里调一次即可；幂等，重复调用不会重复启动容器。
 */
private var started = false

fun initializeIosApp() {
    if (started) return
    started = true
    runCatching {
        startKoin { modules(sharedModule, platformModule) }
    }.onFailure { error ->
        AppLog.e("IosApp", "Koin 启动失败：" + error.message)
    }
    AppLog.i("IosApp", "iOS 壳初始化完成")
}
