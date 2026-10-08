package cc.jaxy.anlobehub.core.network.reachability

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BaseUrl 可达性探测: HEAD 根路径。
 *
 * 2xx/3xx/4xx 均视为可达 (4xx 只是根路径无对应资源, 不是连不上);
 * 只有连接异常 / 超时 / 5xx 才判 `AnError("NETWORK")`。
 */
@Singleton
class Reachability @Inject constructor(
    private val client: OkHttpClient,
) {

    // 探测用更短的超时, 避免登录页转圈过久。
    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun probe(baseUrl: String): AnResult<Unit> {
        val url = baseUrl.toHttpUrlOrNull()
            ?: return AnResult.Err(AnError("INVALID_INPUT", "invalid baseUrl: $baseUrl"))
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).head().build()
                probeClient.newCall(request).execute().use { response ->
                    if (response.code in 200..499) {
                        AnResult.Ok(Unit)
                    } else {
                        AnResult.Err(AnError("NETWORK", "server error: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AnResult.Err(AnError("NETWORK", e.message ?: "server unreachable", e))
        } catch (e: Exception) {
            AnResult.Err(AnError("NETWORK", e.message ?: "server unreachable", e))
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val CALL_TIMEOUT_SECONDS = 15L
    }
}
