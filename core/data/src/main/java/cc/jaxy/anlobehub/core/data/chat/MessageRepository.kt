package cc.jaxy.anlobehub.core.data.chat

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class SendMessageResult(
    val assistantMessageId: String? = null,
    val userMessageId: String? = null,
    val topicId: String? = null,
    val isCreateNewTopic: Boolean? = null,
)

interface MessageRepository {
    suspend fun listMessages(
        baseUrl: String,
        topicId: String? = null,
        sessionId: String? = null,
        agentId: String? = null,
    ): AnResult<List<ChatMessage>>

    suspend fun sendMessageInServer(
        baseUrl: String,
        content: String,
        topicId: String? = null,
        sessionId: String? = null,
        createNewTopic: Boolean? = null,
    ): AnResult<SendMessageResult>
}

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : MessageRepository {

    override suspend fun listMessages(
        baseUrl: String,
        topicId: String?,
        sessionId: String?,
        agentId: String?,
    ): AnResult<List<ChatMessage>> {
        val input = buildJsonObject {
            if (!topicId.isNullOrBlank()) put("topicId", topicId)
            if (!agentId.isNullOrBlank()) put("agentId", agentId)
            if (!sessionId.isNullOrBlank()) put("sessionId", sessionId)
        }.takeIf { it.isNotEmpty() }
        return trpc.query(baseUrl, "message.getMessages", input) { it.asMessageList() }
    }

    override suspend fun sendMessageInServer(
        baseUrl: String,
        content: String,
        topicId: String?,
        sessionId: String?,
        createNewTopic: Boolean?,
    ): AnResult<SendMessageResult> {
        if (content.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "empty message content"))
        }
        val input = buildJsonObject {
            put("newUserMessage", buildJsonObject { put("content", content) })
            if (!topicId.isNullOrBlank()) put("topicId", topicId)
            if (!sessionId.isNullOrBlank()) put("sessionId", sessionId)
            if (createNewTopic != null) put("newTopic", createNewTopic)
        }
        return trpc.mutate(baseUrl, "aiChat.sendMessageInServer", input) { el ->
            val obj = el as? JsonObject ?: return@mutate SendMessageResult()
            fun str(key: String): String? =
                (obj[key] as? JsonPrimitive)?.contentOrNull
            SendMessageResult(
                assistantMessageId = str("assistantMessageId"),
                userMessageId = str("userMessageId"),
                topicId = str("topicId"),
                isCreateNewTopic = (obj["isCreateNewTopic"] as? JsonPrimitive)
                    ?.contentOrNull?.toBooleanStrictOrNull(),
            )
        }
    }
}
