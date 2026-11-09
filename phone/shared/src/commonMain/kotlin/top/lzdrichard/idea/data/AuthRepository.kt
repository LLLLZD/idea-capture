package top.lzdrichard.idea.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登录态与账号动作的唯一入口（对齐 LoostoneCenter 的 LoginCoordinator 职责）。
 *
 * 界面与 ViewModel 不直接摸 Api.token / Prefs.token：壳启动时调 [restore] 恢复会话，
 * 界面只读 [session]。
 */
class AuthRepository(
    private val api: Api,
    private val prefs: Prefs,
    private val config: ConfigRepository,
) {

    sealed interface Session {
        data object LoggedOut : Session
        data class Active(val displayName: String) : Session
    }

    /** 启动恢复结果：登录页需要区分「没登录」和「凭证失效」。 */
    enum class RestoreOutcome { NO_TOKEN, INVALID, OK }

    private val _session = MutableStateFlow<Session>(
        if (prefs.token.isNullOrBlank()) Session.LoggedOut else Session.Active(prefs.displayName),
    )
    val session: StateFlow<Session> = _session.asStateFlow()

    val serverUrl: String get() = prefs.serverUrl
    val displayName: String get() = prefs.displayName

    /** 本次启动是否拿到了可用的用户勾选（首登用户为 false → 先进选体裁页）。 */
    var templatesReady: Boolean = false
        private set

    suspend fun login(username: String, password: String): LoginResult {
        api.baseUrl = prefs.serverUrl
        val result = api.login(username.trim(), password)
        if (result.token.isBlank()) throw ApiException(500, "服务端没返回登录凭证")
        api.token = result.token
        prefs.token = result.token
        val name = result.displayName.ifBlank { username.trim() }
        prefs.displayName = name
        _session.value = Session.Active(name)
        // 配置落后端走：登录后立刻拉一次，失败不阻断登录（首页会重试）
        runCatching { config.refresh() }.onSuccess {
            adoptAsrPresetIfDefault()
            templatesReady = it.templates.any { template -> template.selected && !template.isBuiltin }
        }
        return result
    }

    /** 启动时恢复会话：无 token 直接失败；token 失效（401）清掉本地凭证。 */
    suspend fun restore(): RestoreOutcome {
        if (prefs.token.isNullOrBlank()) return RestoreOutcome.NO_TOKEN
        api.baseUrl = prefs.serverUrl
        api.token = prefs.token
        val cfg = try {
            config.refresh()
        } catch (e: ApiException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                clearSession()
                return RestoreOutcome.INVALID
            }
            return RestoreOutcome.INVALID
        } catch (e: Exception) {
            // 断网：保留本地凭证，仍当已登录看待，等联网后各页面自然重试
            return RestoreOutcome.OK
        }
        adoptAsrPresetIfDefault()
        templatesReady = cfg.templates.any { template -> template.selected && !template.isBuiltin }
        return RestoreOutcome.OK
    }

    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        prefs.serverUrl = trimmed
        api.baseUrl = trimmed
    }

    fun logout() {
        clearSession()
        _session.value = Session.LoggedOut
    }

    private fun clearSession() {
        prefs.clearLogin()
        api.token = null
        config.clear()
        templatesReady = false
    }

    /** 首次使用时把转写地址预填成服务端下发的公网预设（用户已改过就不动）。 */
    private fun adoptAsrPresetIfDefault() {
        if (prefs.asrUrl != Prefs.DEFAULT_ASR) return
        val presetUrl = config.firstAsrPresetUrl() ?: return
        prefs.asrUrl = presetUrl
    }
}
