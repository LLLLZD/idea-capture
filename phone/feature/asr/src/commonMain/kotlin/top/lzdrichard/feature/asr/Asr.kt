package top.lzdrichard.feature.asr

import kotlinx.coroutines.CancellationException

/** 转写路线：Auto 原生优先，不可用/失败降级 api；Native 只用原生；Api 只走在线。 */
enum class AsrRoute { Auto, Native, Api }

/** 一次录音的产物。路径是客户端本地文件，音频不上传时也用它补转写。 */
data class AsrAudio(val path: String, val mime: String = "audio/mp4", val durationMs: Int = 0)

/** provider 与 model 会随提交一起落库，用来看清每条稿子是哪条链路出的。 */
data class AsrOutcome(
    val text: String,
    val used: AsrRoute,
    val degraded: Boolean,
    val provider: String,
    val model: String,
)

open class AsrException(message: String) : Exception(message)

/** 本机确实没有可用的原生转写（没权限、设备不支持、Android 空壳）。 */
class AsrUnavailableException(message: String) : AsrException(message)

/** 转写动作本身失败（服务端 4xx/5xx、文件坏了、识别报错）。 */
class AsrFailedException(message: String) : AsrException(message)

/** 连不上转写服务（断网、超时）。调用方要保住录音，等联网后补转写。 */
class AsrNetworkException(message: String, cause: Throwable? = null) : AsrException(message)

/** 单个转写实现。实现方只管音频进、文本出，不关心路线。 */
interface AsrEngine {
    val provider: String
    val model: String
    suspend fun isAvailable(): Boolean
    suspend fun transcribe(audio: AsrAudio): String
}

class AsrService(
    private val native: AsrEngine,
    private val api: AsrEngine,
) {

    suspend fun transcribe(audio: AsrAudio, route: AsrRoute = AsrRoute.Auto): AsrOutcome = when (route) {
        AsrRoute.Api -> apiOutcome(audio, degraded = false)

        AsrRoute.Native -> {
            if (!nativeAvailability()) throw AsrUnavailableException("这台设备用不了系统转写")
            AsrOutcome(native.transcribe(audio), AsrRoute.Native, false, native.provider, native.model)
        }

        AsrRoute.Auto -> {
            val text = if (nativeAvailability()) tryNative(audio) else null
            if (text == null) {
                apiOutcome(audio, degraded = true)
            } else {
                AsrOutcome(text, AsrRoute.Native, false, native.provider, native.model)
            }
        }
    }

    private suspend fun apiOutcome(audio: AsrAudio, degraded: Boolean): AsrOutcome =
        AsrOutcome(api.transcribe(audio), AsrRoute.Api, degraded, api.provider, api.model)

    private suspend fun nativeAvailability(): Boolean = try {
        native.isAvailable()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        false
    }

    /** 原生识别跑完但结果为空不算失败，仍然返回空串。 */
    private suspend fun tryNative(audio: AsrAudio): String? = try {
        native.transcribe(audio)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}
