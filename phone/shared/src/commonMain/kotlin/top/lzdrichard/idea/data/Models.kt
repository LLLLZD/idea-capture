package top.lzdrichard.idea.data

data class Template(
    val id: Int,
    val key: String,
    val name: String,
    val criterion: String,
    val sampleMd: String,
    val wordRange: String,
    val isBuiltin: Boolean,
    val selected: Boolean,
)

data class AsrPreset(
    val id: String,
    val name: String,
    val url: String,
    val apiKey: String,
)

data class ClientConfig(
    val submitIntervalSec: Int,
    val asrPresets: List<AsrPreset>,
    val templates: List<Template>,
)

data class LoginResult(
    val token: String,
    val displayName: String,
    val needsTemplateSetup: Boolean,
)

data class RecordItem(
    val clientId: String,
    val status: String,
    val createdAt: String,
    val templateName: String?,
    val preview: String,
)

data class RecordStatus(
    val clientId: String,
    val status: String,
    val error: String?,
)

data class Document(
    val bodyMd: String,
    val templateName: String?,
    val genreReason: String?,
    val flags: String?,
)

data class RecordDetail(
    val status: String,
    val error: String?,
    val rawText: String,
    val document: Document?,
)

/** 服务端明确拒绝（4xx/5xx 带业务错误），与网络不通区分开。 */
class ApiException(val statusCode: Int, message: String) : Exception(message)

/** 连不上、超时、断网；这类失败要保住用户说的话，排队重试。 */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
