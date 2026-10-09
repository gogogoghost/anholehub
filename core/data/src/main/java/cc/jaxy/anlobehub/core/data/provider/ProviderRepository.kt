package cc.jaxy.anlobehub.core.data.provider

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.common.util.buildModelsUrl
import cc.jaxy.anlobehub.core.common.util.parseModelIds
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * AI 供应商条目（`aiProvider.getAiProviderList` 返回）。
 *
 * 全字段可空：`enabled` 服务端恒返 bool，缺省按 true 处理。
 */
data class AiProvider(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val source: String? = null,
)

/**
 * 供应商详情（`aiProvider.getAiProviderById` 返回）。
 *
 * Tolerant 解析：缺字段给缺省；`keyVaults` 整体透出为字符串 map（web
 * `AiProviderKeyVaultsSchema` 为 `Record<string, string|map>`，各供应商键名
 * 不同：`apiKey`/`baseURL`/`region`/`accessKeyId`/…），非字符串值丢弃；
 * 已配置时服务端可能返回掩码值，原样透出、不做脱敏判断。
 */
data class ProviderDetail(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val source: String? = null,
    val checkModel: String? = null,
    val keyVaults: Map<String, String> = emptyMap(),
    val fetchOnClient: Boolean? = null,
    val enableResponseApi: Boolean? = null,
) {
    /** Back-compat for the two generic vault keys. */
    val apiKey: String? get() = keyVaults["apiKey"]
    val baseURL: String? get() = keyVaults["baseURL"]
}

data class CheckResult(
    val ok: Boolean = false,
    val error: String? = null,
)

interface ProviderRepository {
    suspend fun listProviders(baseUrl: String): AnResult<List<AiProvider>>

    suspend fun toggleProvider(
        baseUrl: String,
        id: String,
        enabled: Boolean,
    ): AnResult<Unit>

    suspend fun getDetail(baseUrl: String, id: String): AnResult<ProviderDetail>

    suspend fun updateConfig(
        baseUrl: String,
        id: String,
        keyVaults: Map<String, String?> = emptyMap(),
        checkModel: String? = null,
        fetchOnClient: Boolean? = null,
        enableResponseApi: Boolean? = null,
    ): AnResult<Unit>

    suspend fun checkConnectivity(
        baseUrl: String,
        id: String,
        model: String? = null,
    ): AnResult<CheckResult>

    suspend fun fetchRemoteModels(
        baseURL: String,
        apiKey: String,
    ): AnResult<List<String>>
}

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : ProviderRepository {

    override suspend fun listProviders(baseUrl: String): AnResult<List<AiProvider>> {
        // 无 input：传空 object。
        return trpc.query(baseUrl, "aiProvider.getAiProviderList", buildJsonObject { }) {
            it.asAiProviderList()
        }
    }

    override suspend fun toggleProvider(
        baseUrl: String,
        id: String,
        enabled: Boolean,
    ): AnResult<Unit> {
        val input = buildJsonObject {
            put("id", id)
            put("enabled", enabled)
        }
        // 官方供应商禁关：服务端抛 BAD_REQUEST，错误经 AnResult.Err 透给 UI。
        return trpc.mutate(baseUrl, "aiProvider.toggleProviderEnabled", input) { }
    }

    override suspend fun getDetail(baseUrl: String, id: String): AnResult<ProviderDetail> {
        val input = buildJsonObject { put("id", id) }
        return trpc.query(baseUrl, "aiProvider.getAiProviderById", input) {
            it.toProviderDetail()
        }
    }

    override suspend fun updateConfig(
        baseUrl: String,
        id: String,
        keyVaults: Map<String, String?>,
        checkModel: String?,
        fetchOnClient: Boolean?,
        enableResponseApi: Boolean?,
    ): AnResult<Unit> {
        val vaultsJson = buildJsonObject {
            keyVaults.forEach { (k, v) ->
                if (v != null) put(k, v)
            }
        }
        val value = buildJsonObject {
            if (vaultsJson.isNotEmpty()) put("keyVaults", vaultsJson)
            if (checkModel != null) put("checkModel", checkModel)
            if (fetchOnClient != null) put("fetchOnClient", fetchOnClient)
            if (enableResponseApi != null) {
                put("config", buildJsonObject { put("enableResponseApi", enableResponseApi) })
            }
        }
        val input = buildJsonObject {
            put("id", id)
            put("value", value)
        }
        return trpc.mutate(baseUrl, "aiProvider.updateAiProviderConfig", input) { }
    }

    override suspend fun checkConnectivity(
        baseUrl: String,
        id: String,
        model: String?,
    ): AnResult<CheckResult> {
        val input = buildJsonObject {
            put("id", id)
            if (model != null) put("model", model)
        }
        return trpc.mutate(baseUrl, "aiProvider.checkProviderConnectivity", input) {
            it.toCheckResult()
        }
    }

    override suspend fun fetchRemoteModels(
        baseURL: String,
        apiKey: String,
    ): AnResult<List<String>> {
        val url = try {
            buildModelsUrl(baseURL)
        } catch (e: IllegalArgumentException) {
            return AnResult.Err(AnError("INVALID_INPUT", e.message ?: "invalid baseURL", e))
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        val client = httpClient.newBuilder()
            .connectTimeout(REMOTE_MODELS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REMOTE_MODELS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REMOTE_MODELS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        try {
            return withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = try {
                        response.body.string()
                    } catch (e: IOException) {
                        return@use AnResult.Err(AnError("NETWORK", "failed to read response", e))
                    }
                    if (!response.isSuccessful) {
                        val code = response.code
                        return@use if (code == 401 || code == 403) {
                            AnResult.Err(AnError("AUTH", "invalid credentials"))
                        } else {
                            AnResult.Err(AnError("HTTP_$code", "request failed: HTTP $code"))
                        }
                    }
                    val element = try {
                        json.parseToJsonElement(body)
                    } catch (e: Exception) {
                        return@use AnResult.Err(AnError("UNKNOWN", "invalid JSON response", e))
                    }
                    AnResult.Ok(parseModelIds(element))
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

    companion object {
        private const val REMOTE_MODELS_TIMEOUT_SECONDS = 15L
    }
}

fun JsonElement.asAiProviderList(): List<AiProvider> {
    return try {
        val items: List<JsonElement> = when (this) {
            is JsonArray -> this
            is JsonNull -> return emptyList()
            is JsonObject -> {
                (this["items"] as? JsonArray)
                    ?: (this["data"] as? JsonArray)
                    ?: (this["providers"] as? JsonArray)
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        items.mapNotNull { runCatching { it.toAiProvider() }.getOrNull() }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun JsonElement.toAiProvider(): AiProvider {
    val obj = this as? JsonObject ?: return AiProvider()
    fun str(key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return AiProvider(
        id = str("id") ?: "",
        name = str("name"),
        description = str("description"),
        enabled = (obj["enabled"] as? JsonPrimitive)?.contentOrNull
            ?.toBooleanStrictOrNull() ?: true,
        source = str("source"),
    )
}

private fun JsonElement.toProviderDetail(): ProviderDetail {
    val obj = this as? JsonObject ?: return ProviderDetail()
    fun str(key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    val vaults = (obj["keyVaults"] as? JsonObject)
        ?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { k to it }
        }?.toMap().orEmpty()
    val config = obj["config"] as? JsonObject
    return ProviderDetail(
        id = str("id") ?: "",
        name = str("name"),
        description = str("description"),
        enabled = (obj["enabled"] as? JsonPrimitive)?.contentOrNull
            ?.toBooleanStrictOrNull() ?: true,
        source = str("source"),
        checkModel = str("checkModel"),
        keyVaults = vaults,
        fetchOnClient = (obj["fetchOnClient"] as? JsonPrimitive)?.contentOrNull
            ?.toBooleanStrictOrNull(),
        enableResponseApi = (config?.get("enableResponseApi") as? JsonPrimitive)
            ?.contentOrNull?.toBooleanStrictOrNull(),
    )
}

private fun JsonElement.toCheckResult(): CheckResult {
    val obj = this as? JsonObject ?: return CheckResult(ok = false)
    val ok = (obj["ok"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
    val error = (obj["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return CheckResult(ok = ok, error = error)
}
