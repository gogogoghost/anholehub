package cc.jaxy.anlobehub.core.data.session

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.result.getOrNull
import cc.jaxy.anlobehub.core.common.util.normalizeBaseUrl
import cc.jaxy.anlobehub.core.network.auth.AuthSession
import cc.jaxy.anlobehub.core.network.auth.BetterAuthApi
import cc.jaxy.anlobehub.core.network.cookies.PersistentCookieStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: BetterAuthApi,
    private val cookieStore: PersistentCookieStore,
    private val serverStore: ServerStore,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    override suspend fun refresh(baseUrl: String): AnResult<AuthState> {
        val normalized = normalize(baseUrl) ?: return invalidBaseUrl()
        return when (val result = authApi.getSession(normalized)) {
            is AnResult.Ok -> {
                val state = result.value.toAuthState()
                _authState.value = state
                AnResult.Ok(state)
            }
            is AnResult.Err -> AnResult.Err(result.error)
        }
    }

    override suspend fun signIn(
        baseUrl: String,
        email: String,
        password: String,
    ): AnResult<AuthState> {
        if (email.isBlank() || password.isBlank()) {
            return AnResult.Err(AnError(code = "INVALID_INPUT", message = "Email and password must not be empty"))
        }
        val normalized = normalize(baseUrl) ?: return invalidBaseUrl()
        return when (val result = authApi.signInEmail(normalized, email.trim(), password)) {
            is AnResult.Ok -> {
                val state = result.value.toAuthState()
                _authState.value = state
                serverStore.setLastEmail(email.trim())
                AnResult.Ok(state)
            }
            is AnResult.Err -> AnResult.Err(result.error)
        }
    }

    override suspend fun signOut(baseUrl: String): AnResult<Unit> {
        // Best-effort: local state and cookies are always cleared so that
        // logout works even when the server call fails (e.g. offline).
        val normalized = normalize(baseUrl) ?: baseUrl
        try {
            authApi.signOut(normalized)
        } catch (_: Exception) {
            // Ignored: local cleanup below still runs.
        }
        try {
            cookieStore.clear(hostOf(normalized))
        } catch (_: Exception) {
            // Ignored: auth state reset below still runs.
        }
        _authState.value = AuthState.SignedOut
        return AnResult.Ok(Unit)
    }

    private fun AuthSession?.toAuthState(): AuthState {
        val userId = this?.user?.id
        return if (userId.isNullOrBlank()) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(userId = userId, email = user?.email, name = user?.name)
        }
    }

    private fun normalize(baseUrl: String): String? =
        normalizeBaseUrl(baseUrl).getOrNull()

    private fun <T> invalidBaseUrl(): AnResult<T> =
        AnResult.Err(AnError(code = "INVALID_INPUT", message = "Invalid base URL"))

    private fun hostOf(baseUrl: String): String =
        try {
            java.net.URI(baseUrl).host ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }
}
