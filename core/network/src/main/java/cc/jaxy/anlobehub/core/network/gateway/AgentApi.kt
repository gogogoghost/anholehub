package cc.jaxy.anlobehub.core.network.gateway

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `aiAgent.execAgent` 返回（字段全可空兜底，未知字段靠 NetworkJson 忽略）。
 *
 * 服务端完整形态见 `packages/types/src/agentExecution` 的 `ExecAgentResult`，
 * 移动端只取网关建连必需的 operationId + token，其余用于上层回填本地行。
 */
@Serializable
data class ExecAgentResult(
    val operationId: String? = null,
    val token: String? = null,
    val topicId: String? = null,
    val userMessageId: String? = null,
    val assistantMessageId: String? = null,
)

/**
 * Agent 执行入口（tRPC `aiAgent.*` + `config.getGlobalConfig`）。
 *
 * - [execAgent] 固定 `clientProtocol = 2`（服务端按 v2 下发 `message_patch`，
 *   本端 WS 同样按 v2 优先建连；老网关回退逻辑由 [GatewaySocket] 处理）。
 * - [interruptTask] 取消走服务端状态机（WS `interrupt` 帧网关直接忽略，
 *   见 `agent-gateway-client` 注释），不断开 socket。
 * - [globalConfig] 网关地址来自 `serverConfig.agentGatewayUrl`，
 *   协议 `agentGatewayProtocol` 缺省 1。
 */
@Singleton
class AgentApi @Inject constructor(
    private val trpc: TrpcClient,
    private val json: Json,
) {

    suspend fun execAgent(
        baseUrl: String,
        prompt: String,
        topicId: String? = null,
        sessionId: String? = null,
        agentId: String? = null,
        slug: String? = null,
        model: String? = null,
        provider: String? = null,
    ): AnResult<ExecAgentResult> {
        if (prompt.isBlank()) {
            return AnResult.Err(AnError("INVALID_INPUT", "prompt is required"))
        }
        val input = buildJsonObject {
            put("prompt", prompt)
            put("clientProtocol", 2)
            if (!agentId.isNullOrBlank()) put("agentId", agentId)
            if (!slug.isNullOrBlank()) put("slug", slug)
            if (model != null) put("model", model)
            if (provider != null) put("provider", provider)
            if (topicId != null || sessionId != null) {
                putJsonObject("appContext") {
                    if (topicId != null) put("topicId", topicId)
                    if (sessionId != null) put("sessionId", sessionId)
                }
            }
        }
        return trpc.mutate(baseUrl, "aiAgent.execAgent", input) { element ->
            json.decodeFromJsonElement(ExecAgentResult.serializer(), element)
        }
    }

    suspend fun interruptTask(
        baseUrl: String,
        operationId: String? = null,
    ): AnResult<Unit> {
        if (operationId.isNullOrBlank()) {
            return AnResult.Err(AnError("INVALID_INPUT", "operationId is required"))
        }
        val input = buildJsonObject { put("operationId", operationId) }
        return trpc.mutate(baseUrl, "aiAgent.interruptTask", input) { Unit }
    }

    /** `config.getGlobalConfig` 是 query（GET），网关地址从 `serverConfig` 里抠。 */
    suspend fun globalConfig(baseUrl: String): AnResult<GatewayEndpoint> {
        return when (
            val result = trpc.query(baseUrl, "config.getGlobalConfig", null) { element ->
                parseEndpoint(element)
            }
        ) {
            is AnResult.Ok -> result.value?.let { AnResult.Ok(it) }
                ?: AnResult.Err(AnError("UNKNOWN", "missing agentGatewayUrl"))
            is AnResult.Err -> AnResult.Err(result.error)
        }
    }

    /** `config.getGlobalConfig` 形态庞大且只增不减，手动取两个字段。 */
    private fun parseEndpoint(element: JsonElement): GatewayEndpoint? {
        val serverConfig = (element as? JsonObject)?.get("serverConfig") as? JsonObject
            ?: return null
        val url = (serverConfig["agentGatewayUrl"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return null
        val protocolElement = serverConfig["agentGatewayProtocol"] as? JsonPrimitive
        val protocol = protocolElement?.intOrNull
            ?: protocolElement?.contentOrNull?.toIntOrNull() ?: 1
        return GatewayEndpoint(url = url.trimEnd('/'), protocol = protocol)
    }
}
