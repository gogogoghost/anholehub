package cc.jaxy.anlobehub.core.network.gateway

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val V1_HEARTBEAT_MS = 30_000L
private const val V2_HEARTBEAT_MS = 25_000L
private const val HEARTBEAT_JSON = """{"type":"heartbeat"}"""

/**
 * Agent Gateway WS 流式入口（OkHttp WebSocket，无自动重连：重连由上层 Repository 决定）。
 *
 * - v1（`protocol != 2`）：`{gw}/ws?operationId=` → 发 `auth{token}` →
 *   `auth_success` → 发 `resume{lastEventId,wantStatus:true}` → 收 `agent_event`。
 * - v2：`{gw}/v2/ws?token=&clientId=anlobehub-<uuid>` → 等 `ready` →
 *   发 `subscribe{operationId,lastEventId?}` → 收同形 `agent_event`。
 * - 终结即关流：[GatewayEvent.RunEnded] / 由事件映射出的 [GatewayEvent.RunError]
 *   （含 v1 `auth_expired` 上报的 `AUTH_EXPIRED`，上层刷新 token 后重建流）
 *   发出后关闭 socket 并结束 Flow；`wsFailure` / 远端 `wsClosed` 同样以
 *   [GatewayEvent.RunError] 收尾。主动取消只走 [awaitClose][kotlinx.coroutines.channels.awaitClose]，
 *   不补发 [GatewayEvent.RunError]。
 * - JSON 全宽容：报文只按 [JsonObject] 手工取字段，非法 JSON 以
 *   [GatewayEvent.Unknown] 透出不断流；已知无关事件（step/tool/patch/心跳回执等）直接忽略。
 */
@Singleton
class GatewaySocket @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    fun stream(
        endpoint: GatewayEndpoint,
        token: String,
        operationId: String,
        lastEventId: String? = null,
    ): Flow<GatewayEvent> = callbackFlow {
        val state = StreamState(operationId)
        state.socket = try {
            if (endpoint.protocol == 2) {
                connectV2(endpoint.url, token, lastEventId, state)
            } else {
                connectV1(endpoint.url, token, operationId, lastEventId, state)
            }
        } catch (e: Exception) {
            // 非法 URL 等建连前失败：直接以 RunError 终结流。
            trySend(GatewayEvent.RunError(operationId, e.message ?: "invalid gateway url"))
            close()
            return@callbackFlow
        }
        state.heartbeat = launch {
            val period = if (endpoint.protocol == 2) V2_HEARTBEAT_MS else V1_HEARTBEAT_MS
            while (true) {
                delay(period)
                state.socket?.send(HEARTBEAT_JSON)
            }
        }
        awaitClose {
            if (state.done.compareAndSet(false, true)) {
                state.heartbeat?.cancel()
                try {
                    state.socket?.close(1000, null)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun ProducerScope<GatewayEvent>.connectV1(
        gatewayUrl: String,
        token: String,
        operationId: String,
        lastEventId: String?,
        state: StreamState,
    ): WebSocket {
        val url = buildV1Url(gatewayUrl, operationId)
        val listener = gatewayListener(
            state,
            onOpen = { socket ->
                socket.send(
                    """{"type":"auth","token":${jsonEscape(token)},"clientId":${jsonEscape(state.clientId)}}""",
                )
            },
            onText = { socket, text -> handleV1Text(socket, state, lastEventId, text) },
        )
        return client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun ProducerScope<GatewayEvent>.connectV2(
        gatewayUrl: String,
        token: String,
        lastEventId: String?,
        state: StreamState,
    ): WebSocket {
        val url = buildV2Url(gatewayUrl, token, state.clientId)
        val listener = gatewayListener(
            state,
            onOpen = { },
            onText = { socket, text -> handleV2Text(socket, state, lastEventId, text) },
        )
        return client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun ProducerScope<GatewayEvent>.gatewayListener(
        state: StreamState,
        onOpen: (WebSocket) -> Unit,
        onText: (WebSocket, String) -> Unit,
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket)

        override fun onMessage(webSocket: WebSocket, text: String) = onText(webSocket, text)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            terminal(
                state,
                GatewayEvent.RunError(
                    state.operationId,
                    t.message?.takeIf { it.isNotBlank() } ?: "network error",
                ),
            )
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            terminal(
                state,
                GatewayEvent.RunError(
                    state.operationId,
                    reason.takeIf { it.isNotBlank() } ?: "connection closed",
                ),
            )
        }
    }

    private fun ProducerScope<GatewayEvent>.handleV1Text(
        socket: WebSocket,
        state: StreamState,
        lastEventId: String?,
        text: String,
    ) {
        val obj = parseObject(text)
            ?: run { trySend(GatewayEvent.Unknown(text)); return }
        when (jsonString(obj, "type")) {
            "auth_success" -> socket.send(
                """{"type":"resume","lastEventId":${jsonEscape(lastEventId ?: "")},"wantStatus":true}""",
            )

            "auth_failed" -> terminal(
                state,
                GatewayEvent.RunError(
                    state.operationId,
                    jsonString(obj, "reason") ?: "auth failed",
                ),
            )

            // token 过期但操作仍活：上层刷新 token 后重建流。
            "auth_expired" -> terminal(state, GatewayEvent.RunError(state.operationId, "AUTH_EXPIRED"))

            "agent_event" -> {
                val event = obj["event"] as? JsonObject ?: return
                dispatchAgentEvent(state, event, envelopeOperationId = null)
            }

            "resume_complete" ->
                if (isTerminalStatus(jsonString(obj, "status"))) {
                    terminal(state, GatewayEvent.RunEnded(state.operationId, terminal = true))
                }

            "session_complete" ->
                terminal(state, GatewayEvent.RunEnded(state.operationId, terminal = true))

            "heartbeat_ack" -> Unit
            else -> trySend(GatewayEvent.Unknown(text))
        }
    }

    private fun ProducerScope<GatewayEvent>.handleV2Text(
        socket: WebSocket,
        state: StreamState,
        lastEventId: String?,
        text: String,
    ) {
        val obj = parseObject(text)
            ?: run { trySend(GatewayEvent.Unknown(text)); return }
        when (jsonString(obj, "type")) {
            "ready" -> {
                val resume = if (lastEventId != null) {
                    ""","lastEventId":${jsonEscape(lastEventId)}"""
                } else {
                    ""
                }
                socket.send(
                    """{"type":"subscribe","operationId":${jsonEscape(state.operationId)}$resume}""",
                )
            }

            "agent_event" -> {
                val envelopeOp = jsonString(obj, "operationId")
                // 复用通道只承载本 op：信封不属于本订阅的一律丢弃。
                if (envelopeOp != null && envelopeOp != state.operationId) return
                val event = obj["event"] as? JsonObject ?: return
                dispatchAgentEvent(state, event, envelopeOp)
            }

            "session_complete" ->
                terminal(state, GatewayEvent.RunEnded(state.operationId, terminal = true))

            "status_change" ->
                if (isTerminalStatus(jsonString(obj, "status"))) {
                    terminal(state, GatewayEvent.RunEnded(state.operationId, terminal = true))
                }

            "resume_complete" -> {
                val pending = (obj["pending"] as? JsonPrimitive)?.booleanOrNull ?: false
                if (!pending && isTerminalStatus(jsonString(obj, "status"))) {
                    terminal(state, GatewayEvent.RunEnded(state.operationId, terminal = true))
                }
            }

            "subscribe_failed" -> terminal(
                state,
                GatewayEvent.RunError(
                    state.operationId,
                    jsonString(obj, "reason") ?: "subscribe failed",
                ),
            )

            "error" -> {
                val errOp = jsonString(obj, "operationId")
                if (errOp == null || errOp == state.operationId) {
                    val code = jsonString(obj, "code") ?: "error"
                    val message = jsonString(obj, "message") ?: "gateway error"
                    terminal(state, GatewayEvent.RunError(state.operationId, "$code: $message"))
                }
            }

            "heartbeat_ack", "op_lifecycle", "tool_confirmation_request", "input_request" -> Unit
            else -> trySend(GatewayEvent.Unknown(text))
        }
    }

    private fun ProducerScope<GatewayEvent>.dispatchAgentEvent(
        state: StreamState,
        event: JsonObject,
        envelopeOperationId: String?,
    ) {
        val mapped = try {
            mapAgentEvent(event, envelopeOperationId, state.operationId)
        } catch (_: Exception) {
            null
        } ?: return
        // 终结类事件发出后直接关流；chunk/start/end 继续收。
        if (mapped is GatewayEvent.RunEnded || mapped is GatewayEvent.RunError) {
            terminal(state, mapped)
        } else {
            trySend(mapped)
        }
    }

    /** 终结流：只生效一次，终结事件发出后关 socket + 结束 Flow。 */
    private fun ProducerScope<GatewayEvent>.terminal(state: StreamState, event: GatewayEvent) {
        if (!state.done.compareAndSet(false, true)) return
        trySend(event)
        state.heartbeat?.cancel()
        try {
            state.socket?.close(1000, null)
        } catch (_: Exception) {
        }
        close()
    }

    private fun parseObject(raw: String): JsonObject? = try {
        json.parseToJsonElement(raw) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private fun jsonEscape(value: String): String =
        JsonPrimitive(value).toString()

    private class StreamState(val operationId: String) {
        val clientId: String = "anlobehub-" + UUID.randomUUID().toString()
        val done = AtomicBoolean(false)
        var socket: WebSocket? = null
        var heartbeat: Job? = null
    }
}

/** http(s) 网关地址转 ws(s) 基址（去尾斜杠；无 scheme 默认 wss）。 */
internal fun gatewayWsBase(gatewayUrl: String): String {
    val trimmed = gatewayUrl.trim().trimEnd('/')
    return when {
        trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
        trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
        else -> "wss://$trimmed"
    }
}

internal fun buildV1Url(gatewayUrl: String, operationId: String): String =
    gatewayWsBase(gatewayUrl) + "/ws?operationId=" + urlEncode(operationId)

internal fun buildV2Url(gatewayUrl: String, token: String, clientId: String): String =
    gatewayWsBase(gatewayUrl) + "/v2/ws?token=" + urlEncode(token) +
        "&clientId=" + urlEncode(clientId)

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun jsonString(obj: JsonObject, key: String): String? =
    (obj[key] as? JsonPrimitive)?.contentOrNull

/** 网关会话终态（`completed` / `error` / `interrupted`）。 */
internal fun isTerminalStatus(status: String?): Boolean =
    status == "completed" || status == "error" || status == "interrupted"

/**
 * `agent_event` → [GatewayEvent] 纯映射（v1/v2 共用）。
 *
 * @param envelopeOperationId v2 信封上的 operationId（v1 传 null）。
 * @param defaultOperationId 本订阅的 operationId（字段缺失时回退，兼容老网关）。
 * @return null 表示已知无关事件（step/tool/patch/镜像成员终结等），直接忽略。
 */
internal fun mapAgentEvent(
    event: JsonObject,
    envelopeOperationId: String?,
    defaultOperationId: String,
): GatewayEvent? {
    val type = jsonString(event, "type") ?: return null
    // 镜像成员事件自带他方 id；缺字段回退信封/订阅 id（老网关兼容）。
    val opId = jsonString(event, "operationId") ?: envelopeOperationId ?: defaultOperationId
    val data = event["data"] as? JsonObject
    return when (type) {
        "stream_chunk" -> {
            val chunkType = jsonString(data ?: JsonObject(emptyMap()), "chunkType")
            if (chunkType != null && chunkType != "text" && chunkType != "reasoning" &&
                chunkType != "content_part" && chunkType != "reasoning_part"
            ) {
                // tool_state / tools_calling / image / grounding 等非文本增量：忽略。
                if (jsonString(data ?: JsonObject(emptyMap()), "content").isNullOrEmpty() &&
                    jsonString(data ?: JsonObject(emptyMap()), "reasoning").isNullOrEmpty()
                ) {
                    return null
                }
            }
            val textDelta = jsonString(data ?: JsonObject(emptyMap()), "content").orEmpty()
            val reasoningDelta = jsonString(data ?: JsonObject(emptyMap()), "reasoning")
            if (textDelta.isEmpty() && reasoningDelta.isNullOrEmpty()) return null
            GatewayEvent.StreamChunk(opId, textDelta, reasoningDelta)
        }

        "stream_start" -> {
            val assistantId = jsonString(
                data?.get("assistantMessage") as? JsonObject ?: JsonObject(emptyMap()),
                "id",
            )
            GatewayEvent.StreamStarted(opId, assistantId)
        }

        "stream_end" -> GatewayEvent.StreamEnded(opId)

        // 只有本 op 的终结才算终结；镜像成员（member_runtime_end 或他方 id）绝不断流。
        "agent_runtime_end" ->
            if (opId == defaultOperationId) {
                GatewayEvent.RunEnded(opId, terminal = true)
            } else {
                null
            }

        "member_runtime_end" -> null

        "error" -> GatewayEvent.RunError(opId, agentErrorMessage(data))
        else -> null
    }
}

internal fun agentErrorMessage(data: JsonObject?): String {
    if (data == null) return "unknown error"
    jsonString(data, "message")?.takeIf { it.isNotBlank() }?.let { return it }
    (data["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    jsonString(data, "errorType")?.takeIf { it.isNotBlank() }?.let { return it }
    return "unknown error"
}
