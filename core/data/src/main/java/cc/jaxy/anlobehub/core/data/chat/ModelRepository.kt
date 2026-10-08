package cc.jaxy.anlobehub.core.data.chat

import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Per-model reasoning config (personal preference, ungated procedures).
 *
 * The server schema carries ~25 vendor-specific effort keys; clients only
 * need the generic ones plus passthrough. Unknown keys are preserved on
 * read and sent back untouched on write.
 */
data class ReasoningConfig(
    /** Generic effort: low/medium/high/max (OpenAI-style `effort`). */
    val effort: String? = null,
    /** Generic effort: low/medium/high (`reasoningEffort`). */
    val reasoningEffort: String? = null,
    /** Gemini thinking level: minimal/low/medium/high. */
    val thinkingLevel: String? = null,
    /** Raw server object for vendor-specific keys. */
    val raw: Map<String, String> = emptyMap(),
)

interface ModelRepository {
    suspend fun listModels(baseUrl: String): AnResult<List<AIModel>>

    suspend fun getReasoningConfig(
        baseUrl: String,
        modelId: String,
        providerId: String,
    ): AnResult<ReasoningConfig>

    suspend fun updateReasoningConfig(
        baseUrl: String,
        modelId: String,
        providerId: String,
        value: Map<String, String>,
    ): AnResult<Unit>
}

@Singleton
class ModelRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : ModelRepository {

    override suspend fun listModels(baseUrl: String): AnResult<List<AIModel>> {
        val input = buildJsonObject { put("isLogin", true) }
        return trpc.query(baseUrl, "aiProvider.getAiProviderRuntimeState", input) { it.asAIModelList() }
    }

    override suspend fun getReasoningConfig(
        baseUrl: String,
        modelId: String,
        providerId: String,
    ): AnResult<ReasoningConfig> {
        val input = buildJsonObject {
            put("id", modelId)
            put("providerId", providerId)
        }
        return trpc.query(baseUrl, "aiModel.getAiModelReasoningConfig", input) { el ->
            val obj = el as? JsonObject ?: return@query ReasoningConfig()
            fun str(key: String): String? =
                (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val raw = obj.entries.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { k to it }
            }.toMap()
            ReasoningConfig(
                effort = str("effort"),
                reasoningEffort = str("reasoningEffort"),
                thinkingLevel = str("thinkingLevel"),
                raw = raw,
            )
        }
    }

    override suspend fun updateReasoningConfig(
        baseUrl: String,
        modelId: String,
        providerId: String,
        value: Map<String, String>,
    ): AnResult<Unit> {
        val input = buildJsonObject {
            put("id", modelId)
            put("providerId", providerId)
            put("value", buildJsonObject {
                value.forEach { (k, v) -> put(k, v) }
            })
        }
        return trpc.mutate(baseUrl, "aiModel.updateAiModelReasoningConfig", input) { }
    }
}
