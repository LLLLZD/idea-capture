package top.lzdrichard.idea.audio

/** 一次录音的结果：本地文件路径 + 毫秒时长。 */
data class AudioClip(val path: String, val durationMs: Int)

/**
 * 只管录音，不管转写。Android 用 MediaRecorder，iOS 用 AVAudioRecorder，
 * 两端都输出 16k / 32kbps 的 m4a（fun-asr 与 Apple 转写都能吃）。
 */
expect class AudioRecorder() {
    val isRecording: Boolean
    fun start(): Boolean
    fun stop(): AudioClip?
    fun cancel()
}
