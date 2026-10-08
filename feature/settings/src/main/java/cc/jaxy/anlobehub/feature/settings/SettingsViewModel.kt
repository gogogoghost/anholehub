package cc.jaxy.anlobehub.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.preferences.AppLanguage
import cc.jaxy.anlobehub.core.common.preferences.AppTheme
import cc.jaxy.anlobehub.core.data.preferences.UiPreferencesStore
import cc.jaxy.anlobehub.core.data.session.AuthRepository
import cc.jaxy.anlobehub.core.data.session.ServerStore
import cc.jaxy.anlobehub.core.data.user.UserProfile
import cc.jaxy.anlobehub.core.data.user.UserRepository
import cc.jaxy.anlobehub.core.designsystem.text.UiText
import cc.jaxy.anlobehub.core.designsystem.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val uiPreferencesStore: UiPreferencesStore,
) : ViewModel() {

    data class UiState(
        val baseUrl: String? = null,
        val profile: UserProfile? = null,
        val loading: Boolean = false,
        val error: UiText? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _signedOutEvent = MutableSharedFlow<Unit>()
    val signedOutEvent: SharedFlow<Unit> = _signedOutEvent.asSharedFlow()

    private val _switchServerEvent = MutableSharedFlow<Unit>()
    val switchServerEvent: SharedFlow<Unit> = _switchServerEvent.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    baseUrl = null,
                    profile = null,
                    loading = false,
                    error = UiText.Res(R.string.no_server),
                )
                return@launch
            }
            val profile = when (val result = userRepository.getUserState(baseUrl)) {
                is AnResult.Ok -> result.value
                is AnResult.Err -> {
                    _uiState.value = _uiState.value.copy(
                        baseUrl = baseUrl,
                        profile = null,
                        loading = false,
                        error = result.error.toUiText(),
                    )
                    return@launch
                }
            }
            _uiState.value = UiState(baseUrl = baseUrl, profile = profile)
        }
    }

    /**
     * 退出登录。保留 [ServerStore] 中的服务器地址便于重登，
     * 导航由父通过 [signedOutEvent] 接管。
     */
    fun signOut() {
        viewModelScope.launch {
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull().orEmpty()
            authRepository.signOut(baseUrl)
            _signedOutEvent.emit(Unit)
        }
    }
    /** 切换服务器：清空 [ServerStore] 后由父通过 [switchServerEvent] 导航。 */
    fun switchServer() {
        viewModelScope.launch {
            serverStore.clear()
            _switchServerEvent.emit(Unit)
        }
    }

    val language: StateFlow<AppLanguage> = uiPreferencesStore.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)

    val theme: StateFlow<AppTheme> = uiPreferencesStore.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { uiPreferencesStore.setLanguage(language) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { uiPreferencesStore.setTheme(theme) }
    }
}
