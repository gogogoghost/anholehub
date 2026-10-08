package cc.jaxy.anlobehub.core.network.auth

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Better Auth 会话 (字段全可空兜底, 未知字段靠 NetworkJson 忽略)。
 *
 * 服务端 `expiresAt` 为 ISO 字符串; 若缺字段则整体仍可解码,
 * 调用方以 `user == null && session == null` 判空会话。
 */
@Serializable
data class AuthSession(
    val user: AuthUser? = null,
    val session: SessionInfo? = null,
)

@Serializable
data class AuthUser(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
)

@Serializable
data class SessionInfo(
    val id: String? = null,
    val token: String? = null,
    val userId: String? = null,
    val expiresAt: String? = null,
)

/**
 * Better Auth 邮箱登录 API (Cookie 会话, 由 OkHttp CookieJar 自动携带)。
 *
 * 错误映射: 账号密码错 (401/403) -> `AUTH`; 其余 HTTP 非 2xx -> `HTTP_{code}`;
 * 网络异常 -> `NETWORK`。密码只放内存, 不落盘。
 */
@Singleton
class BetterAuthApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /** `POST /api/auth/sign-in/email`, 成功返回 user + session (cookie 已进 Jar)。 */
    suspend fun signInEmail(
        baseUrl: String,
        email: String,
        password: String,
    ): AnResult<AuthSession> {
        if (email.isBlank() || password.isBlank()) {
            return AnResult.Err(AnError("INVALID_INPUT", "email and password are required"))
        }
        val url = endpoint(baseUrl, "api/auth/sign-in/email")
            ?: return AnResult.Err(AnError("INVALID_INPUT", "invalid baseUrl: $baseUrl"))
        val payload = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            // 延长 cookie 有效期, 便于移动端持久登录。
            put("rememberMe", true)
        }
        return when (val result = call(post(url, payload))) {
            is AnResult.Ok -> try {
                AnResult.Ok(json.decodeFromString(AuthSession.serializer(), result.value))
            } catch (e: Exception) {
                AnResult.Err(AnError("UNKNOWN", "invalid sign-in response", e))
            }
            is AnResult.Err -> {
                if (result.error.code == "HTTP_401" || result.error.code == "HTTP_403") {
                    AnResult.Err(AnError("AUTH", "invalid email or password"))
                } else {
                    AnResult.Err(result.error)
                }
            }
        }
    }

    /**
     * `GET /api/auth/get-session` (带 Cookie)。
     * 401/403 或空会话 -> `Ok(null)`, 调用方映射为 SignedOut。
     */
    suspend fun getSession(baseUrl: String): AnResult<AuthSession?> {
        val url = endpoint(baseUrl, "api/auth/get-session")
            ?: return AnResult.Err(AnError("INVALID_INPUT", "invalid baseUrl: $baseUrl"))
        return when (val result = call(Request.Builder().url(url).get().build())) {
            is AnResult.Ok -> {
                val body = result.value
                if (body.isBlank() || body.trim() == "null") {
                    return AnResult.Ok(null)
                }
                try {
                    val session = json.decodeFromString(AuthSession.serializer(), body)
                    if (session.user == null && session.session == null) {
                        AnResult.Ok(null)
                    } else {
                        AnResult.Ok(session)
                    }
                } catch (e: Exception) {
                    AnResult.Err(AnError("UNKNOWN", "invalid session response", e))
                }
            }
            is AnResult.Err -> {
                if (result.error.code == "HTTP_401" || result.error.code == "HTTP_403") {
                    AnResult.Ok(null)
                } else {
                    AnResult.Err(result.error)
                }
            }
        }
    }

    /** `POST /api/auth/sign-out`, 成功返回 `Ok(Unit)`。 */
    suspend fun signOut(baseUrl: String): AnResult<Unit> {
        val url = endpoint(baseUrl, "api/auth/sign-out")
            ?: return AnResult.Err(AnError("INVALID_INPUT", "invalid baseUrl: $baseUrl"))
        return when (val result = call(post(url, buildJsonObject { }))) {
            is AnResult.Ok -> AnResult.Ok(Unit)
            is AnResult.Err -> AnResult.Err(result.error)
        }
    }

    /**
     * 2xx 返回 body 字符串; 非 2xx 返回 `HTTP_{code}` (尽量带上服务端 message)。
     * 401/403 不在这里转 AUTH, 由各调用方按语义决定 (getSession 要转 Ok(null))。
     */
    private suspend fun call(request: Request): AnResult<String> {
        try {
            return withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = try {
                        response.body.string()
                    } catch (e: IOException) {
                        ""
                    }
                    if (response.isSuccessful) {
                        AnResult.Ok(body)
                    } else {
                        AnResult.Err(
                            AnError(
                                "HTTP_${response.code}",
                                serverMessage(body) ?: "request failed: HTTP ${response.code}",
                            ),
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return AnResult.Err(AnError("NETWORK", e.message ?: "network error", e))
        } catch (e: Exception) {
            return AnResult.Err(AnError("UNKNOWN", e.message ?: "unknown error", e))
        }
    }

    private fun endpoint(baseUrl: String, path: String): HttpUrl? =
        (baseUrl.trimEnd('/') + "/" + path).toHttpUrlOrNull()

    private fun post(url: HttpUrl, payload: JsonObject): Request =
        Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
            .build()

    private fun serverMessage(body: String): String? = try {
        ((json.parseToJsonElement(body) as? JsonObject)?.get("message") as? JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
