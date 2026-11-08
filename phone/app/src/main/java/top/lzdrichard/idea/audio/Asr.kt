package top.lzdrichard.idea.audio

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import top.lzdrichard.idea.data.ApiException

object Asr {

    private const val MODEL = "fun-asr-nano"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(file: File, baseUrl: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
                .addFormDataPart("model", MODEL)
                .build()
            val builder = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/audio/transcriptions")
                .post(body)
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer " + apiKey)
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    // fun-asr 在音频里没有语音时由内部报 500；对用户来说等同于「没听清」。
                    val msg = if (response.code >= 500) {
                        "没听到语音，检查一下麦克风或被遮挡了"
                    } else {
                        "转写失败（HTTP " + response.code + "）"
                    }
                    throw ApiException(response.code, msg)
                }
                parseText(raw)
            }
        }

    /** 只认 text / OpenAI choices 两种形态；解析不出来就当没转出文字，绝不把原始 JSON 当正文提交。 */
    private fun parseText(raw: String): String = try {
        val json = JSONObject(raw)
        val direct = json.optString("text").trim()
        if (direct.isNotEmpty()) {
            direct
        } else {
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().trim()
            } else {
                ""
            }
        }
    } catch (e: Exception) {
        ""
    }
}
