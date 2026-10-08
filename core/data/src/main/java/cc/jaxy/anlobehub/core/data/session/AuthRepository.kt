package cc.jaxy.anlobehub.core.data.session

import cc.jaxy.anlobehub.core.common.result.AnResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun refresh(baseUrl: String): AnResult<AuthState>

    suspend fun signIn(baseUrl: String, email: String, password: String): AnResult<AuthState>

    suspend fun signOut(baseUrl: String): AnResult<Unit>
}
