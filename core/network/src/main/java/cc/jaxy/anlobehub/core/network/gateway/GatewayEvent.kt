package cc.jaxy.anlobehub.core.network.gateway

/**
 * Agent Gateway 流式事件（v1 / v2 统一映射后的领域形态）。
 *
 * 服务端 22 种 `AgentStreamEvent` 只取聊天渲染必需的子集：
 * chunk / start / end / terminal / error，其余已知非终结事件直接忽略、
 * 未知报文以 [Unknown] 原样透出（不断流，由上层决定忽略或落盘排查）。
 */
sealed interface GatewayEvent {

    /** 文本 / 推理增量（上层负责按 operationId 累加）。 */
    data class StreamChunk(
        val operationId: String,
        val textDelta: String,
        val reasoningDelta: String? = null,
    ) : GatewayEvent

    /** 服务端为本轮创建了 assistant 消息（首包种子）。 */
    data class StreamStarted(
        val operationId: String,
        val assistantMessageId: String? = null,
    ) : GatewayEvent

    /** 本轮可见输出结束（操作可能仍在收尾，以 [RunEnded] 为准）。 */
    data class StreamEnded(val operationId: String) : GatewayEvent

    /** 工具调用开始/结束（流式 steps 行素材；name 尽力提取）。 */
    data class ToolStarted(val operationId: String, val name: String?) : GatewayEvent
    data class ToolEnded(val operationId: String, val name: String?) : GatewayEvent

    /** 运行终结：`agent_runtime_end` / `session_complete` / terminal status。 */
    data class RunEnded(
        val operationId: String,
        val terminal: Boolean,
    ) : GatewayEvent

    /** 运行错误（含 v1 `auth_expired` 上报的 `AUTH_EXPIRED`，由上层刷新 token 重连）。 */
    data class RunError(
        val operationId: String,
        val message: String,
    ) : GatewayEvent

    /** 无法识别的原始报文。 */
    data class Unknown(val raw: String) : GatewayEvent
}

/** 网关 WS 地址与协议版本（1 = v1 单 op 直连，2 = v2 复用；缺省 1）。 */
data class GatewayEndpoint(
    val url: String,
    val protocol: Int = 1,
)

/** 一次运行的句柄（`execAgent` 返回 + 网关回填，上层Repository持有）。 */
data class GatewayRunHandle(
    val operationId: String,
    val assistantMessageId: String? = null,
    val topicId: String? = null,
)
