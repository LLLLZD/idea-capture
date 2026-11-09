import SwiftUI
import UIKit
import shared

@main
struct iOSApp: App {

    init() {
        // 壳初始化：日志 + Koin 装配（镜像 androidApp/MainApplication.onCreate）
        IosAppKt.initializeIosApp()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}

/// 承载 Compose 的 UIViewController（实现落在 shared，壳不引 Compose 栈）
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
