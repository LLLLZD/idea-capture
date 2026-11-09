package top.lzdrichard.feature.asr

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File

actual object PlatformAudio {
    actual fun readBytes(path: String): ByteArray? = try {
        val file = File(path)
        if (file.exists() && file.length() > 0) file.readBytes() else null
    } catch (e: Exception) {
        null
    }
}

actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
    }
}

/** Android 上没有「拿现有录音文件转写」的系统能力，恒不可用，Auto 会直接降级到在线转写。 */
actual class NativeAsrEngine : AsrEngine {
    actual override val provider: String = "native"
    actual override val model: String = "android-speech"
    actual override suspend fun isAvailable(): Boolean = false
    actual override suspend fun transcribe(audio: AsrAudio): String =
        throw AsrUnavailableException("Android 上用不了系统转写")
}
