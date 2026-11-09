package top.lzdrichard.idea.data

import io.ktor.client.HttpClient
import top.lzdrichard.feature.asr.ApiAsrEngine
import top.lzdrichard.feature.asr.AsrAudio
import top.lzdrichard.feature.asr.AsrEngine
import top.lzdrichard.feature.asr.AsrOutcome
import top.lzdrichard.feature.asr.AsrRoute
import top.lzdrichard.feature.asr.AsrService
import top.lzdrichard.feature.asr.NativeAsrEngine

/**
 * 转写入口：把「用哪条路线、地址和 key 从哪来」收口在一处。
 *
 * 路线取自 Prefs.asrRoute（auto / native / api），地址与 key 取自当前配置；
 * 每次调用现取，设置页改了立刻生效，不需要重建对象。
 */
class AsrGateway(
    private val prefs: Prefs,
    private val config: ConfigRepository,
    private val http: HttpClient,
) {

    private val native = NativeAsrEngine()

    /** 在线引擎：地址/密钥随设置与配置变化，所以按调用现造。 */
    private inner class OnlineEngine : AsrEngine {
        override val provider: String = "funasr"
        override val model: String = "fun-asr-nano"
        override suspend fun isAvailable(): Boolean = prefs.asrUrl.isNotBlank()
        override suspend fun transcribe(audio: AsrAudio): String =
            ApiAsrEngine(http, prefs.asrUrl, config.asrKeyFor(prefs.asrUrl)).transcribe(audio)
    }

    suspend fun transcribe(path: String): AsrOutcome {
        val service = AsrService(native, OnlineEngine())
        val route = when (prefs.asrRoute) {
            "native" -> AsrRoute.Native
            "api" -> AsrRoute.Api
            else -> AsrRoute.Auto
        }
        return service.transcribe(AsrAudio(path), route)
    }
}
