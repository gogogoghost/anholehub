package cc.jaxy.anlobehub.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.util.normalizeBaseUrl
import cc.jaxy.anlobehub.core.data.session.AuthRepository
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
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val serverStore: ServerStore,
) : ViewModel() {

    data class UiState(
        val email: String = "",
        val password: String = "",
        val loading: Boolean = false,
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var navBaseUrl: String? = savedStateHandle.get<String>("baseUrl")

    init {
        viewModelScope.launch {
            val lastEmail = runCatching { serverStore.lastEmail.first() }.getOrNull()
            if (!lastEmail.isNullOrBlank() && _uiState.value.email.isBlank()) {
                _uiState.value = _uiState.value.copy(email = lastEmail)
            }
            if (navBaseUrl.isNullOrBlank()) {
                navBaseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            }
        }
    }

    /** NavHost 已把归一化后的 baseUrl 作为参数传入；ViewModel 侧做兜底装配。 */
    fun attachBaseUrl(baseUrl: String) {
        if (navBaseUrl.isNullOrBlank() && baseUrl.isNotBlank()) {
            navBaseUrl = baseUrl
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun login(onLoggedIn: () -> Unit) {
        if (_uiState.value.loading) return
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = UiText.Res(R.string.auth_login_empty_credentials))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)

            val rawBase = navBaseUrl?.takeIf { it.isNotBlank() }
                ?: runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (rawBase.isNullOrBlank()) {
                _uiState.value =
                    _uiState.value.copy(loading = false, error = UiText.Res(R.string.auth_login_missing_server))
                return@launch
            }
            val normalized =
                when (val normalizedResult = normalizeBaseUrl(rawBase)) {
                    is AnResult.Ok -> normalizedResult.value
                    is AnResult.Err -> {
                        _uiState.value =
                            _uiState.value.copy(
                                loading = false,
                                error = normalizedResult.error.toUiText(),
                            )
                        return@launch
                    }
                }

            when (val result = authRepository.signIn(normalized, email, password)) {
                is AnResult.Ok -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = null)
                    onLoggedIn()
                }
                is AnResult.Err -> {
                    _uiState.value =
                        _uiState.value.copy(
                            loading = false,
                            error = result.error.toUiText(),
                        )
                }
            }
        }
    }
}
