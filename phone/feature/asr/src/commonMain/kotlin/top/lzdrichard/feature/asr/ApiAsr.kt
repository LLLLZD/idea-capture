package top.lzdrichard.feature.asr

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 把录音文件 multipart 交给 fun-asr（或任何 OpenAI 风格 /audio/transcriptions）。
 * 只认 text 与 OpenAI choices 两种返回，解析不出来就当失败，避免把整段 JSON 当正文提交。
 */
class ApiAsrEngine(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String = "fun-asr-nano",
) : AsrEngine {

    override val provider: String = "funasr"
    override val model: String = modelName

    override suspend fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun transcribe(audio: AsrAudio): String {
        if (baseUrl.isBlank()) throw AsrUnavailableException("还没设置转写地址")
        val bytes = PlatformAudio.readBytes(audio.path)
            ?: throw AsrFailedException("录音文件不见了")
        if (bytes.size < 4096) throw AsrFailedException("录音太短，没听到声音")

        val response: HttpResponse = try {
            client.post(baseUrl.trimEnd('/') + "/audio/transcriptions") {
                if (apiKey.isNotBlank()) {
                    header(HttpHeaders.Authorization, if (apiKey.startsWith("Bearer ")) apiKey else "Bearer " + apiKey)
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("model", modelName)
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, audio.mime)
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.m4a\"")
                                },
                            )
                        },
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AsrNetworkException(e.message ?: "连不上转写服务", e)
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw AsrFailedException(describe(response.status.value))
        return parseText(body) ?: throw AsrFailedException("没听到语音，检查一下麦克风或被遮挡了")
    }

    private fun describe(code: Int): String = when (code) {
        401, 403 -> "转写服务没通过验证"
        413 -> "录音太长了"
        in 500..599 -> "转写服务出错了（HTTP " + code + "）"
        else -> "转写失败（HTTP " + code + "）"
    }

    private fun parseText(raw: String): String? {
        val json = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        json["text"]?.jsonPrimitive?.contentOrEmpty()?.takeIf { it.isNotBlank() }?.let { return it }
        val choices = json["choices"] ?: return null
        val first = runCatching { choices.jsonArray.first().jsonObject }.getOrNull() ?: return null
        val text = first["text"]?.jsonPrimitive?.contentOrEmpty()
            ?: first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrEmpty()
        return text?.takeIf { it.isNotBlank() }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String =
    runCatching { content }.getOrDefault("")
