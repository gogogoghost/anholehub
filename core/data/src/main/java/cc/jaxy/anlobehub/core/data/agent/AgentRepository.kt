package cc.jaxy.anlobehub.core.data.agent

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.chat.extractId
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class Agent(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val backgroundColor: String? = null,
    val isInbox: Boolean = false,
) {
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            !title.isNullOrBlank() -> title
            else -> id
        }
}

@Serializable
data class AgentConfig(
    val title: String? = null,
    val description: String? = null,
    val systemRole: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val temperature: Double? = null,
    val avatar: String? = null,
    val backgroundColor: String? = null,
    val openingMessage: String? = null,
    val openingQuestions: List<String> = emptyList(),
)

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonElement.toAgent(): Agent? {
    val obj = this as? JsonObject ?: return null
    val id = obj.stringOrNull("id") ?: return null
    if (id.isBlank()) return null
    val isInbox = (obj["isInbox"] as? JsonPrimitive)?.contentOrNull
        ?.toBooleanStrictOrNull() ?: (id == "inbox")
    return Agent(
        id = id,
        title = obj.stringOrNull("title"),
        name = obj.stringOrNull("name"),
        description = obj.stringOrNull("description"),
        avatar = obj.stringOrNull("avatar"),
        backgroundColor = obj.stringOrNull("backgroundColor")
            ?: obj.stringOrNull("background"),
        isInbox = isInbox,
    )
}

fun JsonElement.asAgentList(): List<Agent> {
    return try {
        val items: List<JsonElement>? = when (this) {
            is JsonArray -> this.toList()
            is JsonObject -> {
                val arr = keys.firstNotNullOfOrNull { k ->
                    if (k == "items" || k == "agents" || k == "list" ||
                        k == "data" || k == "rows"
                    ) {
                        this[k] as? JsonArray
                    } else {
                        null
                    }
                }
                arr?.toList()
            }
            else -> null
        } ?: return emptyList()
        val rows = items ?: return emptyList()
        rows.mapNotNull {
            try {
                it.toAgent()
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun JsonElement.toAgentConfig(): AgentConfig {
    val obj = this as? JsonObject ?: return AgentConfig()
    val params = obj["params"] as? JsonObject
    val temperature = (obj["temperature"] as? JsonPrimitive)?.doubleOrNull
        ?: (params?.get("temperature") as? JsonPrimitive)?.doubleOrNull
    return AgentConfig(
        title = obj.stringOrNull("title"),
        description = obj.stringOrNull("description"),
        systemRole = obj.stringOrNull("systemRole"),
        model = obj.stringOrNull("model"),
        provider = obj.stringOrNull("provider"),
        temperature = temperature,
        avatar = obj.stringOrNull("avatar"),
        backgroundColor = obj.stringOrNull("backgroundColor")
            ?: obj.stringOrNull("background"),
        openingMessage = obj.stringOrNull("openingMessage"),
        openingQuestions = (obj["openingQuestions"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { q -> q.isNotBlank() } }
            .orEmpty(),
    )
}

private fun JsonElement.extractAgentId(): String? {
    if (this is JsonPrimitive) {
        return contentOrNull?.takeIf { it.isNotBlank() }
    }
    val obj = this as? JsonObject ?: return null
    obj.stringOrNull("agentId")?.takeIf { it.isNotBlank() }?.let { return it }
    return extractId()
}

interface AgentRepository {
    suspend fun listAgents(
        baseUrl: String,
        keyword: String? = null,
    ): AnResult<List<Agent>>

    suspend fun getConfig(baseUrl: String, agentId: String): AnResult<AgentConfig>

    suspend fun create(
        baseUrl: String,
        title: String,
        systemRole: String? = null,
        model: String? = null,
        provider: String? = null,
        description: String? = null,
        avatar: String? = null,
        openingMessage: String? = null,
        openingQuestions: List<String> = emptyList(),
    ): AnResult<String>

    suspend fun updateConfig(
        baseUrl: String,
        agentId: String,
        fields: Map<String, JsonElement?>,
    ): AnResult<Unit>

    suspend fun remove(baseUrl: String, agentId: String): AnResult<Unit>

    suspend fun duplicate(baseUrl: String, agentId: String): AnResult<String>
}

@Singleton
class AgentRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : AgentRepository {

    override suspend fun listAgents(baseUrl: String, keyword: String?): AnResult<List<Agent>> {
        val input = buildJsonObject {
            put("includeInbox", true)
            put("limit", 100)
            if (!keyword.isNullOrBlank()) put("keyword", keyword)
        }
        return trpc.query(baseUrl, "agent.queryAgents", input) { el ->
            el.asAgentList().sortedWith(
                compareByDescending<Agent> { it.isInbox }.thenBy { it.displayName.lowercase() },
            )
        }
    }

    override suspend fun getConfig(baseUrl: String, agentId: String): AnResult<AgentConfig> {
        if (agentId.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "agentId is required"))
        }
        val input = buildJsonObject { put("agentId", agentId) }
        return trpc.query(baseUrl, "agent.getAgentConfigById", input) { it.toAgentConfig() }
    }

    override suspend fun create(
        baseUrl: String,
        title: String,
        systemRole: String?,
        model: String?,
        provider: String?,
        description: String?,
        avatar: String?,
        openingMessage: String?,
        openingQuestions: List<String>,
    ): AnResult<String> {
        if (title.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "title is required"))
        }
        val input = buildJsonObject {
            putJsonObject("config") {
                put("title", title)
                if (!systemRole.isNullOrBlank()) put("systemRole", systemRole)
                if (!model.isNullOrBlank()) put("model", model)
                if (!provider.isNullOrBlank()) put("provider", provider)
                if (!description.isNullOrBlank()) put("description", description)
                if (!avatar.isNullOrBlank()) put("avatar", avatar)
                if (!openingMessage.isNullOrBlank()) put("openingMessage", openingMessage)
                val questions = openingQuestions.filter { it.isNotBlank() }
                if (questions.isNotEmpty()) {
                    put("openingQuestions", buildJsonArray { questions.forEach { add(JsonPrimitive(it)) } })
                }
            }
        }
        return trpc.mutate(baseUrl, "agent.createAgent", input) { el ->
            el.extractAgentId()
                ?: throw IllegalStateException("missing agentId in createAgent response")
        }
    }

    override suspend fun updateConfig(
        baseUrl: String,
        agentId: String,
        fields: Map<String, JsonElement?>,
    ): AnResult<Unit> {
        if (agentId.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "agentId is required"))
        }
        val value = buildJsonObject {
            for ((key, element) in fields) {
                put(key, element ?: JsonNull)
            }
        }
        val input = buildJsonObject {
            put("agentId", agentId)
            put("value", value)
        }
        return trpc.mutate(baseUrl, "agent.updateAgentConfig", input) { }
    }

    override suspend fun remove(baseUrl: String, agentId: String): AnResult<Unit> {
        if (agentId.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "agentId is required"))
        }
        val input = buildJsonObject { put("agentId", agentId) }
        return trpc.mutate(baseUrl, "agent.removeAgent", input) { }
    }

    override suspend fun duplicate(baseUrl: String, agentId: String): AnResult<String> {
        if (agentId.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "agentId is required"))
        }
        val input = buildJsonObject { put("agentId", agentId) }
        return trpc.mutate(baseUrl, "agent.duplicateAgent", input) { el ->
            el.extractAgentId()
                ?: throw IllegalStateException("missing agentId in duplicateAgent response")
        }
    }
}
