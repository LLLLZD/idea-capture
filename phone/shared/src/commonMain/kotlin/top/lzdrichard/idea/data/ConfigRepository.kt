package top.lzdrichard.idea.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 服务端下发配置（体裁列表、转写预设、提交间隔）的单一来源。
 *
 * 登录后、启动时各刷新一次；界面只读 StateFlow，不各自缓存副本。
 */
class ConfigRepository(private val api: Api) {

    private val _config = MutableStateFlow<ClientConfig?>(null)
    val config: StateFlow<ClientConfig?> = _config.asStateFlow()

    suspend fun refresh(): ClientConfig {
        val fresh = api.clientConfig()
        _config.value = fresh
        return fresh
    }

    fun clear() {
        _config.value = null
    }

    /** 转写预设里的 api_key：按 url 匹配，找不到就是空串（公网预设带 Bearer）。 */
    fun asrKeyFor(url: String): String =
        _config.value?.asrPresets?.firstOrNull { it.url.trimEnd('/') == url.trimEnd('/') }?.apiKey.orEmpty()

    fun firstAsrPresetUrl(): String? = _config.value?.asrPresets?.firstOrNull()?.url
}
