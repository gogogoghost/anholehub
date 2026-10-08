package cc.jaxy.anlobehub.core.data.discovery

import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.result.getOrNull
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class KnowledgeBase(
    val id: String = "",
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class AssistantBrief(
    val id: String = "",
    val title: String? = null,
    val description: String? = null,
    val avatar: String? = null,
)

interface DiscoveryRepository {
    suspend fun listKnowledgeBases(baseUrl: String): AnResult<List<KnowledgeBase>>
    suspend fun listAgents(baseUrl: String): AnResult<List<AssistantBrief>>
}

@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : DiscoveryRepository {

    override suspend fun listKnowledgeBases(baseUrl: String): AnResult<List<KnowledgeBase>> {
        // 无 input 探测；任一成功即返回。
        val r = trpc.query(baseUrl, "knowledgeBase.getKnowledgeBases", null) { it.asKnowledgeBaseList() }
        val list = r.getOrNull()
        if (list != null) return AnResult.Ok(list)
        // 全失败不报错，返回空列表（PWA 对齐：discovery 区可空）。
        return AnResult.Ok(emptyList())
    }

    override suspend fun listAgents(baseUrl: String): AnResult<List<AssistantBrief>> {
        val r = trpc.query(baseUrl, "agent.getAgents", null) { it.asAssistantBriefList() }
        val list = r.getOrNull()
        if (list != null) return AnResult.Ok(list)
        return AnResult.Ok(emptyList())
    }
}

fun JsonElement.asKnowledgeBaseList(): List<KnowledgeBase> {
    return try {
        asItemArray().map { it.toKnowledgeBase() }
    } catch (_: Exception) {
        emptyList()
    }
}

fun JsonElement.asAssistantBriefList(): List<AssistantBrief> {
    return try {
        asItemArray().map { it.toAssistantBrief() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 兼容 items / knowledgeBases / agents / list / data / 纯数组。 */
private fun JsonElement.asItemArray(): List<JsonElement> {
    if (this is JsonArray) return this
    if (this is JsonNull) return emptyList()
    val obj = this as? JsonObject ?: return emptyList()
    for (key in listOf("items", "knowledgeBases", "agents", "list", "data")) {
        when (val v = obj[key]) {
            is JsonArray -> return v
            is JsonObject -> {
                // 兼容 { data: { items: [...] } } 一层嵌套。
                val nested = v["items"] as? JsonArray ?: v["list"] as? JsonArray
                if (nested != null) return nested
            }
            else -> Unit
        }
    }
    return emptyList()
}

private fun JsonElement.toKnowledgeBase(): KnowledgeBase {
    val obj = this as? JsonObject ?: return KnowledgeBase()
    return KnowledgeBase(
        id = obj.stringOrNull("id") ?: "",
        title = obj.stringOrNull("title") ?: obj.stringOrNull("name"),
        description = obj.stringOrNull("description"),
    )
}

private fun JsonElement.toAssistantBrief(): AssistantBrief {
    val obj = this as? JsonObject ?: return AssistantBrief()
    return AssistantBrief(
        id = obj.stringOrNull("id") ?: "",
        title = obj.stringOrNull("title") ?: obj.stringOrNull("name"),
        description = obj.stringOrNull("description"),
        avatar = obj.stringOrNull("avatar"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
