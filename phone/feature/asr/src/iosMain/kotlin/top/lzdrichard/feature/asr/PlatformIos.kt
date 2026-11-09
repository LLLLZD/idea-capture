package top.lzdrichard.feature.asr

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Speech.SFSpeechRecognitionRequest
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.Speech.SFSpeechURLRecognitionRequest
import platform.posix.memcpy
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual object PlatformAudio {
    actual fun readBytes(path: String): ByteArray? {
        val data: NSData = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
        val length = data.length.toInt()
        if (length <= 0) return null
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }
}

actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
    }
}

/**
 * iOS 原生转写：把已经录好的 m4a 交给 SFSpeechRecognizer，能做端侧时强制端侧（录音不出手机）。
 * 不可用（未授权、设备不支持、语言包没下载）时返回 false，由 AsrService 决定降级。
 */
@OptIn(ExperimentalForeignApi::class)
actual class NativeAsrEngine : AsrEngine {

    actual override val provider: String = "native"
    actual override val model: String = "sf-speech"

    private fun recognizer(): SFSpeechRecognizer? = SFSpeechRecognizer(NSLocale("zh-CN"))

    actual override suspend fun isAvailable(): Boolean {
        val engine = recognizer() ?: return false
        if (!engine.available) return false
        return when (SFSpeechRecognizer.authorizationStatus()) {
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> true
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined -> requestAuthorization()
            else -> false
        }
    }

    private suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { cont ->
        SFSpeechRecognizer.requestAuthorization { status ->
            if (cont.isActive) {
                cont.resumeWith(
                    Result.success(
                        status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized,
                    ),
                )
            }
        }
    }

    actual override suspend fun transcribe(audio: AsrAudio): String {
        val engine = recognizer() ?: throw AsrUnavailableException("这台设备不支持系统转写")
        val request: SFSpeechRecognitionRequest = SFSpeechURLRecognitionRequest(NSURL.fileURLWithPath(audio.path))
        request.shouldReportPartialResults = false
        // 支持端侧就强制端侧：录音不出手机。
        request.requiresOnDeviceRecognition = engine.supportsOnDeviceRecognition

        return suspendCancellableCoroutine { cont ->
            var settled = false
            engine.recognitionTaskWithRequest(request) { result, error ->
                if (settled) return@recognitionTaskWithRequest
                if (error != null) {
                    settled = true
                    if (cont.isActive) {
                        cont.resumeWithException(AsrFailedException("系统转写失败：" + error.localizedDescription))
                    }
                    return@recognitionTaskWithRequest
                }
                val text = result?.bestTranscription?.formattedString
                if (text != null && result.isFinal()) {
                    settled = true
                    if (cont.isActive) cont.resumeWith(Result.success(text))
                }
            }
        }
    }
}
