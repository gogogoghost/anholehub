package cc.jaxy.anlobehub.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.data.session.AuthRepository
import cc.jaxy.anlobehub.core.data.session.AuthState
import cc.jaxy.anlobehub.core.data.session.ServerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    sealed interface Destination {
        data object Loading : Destination
        data object Server : Destination
        data class Login(val baseUrl: String) : Destination
        data object Home : Destination
    }

    private val _destination = MutableStateFlow<Destination>(Destination.Loading)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = runCatching { serverStore.baseUrl.first() }.getOrNull()
            if (baseUrl.isNullOrBlank()) {
                _destination.value = Destination.Server
                return@launch
            }
            val state = when (val result = authRepository.refresh(baseUrl)) {
                is AnResult.Ok -> result.value
                is AnResult.Err -> AuthState.SignedOut
            }
            _destination.value = if (state is AuthState.SignedIn) {
                Destination.Home
            } else {
                Destination.Login(baseUrl)
            }
        }
    }
}
