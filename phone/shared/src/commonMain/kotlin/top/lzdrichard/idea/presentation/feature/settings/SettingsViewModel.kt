package top.lzdrichard.idea.presentation.feature.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.lzdrichard.idea.data.AuthRepository
import top.lzdrichard.idea.data.Prefs

/** 设置页状态：服务端地址、转写地址、转写方式。 */
data class SettingsUiState(
    val serverUrl: String = "",
    val asrUrl: String = "",
    val asrRoute: String = "auto",
    val message: String? = null,
)

class SettingsViewModel(
    private val auth: AuthRepository,
    private val prefs: Prefs,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            serverUrl = auth.serverUrl,
            asrUrl = prefs.asrUrl,
            asrRoute = prefs.asrRoute,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, message = null) }

    fun onAsrUrlChange(value: String) = _state.update { it.copy(asrUrl = value, message = null) }

    fun onRouteChange(value: String) = _state.update { it.copy(asrRoute = value, message = null) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        auth.updateServerUrl(current.serverUrl)
        prefs.asrUrl = current.asrUrl.trim()
        prefs.asrRoute = current.asrRoute
        _state.update { it.copy(message = "已保存") }
        onSaved()
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.logout()
        onLoggedOut()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
