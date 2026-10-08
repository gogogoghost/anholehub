package cc.jaxy.anlobehub.feature.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.data.chat.ModelRepository
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

@HiltViewModel
class ModelsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverStore: ServerStore,
    private val modelRepository: ModelRepository,
) : ViewModel() {

    val selectedModelId: String? = savedStateHandle.get<String>("selectedModelId")?.takeIf { it.isNotBlank() }
    val selectedProviderId: String? = savedStateHandle.get<String>("selectedProviderId")?.takeIf { it.isNotBlank() }

    data class UiState(
        val models: List<AIModel> = emptyList(),
        val loading: Boolean = false,
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = UiText.Res(R.string.models_no_server),
                )
                return@launch
            }
            when (val result = modelRepository.listModels(baseUrl)) {
                is AnResult.Ok -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    models = result.value,
                    error = null,
                )
                is AnResult.Err -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = result.error.toUiText(),
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
