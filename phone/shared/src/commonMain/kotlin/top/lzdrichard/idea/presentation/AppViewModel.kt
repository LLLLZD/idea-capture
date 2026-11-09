package top.lzdrichard.idea.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.lzdrichard.idea.core.AppLog
import top.lzdrichard.idea.data.AuthRepository
import top.lzdrichard.idea.presentation.navigation.Routes

/** 应用启动状态：恢复会话决定首屏，别让用户先看到登录页再被弹走。 */
data class AppUiState(
    val loading: Boolean = true,
    val start: String = Routes.LOGIN,
)

class AppViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val outcome = runCatching { auth.restore() }.getOrElse { AuthRepository.RestoreOutcome.INVALID }
            AppLog.i("AppViewModel", "启动恢复会话：" + outcome)
            val start = when {
                outcome != AuthRepository.RestoreOutcome.OK -> Routes.LOGIN
                auth.templatesReady -> Routes.HOME
                else -> Routes.TEMPLATES
            }
            _state.value = AppUiState(loading = false, start = start)
        }
    }

    /** 登录成功后的去向：勾过体裁就直接进首页，首登先去选体裁。 */
    fun afterLogin(): String = if (auth.templatesReady) Routes.HOME else Routes.TEMPLATES
}
