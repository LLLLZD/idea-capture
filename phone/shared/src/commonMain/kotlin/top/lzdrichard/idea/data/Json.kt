package top.lzdrichard.idea.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** 解析小工具：服务端字段可能缺失或为 null，统一按可选处理。 */
object Js {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseObject(raw: String): JsonObject =
        if (raw.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(raw).jsonObject

    fun obj(element: JsonElement?): JsonObject? = element as? JsonObject

    fun arr(element: JsonElement?): JsonArray? = element as? JsonArray

    fun str(element: JsonElement?): String? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString && primitive.content.isBlank()) return "" else if (primitive.isString) return primitive.content
        return primitive.content.takeIf { it != "null" }
    }

    fun strOrNull(element: JsonElement?): String? {
        val value = str(element) ?: return null
        return value.ifBlank { null }
    }

    fun int(element: JsonElement?, fallback: Int = 0): Int = str(element)?.toIntOrNull() ?: fallback

    fun bool(element: JsonElement?, fallback: Boolean = false): Boolean = when (str(element)) {
        "true" -> true
        "false" -> false
        else -> fallback
    }

    fun long(element: JsonElement?, fallback: Long = 0L): Long = str(element)?.toLongOrNull() ?: fallback
}
