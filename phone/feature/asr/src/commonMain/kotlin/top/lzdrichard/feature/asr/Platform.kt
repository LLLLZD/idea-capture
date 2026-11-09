package top.lzdrichard.feature.asr

import io.ktor.client.HttpClient

/** 读本地录音字节；不存在或读不出来返回 null。 */
expect object PlatformAudio {
    fun readBytes(path: String): ByteArray?
}

/** 平台默认的 Ktor 引擎（Android OkHttp、iOS Darwin）。 */
expect fun platformHttpClient(): HttpClient

/** 原生转写：Android 是空壳（系统 SpeechRecognizer 自己占用麦克风，拿不到可提交的音频），iOS 走 SFSpeechRecognizer。 */
expect class NativeAsrEngine() : AsrEngine {
    override val provider: String
    override val model: String
    override suspend fun isAvailable(): Boolean
    override suspend fun transcribe(audio: AsrAudio): String
}
