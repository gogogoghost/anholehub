package cc.jaxy.anlobehub.core.data.provider

import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * AI 供应商条目（`aiProvider.getAiProviderList` 返回）。
 *
 * 全字段可空：`enabled` 服务端恒返 bool，缺省按 true 处理。
 */
data class AiProvider(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val source: String? = null,
)

interface ProviderRepository {
    suspend fun listProviders(baseUrl: String): AnResult<List<AiProvider>>

    suspend fun toggleProvider(
        baseUrl: String,
        id: String,
        enabled: Boolean,
    ): AnResult<Unit>
}

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : ProviderRepository {

    override suspend fun listProviders(baseUrl: String): AnResult<List<AiProvider>> {
        // 无 input：传空 object。
        return trpc.query(baseUrl, "aiProvider.getAiProviderList", buildJsonObject { }) {
            it.asAiProviderList()
        }
    }

    override suspend fun toggleProvider(
        baseUrl: String,
        id: String,
        enabled: Boolean,
    ): AnResult<Unit> {
        val input = buildJsonObject {
            put("id", id)
            put("enabled", enabled)
        }
        // 官方供应商禁关：服务端抛 BAD_REQUEST，错误经 AnResult.Err 透给 UI。
        return trpc.mutate(baseUrl, "aiProvider.toggleProviderEnabled", input) { }
    }
}

fun JsonElement.asAiProviderList(): List<AiProvider> {
    return try {
        val items: List<JsonElement> = when (this) {
            is JsonArray -> this
            is JsonNull -> return emptyList()
            is JsonObject -> {
                (this["items"] as? JsonArray)
                    ?: (this["data"] as? JsonArray)
                    ?: (this["providers"] as? JsonArray)
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        items.mapNotNull { runCatching { it.toAiProvider() }.getOrNull() }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun JsonElement.toAiProvider(): AiProvider {
    val obj = this as? JsonObject ?: return AiProvider()
    fun str(key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return AiProvider(
        id = str("id") ?: "",
        name = str("name"),
        description = str("description"),
        enabled = (obj["enabled"] as? JsonPrimitive)?.contentOrNull
            ?.toBooleanStrictOrNull() ?: true,
        source = str("source"),
    )
}
