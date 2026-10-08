package cc.jaxy.anlobehub.core.data.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ChatTopic(
    val id: String = "",
    val title: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val sessionId: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val role: String = "assistant",
    val content: String = "",
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val model: String? = null,
    val provider: String? = null,
    val error: String? = null,
)

@Serializable
data class ModelAbilities(
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val functionCall: Boolean = false,
    val files: Boolean = false,
    val audio: Boolean = false,
    val video: Boolean = false,
    val imageOutput: Boolean = false,
    val search: Boolean = false,
    val structuredOutput: Boolean = false,
)

@Serializable
data class AIModel(
    val id: String = "",
    val displayName: String? = null,
    val providerId: String? = null,
    val providerName: String? = null,
    val enabled: Boolean? = null,
    val type: String? = null,
    val description: String? = null,
    val contextWindowTokens: Int? = null,
    val maxOutput: Int? = null,
    val abilities: ModelAbilities = ModelAbilities(),
)

fun contentToText(element: JsonElement?): String {
    return when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull ?: ""
        is JsonArray -> element.joinToString("") { contentToText(it) }
        is JsonObject -> {
            val text = element["text"]
            if (text != null) contentToText(text) else element.toString()
        }
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longOrNull(key: String): Long? {
    val prim = this[key] as? JsonPrimitive ?: return null
    if (prim.isString) {
        return prim.contentOrNull?.toLongOrNull()
    }
    return try {
        prim.contentOrNull?.toLongOrNull()
    } catch (_: Exception) {
        null
    }
}

fun JsonElement.toChatMessage(): ChatMessage {
    val obj = this as? JsonObject ?: return ChatMessage()
    val errorRaw = obj["error"]
    val errorText = when (errorRaw) {
        null, is JsonNull -> null
        is JsonPrimitive -> errorRaw.contentOrNull
        else -> errorRaw.toString().takeIf { it.isNotBlank() }
    }
    return ChatMessage(
        id = obj.stringOrNull("id") ?: "",
        role = obj.stringOrNull("role") ?: "assistant",
        content = contentToText(obj["content"]),
        createdAt = obj.longOrNull("createdAt"),
        updatedAt = obj.longOrNull("updatedAt"),
        model = obj.stringOrNull("model"),
        provider = obj.stringOrNull("provider"),
        error = errorText,
    )
}

fun JsonElement.toChatTopic(): ChatTopic {
    val obj = this as? JsonObject ?: return ChatTopic()
    return ChatTopic(
        id = obj.stringOrNull("id") ?: "",
        title = obj.stringOrNull("title") ?: obj.stringOrNull("name"),
        createdAt = obj.longOrNull("createdAt"),
        updatedAt = obj.longOrNull("updatedAt"),
        sessionId = obj.stringOrNull("sessionId"),
    )
}

/** String 或 {id} 两种返回形状兼容提取。 */
fun JsonElement.extractId(): String? {
    if (this is JsonPrimitive) {
        return contentOrNull?.takeIf { it.isNotBlank() }
    }
    val obj = this as? JsonObject ?: return null
    obj.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { return it }
    // 兼容 { result: id } / { data: id } 等单层包装。
    for (key in listOf("result", "data", "topicId", "sessionId")) {
        val nested = obj[key] ?: continue
        if (nested is JsonPrimitive) {
            nested.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        } else if (nested is JsonObject) {
            nested.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return null
}

fun JsonElement.asMessageList(): List<ChatMessage> {
    return try {
        when (this) {
            is JsonArray -> map { it.toChatMessage() }
            is JsonObject -> {
                val arr = keys.firstNotNullOfOrNull { k ->
                    if (k == "items" || k == "messages" || k == "list" || k == "data") {
                        this[k] as? JsonArray
                    } else {
                        null
                    }
                } ?: return emptyList()
                arr.map { it.toChatMessage() }
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun JsonElement.asTopicList(): List<ChatTopic> {
    return try {
        when (this) {
            is JsonArray -> map { it.toChatTopic() }
            is JsonObject -> {
                val arr = (this["items"] as? JsonArray) ?: return emptyList()
                arr.map { it.toChatTopic() }
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun JsonElement.asAIModelList(): List<AIModel> {
    return try {
        val obj = this as? JsonObject ?: return emptyList()
        // Shape 1: { providers: [{ id, name?, models: [...] }] }
        (obj["providers"] as? JsonArray)?.let { providers ->
            val out = mutableListOf<AIModel>()
            for (p in providers) {
                val pObj = p as? JsonObject ?: continue
                val providerId = pObj.stringOrNull("id")
                val providerName = pObj.stringOrNull("name")
                    ?: pObj.stringOrNull("displayName")
                    ?: pObj.stringOrNull("display_name")
                val models = pObj["models"] as? JsonArray ?: continue
                for (m in models) {
                    val mObj = m as? JsonObject ?: continue
                    runCatching {
                        mObj.toAIModel(
                            fallbackProviderId = providerId,
                            fallbackProviderName = providerName,
                        )
                    }.getOrNull()?.let { out += it }
                }
            }
            if (out.isNotEmpty()) return out
        }
        // Shape 2: { models: [...] }
        (obj["models"] as? JsonArray)?.let { models ->
            return models.mapNotNull { m ->
                val mObj = m as? JsonObject ?: return@mapNotNull null
                runCatching { mObj.toAIModel() }.getOrNull()
            }
        }
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun JsonObject.toAIModel(
    fallbackProviderId: String? = null,
    fallbackProviderName: String? = null,
): AIModel {
    val id = stringOrNull("id") ?: stringOrNull("model")
        ?: throw IllegalArgumentException("model without id")
    val abilities = (this["abilities"] as? JsonObject)?.let { a ->
        fun flag(key: String): Boolean =
            (a[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
        ModelAbilities(
            vision = flag("vision"),
            reasoning = flag("reasoning"),
            functionCall = flag("functionCall"),
            files = flag("files"),
            audio = flag("audio"),
            video = flag("video"),
            imageOutput = flag("imageOutput"),
            search = flag("search"),
            structuredOutput = flag("structuredOutput"),
        )
    } ?: ModelAbilities()
    return AIModel(
        id = id,
        displayName = stringOrNull("displayName")
            ?: stringOrNull("display_name")
            ?: stringOrNull("name"),
        providerId = stringOrNull("providerId") ?: fallbackProviderId,
        providerName = stringOrNull("providerName")
            ?: stringOrNull("provider")?.takeIf { it != stringOrNull("providerId") }
            ?: fallbackProviderName,
        enabled = (this["enabled"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull(),
        type = stringOrNull("type"),
        description = stringOrNull("description"),
        contextWindowTokens = (this["contextWindowTokens"] as? JsonPrimitive)
            ?.contentOrNull?.toIntOrNull(),
        maxOutput = (this["maxOutput"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
        abilities = abilities,
    )
}
