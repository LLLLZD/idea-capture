package top.lzdrichard.idea.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import top.lzdrichard.idea.presentation.components.LoadingBlock
import top.lzdrichard.idea.presentation.navigation.AppNavHost
import top.lzdrichard.idea.presentation.theme.IdeaCaptureTheme

/**
 * 应用根组件：双端共用（Android 由 MainActivity setContent，iOS 由 MainViewController 承载）。
 *
 * 主题在根部只包一次，页面里不允许出现裸色值/裸字号。
 */
@Composable
fun App() {
    val appViewModel = koinViewModel<AppViewModel>()
    val state by appViewModel.state.collectAsState()

    IdeaCaptureTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingBlock("正在恢复会话")
                }
            } else {
                AppNavHost(start = state.start, afterLogin = appViewModel::afterLogin)
            }
        }
    }
}
