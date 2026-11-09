package top.lzdrichard.idea.presentation.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lzdrichard.idea.core.AppLog
import top.lzdrichard.idea.data.AuthRepository

/** 登录页状态：地址、输入、忙碌、错误文案。 */
data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(serverUrl = auth.serverUrl))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun login(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.busy) return
        if (current.serverUrl.isBlank()) {
            _state.update { it.copy(error = "先填服务端地址") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                auth.updateServerUrl(current.serverUrl)
                auth.login(current.username, current.password)
                AppLog.i("LoginViewModel", "登录成功 user=" + current.username.trim())
                _state.update { it.copy(busy = false, password = "") }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "登录失败") }
            }
        }
    }
}
