package top.lzdrichard.idea.data

import top.lzdrichard.feature.asr.AsrException
import top.lzdrichard.feature.asr.AsrNetworkException
import top.lzdrichard.idea.core.AppLog

/**
 * 本地待发队列：断网、限流时用户说的话不能丢。
 *
 * 落盘在 Prefs.pending（JSON）；录音文件在应用私有目录（PlatformFile），
 * 补送成功或确认无内容后才删除。
 */
class PendingQueue(
    private val prefs: Prefs,
    private val asr: AsrGateway,
    private val records: RecordRepository,
) {

    /** 补送结果：还剩下几条、是否因网络中断提前收工 */
    data class FlushResult(val remaining: Int, val sent: Int, val networkStalled: Boolean)

    fun count(): Int = items().size

    fun items(): MutableList<PendingSubmit> = PendingSubmit.list(prefs.pending)

    /** 入队；同一 clientId 只保留一条（幂等重试不会重复排）。 */
    fun enqueue(item: PendingSubmit) {
        val list = items()
        if (list.any { it.clientId == item.clientId }) return
        list.add(item)
        prefs.pending = PendingSubmit.dump(list)
    }

    /**
     * 保存录音到私有目录（排队期间不会被系统缓存清理扫掉）。
     * 返回保存后的路径；复制失败返回 null（此时只能丢这条录音）。
     */
    fun keepAudio(sourcePath: String, clientId: String): String? =
        PlatformFile.copyTo(sourcePath, PlatformFile.privatePath(clientId + ".m4a"))

    /** 按入队顺序补送：没转写的先补转写；遇到网络错误就停，等下一轮。 */
    suspend fun flush(): FlushResult {
        val pending = items()
        if (pending.isEmpty()) return FlushResult(0, 0, false)

        val remaining = mutableListOf<PendingSubmit>()
        var sent = 0
        var stalled = false

        for (item in pending) {
            if (stalled) {
                remaining.add(item)
                continue
            }
            var text = item.text
            var provider = item.asrProvider
            var model = item.asrModel
            val audioPath = item.audioPath

            if (text.isBlank()) {
                if (audioPath == null || !PlatformFile.exists(audioPath)) {
                    AppLog.w("PendingQueue", "录音缺失，丢弃 client_id=" + item.clientId)
                    continue
                }
                val outcome = try {
                    asr.transcribe(audioPath)
                } catch (e: AsrNetworkException) {
                    stalled = true
                    remaining.add(item)
                    continue
                } catch (e: AsrException) {
                    // 这条录音确实没内容（静音/损坏）：丢掉录音，别一直占着队
                    PlatformFile.delete(audioPath)
                    AppLog.w("PendingQueue", "补转写失败，丢弃 client_id=" + item.clientId + "：" + e.message)
                    continue
                }
                if (outcome.text.isBlank()) {
                    PlatformFile.delete(audioPath)
                    continue
                }
                text = outcome.text
                provider = outcome.provider
                model = outcome.model
            }

            try {
                records.submit(item.clientId, text, item.durationMs, provider, model)
                audioPath?.let { PlatformFile.delete(it) }
                sent += 1
            } catch (e: NetworkException) {
                stalled = true
                remaining.add(item.copy(text = text, asrProvider = provider, asrModel = model))
            } catch (e: ApiException) {
                if (e.statusCode == 429) {
                    stalled = true
                    remaining.add(item.copy(text = text, asrProvider = provider, asrModel = model))
                } else {
                    // 其余 4xx：服务端明确不接受，重试也不会过，丢掉
                    audioPath?.let { PlatformFile.delete(it) }
                    AppLog.w("PendingQueue", "提交被拒，丢弃 client_id=" + item.clientId + "：" + e.message)
                }
            }
        }

        prefs.pending = PendingSubmit.dump(remaining)
        return FlushResult(remaining.size, sent, stalled)
    }
}
