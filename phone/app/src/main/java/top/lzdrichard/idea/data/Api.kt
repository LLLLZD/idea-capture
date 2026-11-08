package top.lzdrichard.idea.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val statusCode: Int, message: String) : Exception(message)

object Api {

    var baseUrl: String = Prefs.DEFAULT_SERVER
    var token: String? = null

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
            token?.let { builder.header("Authorization", "Bearer " + it) }
            when (method) {
                "GET" -> builder.get()
                "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(JSON_TYPE))
                else -> builder.post((body ?: JSONObject()).toString().toRequestBody(JSON_TYPE))
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, describe(text, response.code))
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    private fun describe(text: String, code: Int): String {
        return try {
            val json = JSONObject(text)
            json.optString("detail").ifBlank {
                val err = json.optString("error")
                if (err == "rate_limited") "提交太频繁，稍等一下" else err.ifBlank { "请求失败（HTTP " + code + "）" }
            }
        } catch (e: Exception) {
            "请求失败（HTTP " + code + "）"
        }
    }

    private fun parseTemplate(json: JSONObject) = Template(
        id = json.getInt("id"),
        key = json.getString("key"),
        name = json.getString("name"),
        criterion = json.optString("criterion"),
        sampleMd = json.optString("sample_md"),
        wordRange = json.optString("word_range"),
        isBuiltin = json.optBoolean("is_builtin"),
        selected = json.optBoolean("selected"),
    )

    suspend fun login(username: String, password: String): LoginResult {
        val body = JSONObject().put("username", username).put("password", password)
        val res = call("POST", "/v1/auth/login", body)
        val user = res.optJSONObject("user")
        return LoginResult(
            token = res.getString("token"),
            displayName = user?.optString("display_name").orEmpty(),
            needsTemplateSetup = res.optBoolean("needs_template_setup"),
        )
    }

    suspend fun clientConfig(): ClientConfig {
        val res = call("GET", "/v1/client-config")
        val templateArray = res.optJSONArray("templates") ?: JSONArray()
        val templates = (0 until templateArray.length())
            .map { parseTemplate(templateArray.getJSONObject(it)) }
        val presetArray = res.optJSONArray("asr_presets") ?: JSONArray()
        val presets = (0 until presetArray.length()).map {
            val item = presetArray.getJSONObject(it)
            AsrPreset(
                id = item.optString("id"),
                name = item.optString("name"),
                url = item.optString("url"),
                apiKey = item.optString("api_key"),
            )
        }
        return ClientConfig(
            submitIntervalSec = res.optInt("submit_interval_sec", 10),
            asrPresets = presets,
            templates = templates,
        )
    }

    suspend fun saveTemplates(templateIds: List<Int>) {
        val array = JSONArray(templateIds)
        call("PUT", "/v1/me/templates", JSONObject().put("template_ids", array))
    }

    suspend fun submit(clientId: String, text: String, durationMs: Int): String {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("text", text)
            .put("duration_ms", durationMs)
            .put("asr_provider", "funasr")
        val res = call("POST", "/v1/records", body)
        return res.optString("status", "pending")
    }

    suspend fun statuses(clientIds: List<String>): List<RecordStatus> {
        val res = call("POST", "/v1/records/status", JSONObject().put("client_ids", JSONArray(clientIds)))
        val array = res.optJSONArray("records") ?: JSONArray()
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            RecordStatus(
                clientId = item.getString("client_id"),
                status = item.getString("status"),
                error = if (item.isNull("error")) null else item.optString("error"),
            )
        }
    }

    suspend fun records(limit: Int = 20): List<RecordItem> {
        val res = call("GET", "/v1/records?limit=" + limit)
        val array = res.optJSONArray("records") ?: JSONArray()
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            RecordItem(
                clientId = item.getString("client_id"),
                status = item.getString("status"),
                createdAt = item.optString("created_at"),
                templateName = if (item.isNull("template_name")) null else item.optString("template_name"),
                preview = item.optString("preview"),
            )
        }
    }

    suspend fun record(clientId: String): RecordDetail {
        val res = call("GET", "/v1/records/" + clientId)
        val record = res.getJSONObject("record")
        val doc = res.optJSONObject("document")
        return RecordDetail(
            status = record.getString("status"),
            error = if (record.isNull("error")) null else record.optString("error"),
            rawText = record.optString("raw_text"),
            document = doc?.let {
                Document(
                    bodyMd = it.optString("body_md"),
                    templateName = if (it.isNull("template_name")) null else it.optString("template_name"),
                    genreReason = if (it.isNull("genre_reason")) null else it.optString("genre_reason"),
                    flags = if (it.isNull("flags")) null else it.optString("flags"),
                )
            },
        )
    }

    suspend fun retry(clientId: String) {
        call("POST", "/v1/records/" + clientId + "/retry")
    }

    suspend fun renderError(clientId: String, detail: String) {
        call("POST", "/v1/records/" + clientId + "/render-error", JSONObject().put("detail", detail))
    }
}
