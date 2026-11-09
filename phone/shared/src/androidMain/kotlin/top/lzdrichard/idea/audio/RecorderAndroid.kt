package top.lzdrichard.idea.audio

import android.media.MediaRecorder
import android.os.Build
import top.lzdrichard.idea.AndroidApp
import top.lzdrichard.idea.data.newRecordingPath
import java.io.File

actual class AudioRecorder actual constructor() {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    actual val isRecording: Boolean
        get() = recorder != null

    actual fun start(): Boolean {
        if (recorder != null) return true
        val file = File(newRecordingPath())
        // VOICE_RECOGNITION 不走通话路径的自动增益与回声消除，转写更接近原声；个别机型不支持时退回 MIC。
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val machine = build(source)
            if (machine != null) {
                if (prepareCompat(machine, file)) {
                    recorder = machine
                    output = file
                    startedAt = System.currentTimeMillis()
                    return true
                }
                runCatching { machine.release() }
            }
        }
        output = null
        return false
    }

    private fun build(source: Int): MediaRecorder? = try {
        val machine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(AndroidApp.context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        machine.setAudioSource(source)
        machine
    } catch (e: Exception) {
        null
    }

    private fun prepareCompat(machine: MediaRecorder, file: File): Boolean = try {
        machine.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        machine.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        machine.setAudioSamplingRate(16000)
        machine.setAudioEncodingBitRate(32000)
        machine.setOutputFile(file.absolutePath)
        machine.prepare()
        machine.start()
        true
    } catch (e: Exception) {
        false
    }

    actual fun stop(): AudioClip? {
        val machine = recorder ?: return null
        val file = output
        val duration = (System.currentTimeMillis() - startedAt).toInt()
        recorder = null
        output = null
        return try {
            machine.stop()
            machine.release()
            if (file != null && file.exists() && file.length() >= 4096) {
                AudioClip(file.absolutePath, duration)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            runCatching { machine.release() }
            file?.delete()
            null
        }
    }

    actual fun cancel() {
        val machine = recorder ?: return
        recorder = null
        runCatching {
            machine.stop()
            machine.release()
        }
        output?.delete()
        output = null
    }
}
