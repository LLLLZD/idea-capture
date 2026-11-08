package top.lzdrichard.idea.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class Recorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec-" + System.currentTimeMillis() + ".m4a")
        // VOICE_RECOGNITION 不走通话路径的自动增益与回声消除，转写更接近原声；个别机型不支持时退回 MIC。
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val machine = build(source)
            if (machine != null) {
                if (machine.prepareCompat(file)) {
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
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        machine.setAudioSource(source)
        machine
    } catch (e: Exception) {
        null
    }

    private fun MediaRecorder.prepareCompat(file: File): Boolean = try {
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(16000)
        setAudioEncodingBitRate(32000)
        setOutputFile(file.absolutePath)
        prepare()
        start()
        true
    } catch (e: Exception) {
        false
    }

    /** 停止并返回 (文件, 毫秒时长)；太短或异常时返回 null。 */
    fun stop(): Pair<File, Int>? {
        val machine = recorder ?: return null
        val file = output
        val duration = (System.currentTimeMillis() - startedAt).toInt()
        recorder = null
        output = null
        return try {
            machine.stop()
            machine.release()
            if (file != null && file.exists() && file.length() > 1024) {
                if (file.length() >= 4096) {
                    file to duration
                } else {
                    file.delete()
                    null
                }
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

    fun cancel() {
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
