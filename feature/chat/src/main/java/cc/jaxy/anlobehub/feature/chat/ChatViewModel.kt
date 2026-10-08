package cc.jaxy.anlobehub.feature.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.data.chat.ChatMessage
import cc.jaxy.anlobehub.core.data.chat.ChatTopic
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import cc.jaxy.anlobehub.core.data.chat.MessageRepository
import cc.jaxy.anlobehub.core.data.chat.ModelRepository
import cc.jaxy.anlobehub.core.data.chat.TopicRepository
import cc.jaxy.anlobehub.core.data.files.FileRepository
import cc.jaxy.anlobehub.core.data.files.UploadedFile
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.network.gateway.AgentApi
import cc.jaxy.anlobehub.core.network.gateway.GatewayEvent
import cc.jaxy.anlobehub.core.network.gateway.GatewaySocket
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverStore: ServerStore,
    private val messageRepository: MessageRepository,
    private val topicRepository: TopicRepository,
    private val modelRepository: ModelRepository,
    private val fileRepository: FileRepository,
    private val agentApi: AgentApi,
    private val gatewaySocket: GatewaySocket,
) : ViewModel() {

    // Agent-first identity. "agentId" is the source of truth; "topicId" is the
    // optional initial topic (deep link). "agentTitle" is the display name passed
    // by the parent route (no extra fetch for the TopBar title).
    val agentId: String = savedStateHandle.get<String>("agentId") ?: ""
    val agentTitle: String? = savedStateHandle.get<String>("agentTitle")?.takeIf { it.isNotBlank() }
    private val initialTopicId: String? = savedStateHandle.get<String>("topicId")?.takeIf { it.isNotBlank() }
    // Legacy route compat during cutover; never sent to any backend. Parent removes it.
    @Suppress("unused")
    val sessionId: String? = savedStateHandle.get<String>("sessionId")
    val isInboxAgent: Boolean get() = agentId.isBlank() || agentId.equals("inbox", ignoreCase = true)

    data class UiState(
        val messages: List<ChatMessage> = emptyList(),
        val streaming: Boolean = false,
        val streamingText: String = "",
        val error: UiText? = null,
        val topics: List<ChatTopic> = emptyList(),
        val activeTopicId: String? = null,
        val models: List<AIModel> = emptyList(),
        val activeModel: AIModel? = null,
        val pendingFiles: List<UploadedFile> = emptyList(),
        val uploading: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var activeBaseUrl: String? = null
    private var activeOperationId: String? = null
    private var pendingAssistantId: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val baseUrl = currentBaseUrl()
            if (baseUrl == null) {
                _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.chat_no_server))
                return@launch
            }
            // Fresh topic by default: history loads lazily via loadTopics().
            // A deep-linked topicId still needs the list to validate against.
            val needTopics = initialTopicId != null || _uiState.value.activeTopicId != null
            val topics = if (needTopics) {
                when (val result = topicRepository.listTopics(baseUrl, agentId = effectiveAgentId())) {
                    is AnResult.Ok -> result.value
                    is AnResult.Err -> emptyList()
                }
            } else {
                emptyList()
            }
            val keptTopic = _uiState.value.activeTopicId
                ?.takeIf { id -> topics.any { it.id == id } }
            val deepLinkTopic = initialTopicId?.takeIf { id -> topics.any { it.id == id } }
            val activeTopicId = keptTopic ?: deepLinkTopic
            val models = when (val result = modelRepository.listModels(baseUrl)) {
                is AnResult.Ok -> result.value
                is AnResult.Err -> emptyList()
            }
            val keptModel = _uiState.value.activeModel
                ?.takeIf { current -> models.any { it.id == current.id } }
            val activeModel = keptModel
                ?: models.firstOrNull { it.enabled != false }
                ?: models.firstOrNull()
            _uiState.value = _uiState.value.copy(
                topics = topics,
                activeTopicId = activeTopicId,
                models = models,
                activeModel = activeModel,
            )
            loadMessages()
        }
    }

    /** Lazily loads topic history (called when the history panel opens). */
    fun loadTopics() {
        viewModelScope.launch {
            val baseUrl = currentBaseUrl() ?: return@launch
            when (val result = topicRepository.listTopics(baseUrl, agentId = effectiveAgentId())) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(topics = result.value)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(error = result.error.toUiText())
            }
        }
    }

    /** Starts a fresh conversation (clears active topic + messages). */
    fun newConversation() {
        if (_uiState.value.streaming) stop()
        _uiState.value = _uiState.value.copy(activeTopicId = null, messages = emptyList(), error = null)
    }

    fun selectTopic(id: String?) {
        if (_uiState.value.activeTopicId == id) return
        if (_uiState.value.streaming) stop()
        _uiState.value = _uiState.value.copy(activeTopicId = id)
        viewModelScope.launch { loadMessages() }
    }

    fun selectModel(model: AIModel) {
        _uiState.value = _uiState.value.copy(activeModel = model)
    }

    fun send(text: String, fileIds: List<String> = emptyList()) {
        if (text.isBlank()) return
        if (_uiState.value.streaming) return
        streamJob?.cancel()
        streamJob = viewModelScope.launch { runSend(text, fileIds) }
    }

    fun attach(bytes: ByteArray, name: String, mime: String) {
        viewModelScope.launch {
            val baseUrl = currentBaseUrl()
            if (baseUrl == null) {
                _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.chat_no_server))
                return@launch
            }
            _uiState.value = _uiState.value.copy(uploading = true, error = null)
            when (val result = fileRepository.uploadFile(baseUrl, name, mime, bytes)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(
                    pendingFiles = _uiState.value.pendingFiles + result.value,
                    uploading = false,
                )
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    uploading = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun removePendingFile(file: UploadedFile) {
        _uiState.value = _uiState.value.copy(pendingFiles = _uiState.value.pendingFiles - file)
    }

    fun stop() {
        val snapshot = _uiState.value
        if (!snapshot.streaming) return
        streamJob?.cancel()
        streamJob = null
        val baseUrl = activeBaseUrl
        val operationId = activeOperationId
        if (!baseUrl.isNullOrBlank() && !operationId.isNullOrBlank()) {
            viewModelScope.launch { runCatching { agentApi.interruptTask(baseUrl, operationId) } }
        }
        val acc = snapshot.streamingText
        _uiState.value =
            if (acc.isNotBlank()) {
                snapshot.copy(
                    messages = snapshot.messages + ChatMessage(
                        id = pendingAssistantId ?: "asst-$operationId",
                        role = "assistant",
                        content = acc,
                        createdAt = System.currentTimeMillis(),
                    ),
                    streaming = false,
                    streamingText = "",
                )
            } else {
                snapshot.copy(streaming = false, streamingText = "")
            }
        activeOperationId = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun loadMessages() {
        // Fresh topic has no messages; don't query the server.
        if (_uiState.value.activeTopicId == null) {
            _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
            return
        }
        val baseUrl = currentBaseUrl()
        if (baseUrl == null) {
            _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.chat_no_server))
            return
        }
        when (
            val result = messageRepository.listMessages(
                baseUrl,
                topicId = _uiState.value.activeTopicId,
                agentId = effectiveAgentId(),
            )
        ) {
            is AnResult.Ok -> _uiState.value = _uiState.value.copy(messages = result.value, error = null)
            is AnResult.Err -> _uiState.value = _uiState.value.copy(error = result.error.toUiText())
        }
    }

    private suspend fun runSend(text: String, fileIds: List<String>) {
        val baseUrl = currentBaseUrl()
        if (baseUrl == null) {
            _uiState.value = _uiState.value.copy(streaming = false, error = UiText.Res(R.string.chat_no_server))
            return
        }
        if (fileIds.isNotEmpty()) {
            // Gateway attachment shape TBD: log fileIds only, don't block sending.
            Log.d("ChatViewModel", "send with ${fileIds.size} file(s); gateway attachment shape TBD")
        }
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = "local-$now",
            role = "user",
            content = text,
            createdAt = now,
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            streaming = true,
            streamingText = "",
            error = null,
            pendingFiles = emptyList(),
        )
        activeOperationId = null
        pendingAssistantId = null

        val snapshot = _uiState.value
        val exec = agentApi.execAgent(
            baseUrl,
            prompt = text,
            topicId = snapshot.activeTopicId,
            agentId = execAgentIdOrNull(),
            slug = execSlugOrNull(),
            model = snapshot.activeModel?.id?.takeIf { it.isNotBlank() },
            provider = snapshot.activeModel?.providerId?.takeIf { it.isNotBlank() },
        )
        if (exec is AnResult.Err) {
            _uiState.value = _uiState.value.copy(streaming = false, streamingText = "", error = exec.error.toUiText())
            return
        }
        val execOk = (exec as AnResult.Ok).value
        val operationId = execOk.operationId?.takeIf { it.isNotBlank() }
        val token = execOk.token?.takeIf { it.isNotBlank() }
        if (operationId == null || token == null) {
            _uiState.value = _uiState.value.copy(streaming = false, streamingText = "", error = UiText.Res(R.string.chat_no_stream_task))
            return
        }
        activeOperationId = operationId
        if (!execOk.assistantMessageId.isNullOrBlank()) {
            pendingAssistantId = execOk.assistantMessageId
        }

        val endpoint =
            when (val cfg = agentApi.globalConfig(baseUrl)) {
                is AnResult.Ok -> cfg.value
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(streaming = false, streamingText = "", error = UiText.Res(R.string.chat_gateway_missing))
                    return
                }
            }

        val buffer = StringBuilder()
        try {
            gatewaySocket.stream(endpoint, token, operationId).collect { event ->
                when (event) {
                    is GatewayEvent.StreamChunk -> {
                        buffer.append(event.textDelta)
                        _uiState.value = _uiState.value.copy(streamingText = buffer.toString())
                    }
                    is GatewayEvent.StreamStarted -> {
                        if (!event.assistantMessageId.isNullOrBlank()) {
                            pendingAssistantId = event.assistantMessageId
                        }
                    }
                    is GatewayEvent.StreamEnded -> Unit
                    is GatewayEvent.RunEnded -> {
                        val assistant = ChatMessage(
                            id = pendingAssistantId ?: "asst-$operationId",
                            role = "assistant",
                            content = buffer.toString(),
                            createdAt = System.currentTimeMillis(),
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + assistant,
                            streaming = false,
                            streamingText = "",
                        )
                        activeOperationId = null
                    }
                    is GatewayEvent.RunError -> {
                        val msg =
                            if ("AUTH_EXPIRED" in event.message) {
                                UiText.Res(R.string.chat_session_expired_reenter)
                            } else {
                                UiText.rawOrNull(event.message)
                                    ?: UiText.Res(R.string.chat_stream_interrupted)
                            }
                        _uiState.value = _uiState.value.copy(streaming = false, streamingText = "", error = msg)
                        activeOperationId = null
                    }
                    is GatewayEvent.Unknown -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _uiState.value = _uiState.value.copy(
                streaming = false,
                streamingText = "",
                error = UiText.rawOrNull(t.message)
                    ?: UiText.Res(R.string.chat_stream_interrupted),
            )
            activeOperationId = null
        }
    }

    // Contract §2: execAgent takes agentId when id-shaped, else slug.
    // Inbox chat passes slug "inbox".
    private fun execAgentIdOrNull(): String? =
        agentId.takeIf { it.isNotBlank() && !it.equals("inbox", ignoreCase = true) && looksIdShaped(it) }
    private fun execSlugOrNull(): String? =
        when {
            isInboxAgent -> "inbox"
            agentId.isNotBlank() && !looksIdShaped(agentId) -> agentId
            else -> null
        }
    private fun effectiveAgentId(): String? =
        agentId.takeIf { it.isNotBlank() } ?: "inbox"
    private fun looksIdShaped(value: String): Boolean {
        if (value.contains("/") || value.contains(" ")) return false
        if (value.length >= 16) return true
        val hexLike = value.count { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } >= value.length / 2
        return value.contains("-") || value.contains("_") || hexLike
    }
    private suspend fun currentBaseUrl(): String? {
        val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (baseUrl != null) {
            activeBaseUrl = baseUrl
        }
        return baseUrl
    }
}

