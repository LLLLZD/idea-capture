package top.lzdrichard.idea.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL
import top.lzdrichard.idea.data.newRecordingPath
import top.lzdrichard.idea.data.nowMillis

@OptIn(ExperimentalForeignApi::class)
actual class AudioRecorder actual constructor() {

    private var recorder: AVAudioRecorder? = null
    private var outputPath: String? = null
    private var startedAt: Long = 0L

    actual val isRecording: Boolean
        get() = recorder?.recording == true

    actual fun start(): Boolean {
        if (recorder != null) return true
        val path = newRecordingPath()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
        session.setActive(true, null)

        // 16k 单声道 AAC：fun-asr 与 SFSpeechRecognizer 都吃这个
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to 16000.0,
            AVNumberOfChannelsKey to 1,
        )
        val created = AVAudioRecorder(NSURL.fileURLWithPath(path), settings, null)
        val started = created.prepareToRecord() && created.record()
        if (!started) return false
        recorder = created
        outputPath = path
        startedAt = nowMillis()
        return true
    }

    actual fun stop(): AudioClip? {
        val current = recorder ?: return null
        val path = outputPath
        val duration = (nowMillis() - startedAt).toInt()
        recorder = null
        outputPath = null
        current.stop()
        AVAudioSession.sharedInstance().setActive(false, null)
        return path?.let { AudioClip(it, duration) }
    }

    actual fun cancel() {
        val current = recorder ?: return
        recorder = null
        outputPath = null
        current.stop()
        current.deleteRecording()
        AVAudioSession.sharedInstance().setActive(false, null)
    }
}
