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
