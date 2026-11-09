package top.lzdrichard.idea.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class Api(private val client: HttpClient) {

    var baseUrl: String = Prefs.DEFAULT_SERVER
    var token: String? = null

    private suspend fun call(method: HttpMethod, path: String, body: JsonObject? = null): JsonObject {
        val response = try {
            client.request(baseUrl.trimEnd('/') + path) {
                this.method = method
                token?.let { header(HttpHeaders.Authorization, "Bearer " + it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkException(e.message ?: "连不上服务端", e)
        }

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, describe(text, response.status.value))
        }
        return try {
            Js.parseObject(text)
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
    }

    private fun describe(text: String, code: Int): String {
        val json = try {
            Js.parseObject(text)
        } catch (e: Exception) {
            null
        }
        val detail = json?.let { Js.strOrNull(it["detail"]) }
        if (detail != null) return detail
        val error = json?.let { Js.strOrNull(it["error"]) }
        return when {
            error == null -> "请求失败（HTTP " + code + "）"
            error == "rate_limited" -> "提交太频繁，稍等一下"
            error == "duplicated" -> "这条已经交过了"
            else -> error
        }
    }

    private fun parseTemplate(json: JsonObject) = Template(
        id = Js.int(json["id"]),
        key = Js.str(json["key"]).orEmpty(),
        name = Js.str(json["name"]).orEmpty(),
        criterion = Js.str(json["criterion"]).orEmpty(),
        sampleMd = Js.str(json["sample_md"]).orEmpty(),
        wordRange = Js.str(json["word_range"]).orEmpty(),
        isBuiltin = Js.bool(json["is_builtin"]),
        selected = Js.bool(json["selected"]),
    )

    suspend fun login(username: String, password: String): LoginResult {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val res = call(HttpMethod.Post, "/v1/auth/login", body)
        val user = Js.obj(res["user"])
        return LoginResult(
            token = Js.str(res["token"]).orEmpty(),
            displayName = user?.let { Js.str(it["display_name"]) }.orEmpty(),
            needsTemplateSetup = Js.bool(res["needs_template_setup"]),
        )
    }

    suspend fun clientConfig(): ClientConfig {
        val res = call(HttpMethod.Get, "/v1/client-config")
        val templates = Js.arr(res["templates"])?.mapNotNull { Js.obj(it)?.let(::parseTemplate) }.orEmpty()
        val presets = Js.arr(res["asr_presets"])?.mapNotNull { element ->
            val item = Js.obj(element) ?: return@mapNotNull null
            AsrPreset(
                id = Js.str(item["id"]).orEmpty(),
                name = Js.str(item["name"]).orEmpty(),
                url = Js.str(item["url"]).orEmpty(),
                apiKey = Js.str(item["api_key"]).orEmpty(),
            )
        }.orEmpty()
        return ClientConfig(
            submitIntervalSec = Js.int(res["submit_interval_sec"], 10),
            asrPresets = presets,
            templates = templates,
        )
    }

    suspend fun saveTemplates(templateIds: List<Int>) {
        val array = JsonArray(templateIds.map { JsonPrimitive(it) })
        val body = buildJsonObject { put("template_ids", array) }
        call(HttpMethod.Put, "/v1/me/templates", body)
    }

    /** provider/model 记录这条稿子是哪条链路转出来的，界面不提示但落库可查。 */
    suspend fun submit(
        clientId: String,
        text: String,
        durationMs: Int,
        provider: String?,
        model: String?,
    ): String {
        val body = buildJsonObject {
            put("client_id", clientId)
            put("text", text)
            put("duration_ms", durationMs)
            if (!provider.isNullOrBlank()) put("asr_provider", provider)
            if (!model.isNullOrBlank()) put("asr_model", model)
        }
        val res = call(HttpMethod.Post, "/v1/records", body)
        return Js.str(res["status"]).orEmpty().ifBlank { "pending" }
    }

    suspend fun statuses(clientIds: List<String>): List<RecordStatus> {
        val array = buildJsonArray { clientIds.forEach { add(JsonPrimitive(it)) } }
        val body = buildJsonObject { put("client_ids", array) }
        val res = call(HttpMethod.Post, "/v1/records/status", body)
        return Js.arr(res["records"])?.mapNotNull { element ->
            val item = Js.obj(element) ?: return@mapNotNull null
            RecordStatus(
                clientId = Js.str(item["client_id"]).orEmpty(),
                status = Js.str(item["status"]).orEmpty(),
                error = Js.strOrNull(item["error"]),
            )
        }.orEmpty()
    }

    suspend fun records(limit: Int = 20): List<RecordItem> {
        val res = call(HttpMethod.Get, "/v1/records?limit=" + limit)
        return Js.arr(res["records"])?.mapNotNull { element ->
            val item = Js.obj(element) ?: return@mapNotNull null
            RecordItem(
                clientId = Js.str(item["client_id"]).orEmpty(),
                status = Js.str(item["status"]).orEmpty(),
                createdAt = Js.str(item["created_at"]).orEmpty(),
                templateName = Js.strOrNull(item["template_name"]),
                preview = Js.str(item["preview"]).orEmpty(),
            )
        }.orEmpty()
    }

    suspend fun record(clientId: String): RecordDetail {
        val res = call(HttpMethod.Get, "/v1/records/" + clientId)
        val record = Js.obj(res["record"]) ?: throw ApiException(500, "服务端返回格式不对")
        val doc = Js.obj(res["document"])
        return RecordDetail(
            status = Js.str(record["status"]).orEmpty(),
            error = Js.strOrNull(record["error"]),
            rawText = Js.str(record["raw_text"]).orEmpty(),
            document = doc?.let {
                Document(
                    bodyMd = Js.str(it["body_md"]).orEmpty(),
                    templateName = Js.strOrNull(it["template_name"]),
                    genreReason = Js.strOrNull(it["genre_reason"]),
                    flags = Js.strOrNull(it["flags"]),
                )
            },
        )
    }

    suspend fun retry(clientId: String) {
        call(HttpMethod.Post, "/v1/records/" + clientId + "/retry")
    }

    suspend fun renderError(clientId: String, detail: String) {
        val body = buildJsonObject { put("detail", detail) }
        call(HttpMethod.Post, "/v1/records/" + clientId + "/render-error", body)
    }
}
