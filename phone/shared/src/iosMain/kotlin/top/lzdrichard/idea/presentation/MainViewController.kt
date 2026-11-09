package top.lzdrichard.idea.presentation

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS 壳的唯一入口：Swift 侧调用 MainViewControllerKt.MainViewController() 拿到承载 Compose 的 VC。
 * 业务逻辑与依赖装配都在 shared 里，Swift 只管包一层窗口。
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
