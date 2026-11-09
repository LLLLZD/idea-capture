package top.lzdrichard.idea.data

import top.lzdrichard.feature.asr.AsrException
import top.lzdrichard.feature.asr.AsrNetworkException
import top.lzdrichard.feature.asr.AsrUnavailableException
import top.lzdrichard.idea.audio.AudioRecorder
import top.lzdrichard.idea.core.AppLog

/**
 * 一次录音会话的完整编排：录音 → 转写 → 提交（失败则排队）。
 *
 * 从原 AppState.stopRecording 抽出来，界面只关心结果文案，不关心降级判断。
 */
class RecordingController(
    private val recorder: AudioRecorder,
    private val asr: AsrGateway,
    private val pending: PendingQueue,
    private val records: RecordRepository,
) {

    /** 一次停录处理的结果：成功提交返回 clientId；排队/失败给出提示文案。 */
    data class Outcome(
        val clientId: String?,
        val queued: Boolean,
        val message: String?,
    )

    val isRecording: Boolean get() = recorder.isRecording

    fun start(): Boolean = recorder.start()

    fun cancel() = recorder.cancel()

    suspend fun stop(): Outcome {
        val clip = recorder.stop() ?: return Outcome(null, false, "录音太短，没存下来")
        val clientId = randomId()

        val outcome = try {
            asr.transcribe(clip.path)
        } catch (e: AsrNetworkException) {
            // 转写就要联网；断网时先留住录音，联网后再补转写，别把用户说的话丢了
            val kept = pending.keepAudio(clip.path, clientId)
            PlatformFile.delete(clip.path)
            pending.enqueue(PendingSubmit(clientId, "", clip.durationMs, nowMillis(), kept))
            return Outcome(clientId, true, "网络不通，录音已存本机，联网后自动补转写")
        } catch (e: AsrUnavailableException) {
            PlatformFile.delete(clip.path)
            return Outcome(null, false, e.message ?: "这台设备用不了系统转写")
        } catch (e: AsrException) {
            PlatformFile.delete(clip.path)
            return Outcome(null, false, e.message ?: "转写失败")
        }

        PlatformFile.delete(clip.path)
        if (outcome.text.isBlank()) return Outcome(null, false, "没转出文字")

        return try {
            records.submit(clientId, outcome.text, clip.durationMs, outcome.provider, outcome.model)
            Outcome(clientId, false, null)
        } catch (e: NetworkException) {
            pending.enqueue(
                PendingSubmit(clientId, outcome.text, clip.durationMs, nowMillis(), null, outcome.provider, outcome.model),
            )
            Outcome(clientId, true, "网络不通，已排队，联网后自动送出")
        } catch (e: ApiException) {
            if (e.statusCode == 429) {
                pending.enqueue(
                    PendingSubmit(clientId, outcome.text, clip.durationMs, nowMillis(), null, outcome.provider, outcome.model),
                )
                Outcome(clientId, true, "刚交过一条，这条先排队，稍后自动送出")
            } else {
                AppLog.e("RecordingController", "提交被拒：" + e.message)
                Outcome(null, false, e.message ?: "提交失败")
            }
        }
    }
}
