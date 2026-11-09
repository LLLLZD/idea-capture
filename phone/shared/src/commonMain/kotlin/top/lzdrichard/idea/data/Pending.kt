package top.lzdrichard.idea.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一条还没送出去的提交；断网时落盘，恢复后按顺序补送。text 为空表示录音还没转写，靠 audioPath 稍后补。 */
data class PendingSubmit(
    val clientId: String,
    val text: String,
    val durationMs: Int,
    val createdAt: Long,
    val audioPath: String? = null,
    val asrProvider: String? = null,
    val asrModel: String? = null,
) {
    fun toJson() = buildJsonObject {
        put("client_id", clientId)
        put("text", text)
        put("duration_ms", durationMs)
        put("created_at", createdAt)
        put("audio_path", audioPath ?: "")
        put("asr_provider", asrProvider ?: "")
        put("asr_model", asrModel ?: "")
    }

    companion object {
        fun list(raw: String): MutableList<PendingSubmit> = try {
            val array = Js.json.parseToJsonElement(raw) as? JsonArray ?: JsonArray(emptyList())
            array.map { element ->
                val item = Js.obj(element) ?: return@map null
                PendingSubmit(
                    clientId = Js.str(item["client_id"]).orEmpty(),
                    text = Js.str(item["text"]).orEmpty(),
                    durationMs = Js.int(item["duration_ms"]),
                    createdAt = Js.long(item["created_at"]),
                    audioPath = Js.strOrNull(item["audio_path"]),
                    asrProvider = Js.strOrNull(item["asr_provider"]),
                    asrModel = Js.strOrNull(item["asr_model"]),
                )
            }.filterNotNull().toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }

        fun dump(items: List<PendingSubmit>): String = JsonArray(items.map { it.toJson() }).toString()
    }
}
