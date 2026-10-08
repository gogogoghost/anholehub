package cc.jaxy.anlobehub.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.util.normalizeBaseUrl
import cc.jaxy.anlobehub.core.common.preferences.AppLanguage
import cc.jaxy.anlobehub.core.common.preferences.AppTheme
import cc.jaxy.anlobehub.core.data.preferences.UiPreferencesStore
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import cc.jaxy.anlobehub.core.network.reachability.Reachability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val reachability: Reachability,
    private val serverStore: ServerStore,
    private val uiPreferences: UiPreferencesStore,
) : ViewModel() {

    data class UiState(
        val link: String = "",
        val probing: Boolean = false,
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val theme: StateFlow<AppTheme> = uiPreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppTheme.SYSTEM)
    val language: StateFlow<AppLanguage> = uiPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    init {
        viewModelScope.launch {
            val stored = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (!stored.isNullOrBlank() && _uiState.value.link.isBlank()) {
                _uiState.value = _uiState.value.copy(link = stored)
            }
        }
    }

    fun onLinkChange(value: String) {
        _uiState.value = _uiState.value.copy(link = value, error = null)
    }

    fun confirm(raw: String, onConfirmed: (String) -> Unit) {
        if (_uiState.value.probing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(probing = true, error = null)

            val normalized =
                when (val normalizedResult = normalizeBaseUrl(raw)) {
                    is AnResult.Ok -> normalizedResult.value
                    is AnResult.Err -> {
                        _uiState.value =
                            _uiState.value.copy(
                                probing = false,
                                error = normalizedResult.error.toUiText(),
                            )
                        return@launch
                    }
                }

            when (val probeResult = reachability.probe(normalized)) {
                is AnResult.Ok -> Unit
                is AnResult.Err -> {
                    _uiState.value =
                        _uiState.value.copy(
                            probing = false,
                            error = probeResult.error.toUiText(),
                        )
                    return@launch
                }
            }

            val saved =
                runCatching { serverStore.setBaseUrl(normalized) }.exceptionOrNull()
            if (saved != null) {
                _uiState.value =
                    _uiState.value.copy(probing = false, error = UiText.Res(R.string.auth_server_save_failed))
                return@launch
            }

            _uiState.value = _uiState.value.copy(probing = false, error = null)
            onConfirmed(normalized)
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { uiPreferences.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { uiPreferences.setLanguage(language) }
    }
}
