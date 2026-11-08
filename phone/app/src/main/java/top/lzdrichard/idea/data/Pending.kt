package top.lzdrichard.idea.data

import org.json.JSONArray
import org.json.JSONObject

/** 一条还没送出去的提交；断网时落盘，恢复后按顺序补送。text 为空表示录音还没转写，靠 audioPath 稍后补。 */
data class PendingSubmit(
    val clientId: String,
    val text: String,
    val durationMs: Int,
    val createdAt: Long,
    val audioPath: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("client_id", clientId)
        .put("text", text)
        .put("duration_ms", durationMs)
        .put("created_at", createdAt)
        .put("audio_path", audioPath ?: "")

    companion object {
        fun list(raw: String): MutableList<PendingSubmit> = try {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val item = array.getJSONObject(index)
                PendingSubmit(
                    clientId = item.optString("client_id"),
                    text = item.optString("text"),
                    durationMs = item.optInt("duration_ms"),
                    createdAt = item.optLong("created_at"),
                    audioPath = item.optString("audio_path").ifBlank { null },
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }

        fun dump(items: List<PendingSubmit>): String {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
