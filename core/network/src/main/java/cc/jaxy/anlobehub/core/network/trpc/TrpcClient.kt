package cc.jaxy.anlobehub.core.network.trpc

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.superjson.SuperJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 最小 tRPC (v11, superjson transformer) 客户端, 对接 `{base}/trpc/mobile`。
 *
 * - query 走 `GET <proc>?batch=1[&input={"0":{"json":...}}]`,
 *   input 为空时不拼接 input 参数 (tRPC 视为 undefined)。
 * - mutate 走 `POST <proc>?batch=1`, body 恒为 `{"0":{"json":...}}`;
 *   input 为空时 body 为 `{"0":{"json":null}}`, superjson 可干净还原为 null。
 * - 响应同时兼容 batch 数组 (取第 0 个) 与单对象两种形状。
 * - 错误映射: 网络异常 -> `NETWORK`; HTTP 非 2xx -> `HTTP_{code}`;
 *   body 内 TRPCError -> `TRPC_{code}`; 其余 -> `INVALID_INPUT` / `UNKNOWN`。
 *
 * 由 [cc.jaxy.anlobehub.core.network.di.NetworkModule] 以 Hilt 单例提供。
 */
class TrpcClient(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /**
     * @param baseUrl 归一化后的 base URL (无尾斜杠)。
     * @param path 单个 proc 路径, 如 `session.getGroupedSessions`。
     * @param input superjson 化之前的原始 input, 为空表示无参调用。
     * @param deserialize 把 superjson 还原后的 [JsonElement] 解成业务类型。
     */
    suspend fun <T> query(
        baseUrl: String,
        path: String,
        input: JsonElement? = null,
        deserialize: (JsonElement) -> T,
    ): AnResult<T> {
        val httpUrl = try {
            val builder = endpointUrl(baseUrl, path).newBuilder()
                .addQueryParameter("batch", "1")
            // input 为空时不带 input 参数。
            if (input != null) {
                builder.addQueryParameter(
                    "input",
                    json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject { put("0", SuperJson.encode(input)) },
                    ),
                )
            }
            builder.build()
        } catch (e: IllegalArgumentException) {
            return AnResult.Err(AnError("INVALID_INPUT", e.message ?: "invalid baseUrl", e))
        }
        return execute(Request.Builder().url(httpUrl).get().build(), deserialize)
    }

    /** 同 [query], 走 POST (mutation)。 */
    suspend fun <T> mutate(
        baseUrl: String,
        path: String,
        input: JsonElement? = null,
        deserialize: (JsonElement) -> T,
    ): AnResult<T> {
        val httpUrl = try {
            endpointUrl(baseUrl, path).newBuilder()
                .addQueryParameter("batch", "1")
                .build()
        } catch (e: IllegalArgumentException) {
            return AnResult.Err(AnError("INVALID_INPUT", e.message ?: "invalid baseUrl", e))
        }
        val payload = buildJsonObject { put("0", SuperJson.encode(input)) }
        val request = Request.Builder()
            .url(httpUrl)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
            .build()
        return execute(request, deserialize)
    }

    private suspend fun <T> execute(
        request: Request,
        deserialize: (JsonElement) -> T,
    ): AnResult<T> {
        try {
            return withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = try {
                        response.body.string()
                    } catch (e: IOException) {
                        return@use AnResult.Err(AnError("NETWORK", "failed to read response", e))
                    }
                    if (!response.isSuccessful) {
                        return@use AnResult.Err(
                            AnError(
                                "HTTP_${response.code}",
                                serverMessage(body) ?: "request failed: HTTP ${response.code}",
                            ),
                        )
                    }
                    parseBody(body, deserialize)
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

    private fun <T> parseBody(body: String, deserialize: (JsonElement) -> T): AnResult<T> {
        if (body.isBlank()) {
            return AnResult.Err(AnError("UNKNOWN", "empty response"))
        }
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return AnResult.Err(AnError("UNKNOWN", "invalid JSON response", e))
        }
        val first = when (root) {
            // batch=1 响应是单元素数组; 单 proc 直调是对象, 两种都吃。
            is JsonArray -> root.firstOrNull() as? JsonObject
                ?: return AnResult.Err(AnError("UNKNOWN", "empty batch response"))

            is JsonObject -> root
            else -> return AnResult.Err(AnError("UNKNOWN", "unexpected response shape"))
        }
        (first["result"] as? JsonObject)?.let { result ->
            val data = result["data"]
            val unwrapped = if (data is JsonObject) SuperJson.unwrap(data) else data ?: JsonNull
            return try {
                AnResult.Ok(deserialize(unwrapped))
            } catch (e: Exception) {
                AnResult.Err(AnError("UNKNOWN", "decode error: ${e.message}", e))
            }
        }
        (first["error"] as? JsonObject)?.let { error ->
            // tRPC 错误形如 { error: { json: { message, code, data? } } }。
            val inner = error["json"] as? JsonObject ?: error
            val code = (inner["code"] as? JsonPrimitive)?.contentOrNull ?: "UNKNOWN"
            val message = (inner["message"] as? JsonPrimitive)?.contentOrNull
                ?: "request failed"
            return AnResult.Err(AnError("TRPC_$code", message))
        }
        return AnResult.Err(AnError("UNKNOWN", "unexpected response shape"))
    }

    /** 尝试从服务端错误体里抠 `{ message }`, 抠不到返回 null。 */
    private fun serverMessage(body: String): String? = try {
        ((json.parseToJsonElement(body) as? JsonObject)?.get("message") as? JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun endpointUrl(baseUrl: String, path: String): HttpUrl =
        (baseUrl.trimEnd('/') + "/trpc/mobile/" + path.trim('/')).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("invalid baseUrl: $baseUrl")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
