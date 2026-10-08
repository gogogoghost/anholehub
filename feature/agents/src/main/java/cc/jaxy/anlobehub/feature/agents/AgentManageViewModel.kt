package cc.jaxy.anlobehub.feature.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.agent.AgentRepository
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@HiltViewModel
class AgentManageViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val agentRepository: AgentRepository,
) : ViewModel() {

    data class UiState(
        val agentId: String = "",
        val title: String = "",
        val systemRole: String = "",
        val model: String = "",
        val modelDisplayName: String = "",
        val provider: String = "",
        val isInbox: Boolean = false,
        val loading: Boolean = false,
        val saving: Boolean = false,
        val duplicating: Boolean = false,
        val deleting: Boolean = false,
        val saved: Boolean = false,
        val deleted: Boolean = false,
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null

    fun ensureLoaded(agentId: String) {
        if (loadedFor == agentId) return
        loadedFor = agentId
        _uiState.value = _uiState.value.copy(agentId = agentId)
        load()
    }

    fun load() {
        val id = _uiState.value.agentId.ifBlank { loadedFor.orEmpty() }
        if (id.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(agentId = id, loading = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = UiText.Res(R.string.agents_no_server),
                )
                return@launch
            }
            val agents = when (val r = agentRepository.listAgents(baseUrl)) {
                is AnResult.Ok -> r.value
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = r.error.toUiText(),
                    )
                    return@launch
                }
            }
            val summary = agents.find { it.id == id }
            when (val result = agentRepository.getConfig(baseUrl, id)) {
                is AnResult.Ok -> {
                    _uiState.value = _uiState.value.copy(
                        title = summary?.name?.takeIf { it.isNotBlank() }
                            ?: summary?.title?.takeIf { it.isNotBlank() }.orEmpty(),
                        systemRole = result.value.systemRole.orEmpty(),
                        model = result.value.model.orEmpty(),
                        provider = result.value.provider.orEmpty(),
                        isInbox = summary?.isInbox == true,
                        loading = false,
                        error = null,
                    )
                }
                is AnResult.Err ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = result.error.toUiText(),
                    )
            }
        }
    }

    fun onTitleChange(v: String) {
        _uiState.value = _uiState.value.copy(title = v, saved = false)
    }

    fun onSystemRoleChange(v: String) {
        _uiState.value = _uiState.value.copy(systemRole = v, saved = false)
    }

    fun setModel(model: AIModel?) {
        _uiState.value = _uiState.value.copy(
            model = model?.id.orEmpty(),
            modelDisplayName = model?.displayName?.takeIf { it.isNotBlank() }.orEmpty(),
            provider = model?.providerId.orEmpty(),
            saved = false,
        )
    }

    fun clearModel() {
        _uiState.value = _uiState.value.copy(model = "", modelDisplayName = "", provider = "", saved = false)
    }

    fun save() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, saved = false)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = UiText.Res(R.string.agents_no_server),
                )
                return@launch
            }
            val s = _uiState.value
            val fields = buildMap {
                put("title", JsonPrimitive(s.title.trim()))
                put("systemRole", JsonPrimitive(s.systemRole))
                if (s.model.isNotBlank()) put("model", JsonPrimitive(s.model.trim()))
                if (s.provider.isNotBlank()) put("provider", JsonPrimitive(s.provider.trim()))
            }
            when (val result = agentRepository.updateConfig(baseUrl, s.agentId, fields)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(saving = false, saved = true)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun duplicate() {
        if (_uiState.value.duplicating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(duplicating = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    duplicating = false,
                    error = UiText.Res(R.string.agents_no_server),
                )
                return@launch
            }
            when (val result = agentRepository.duplicate(baseUrl, _uiState.value.agentId)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(duplicating = false)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    duplicating = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun delete() {
        if (_uiState.value.isInbox) {
            _uiState.value = _uiState.value.copy(
                error = UiText.Res(R.string.agents_inbox_delete_error),
            )
            return
        }
        if (_uiState.value.deleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    error = UiText.Res(R.string.agents_no_server),
                )
                return@launch
            }
            when (val result = agentRepository.remove(baseUrl, _uiState.value.agentId)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(deleting = false, deleted = true)
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    deleting = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
