package cc.jaxy.anlobehub.core.data.session

sealed interface AuthState {
    data object SignedOut : AuthState

    data class SignedIn(
        val userId: String,
        val email: String?,
        val name: String?,
        val avatar: String? = null,
    ) : AuthState
}
