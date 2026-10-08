package cc.jaxy.anlobehub.core.network.cookies

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.jaxy.anlobehub.core.network.json.NetworkJson
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

private val Context.cookieDataStore by preferencesDataStore(name = "network_cookies")

/** Cookie 的可序列化快照, DataStore 按 host 存 `Set<String>`。 */
@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val hostOnly: Boolean = false,
)

/**
 * 按 baseUrl host 隔离持久化的 [CookieJar] (Better Auth session cookie 载体)。
 *
 * - 内存 ConcurrentHashMap 做一级缓存, DataStore 做跨进程恢复。
 * - [CookieJar.loadForRequest] 是同步回调, 缓存未命中时用 `runBlocking`
 *   从 DataStore 读一次 (调用线程是 OkHttp 网络线程, 不会卡主线程)。
 * - 写入走内存 + 后台协程持久化, 读写失败均为 best-effort, 不抛错。
 *
 * 由 [cc.jaxy.anlobehub.core.network.di.NetworkModule] 以 Hilt 单例提供。
 */
class PersistentCookieStore(context: Context) : CookieJar {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memory = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host.lowercase()
        val now = System.currentTimeMillis()
        val byKey = cached(host).associateBy { Triple(it.name, it.domain, it.path) }.toMutableMap()
        for (cookie in cookies) {
            byKey[Triple(cookie.name, cookie.domain, cookie.path)] = cookie
        }
        val merged = byKey.values
            .filter { it.expiresAt > now }
            .takeLast(MAX_PER_HOST)
        memory[host] = merged
        ioScope.launch { persist(host, merged) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cached(url.host.lowercase()).filter { it.expiresAt > now && it.matches(url) }
    }

    /**
     * 清除该 host 的全部 cookie。
     * @param host 可传完整 baseUrl (`https://chat.example.com`) 或裸 host。
     */
    fun clear(host: String) {
        val normalized = host.trim().lowercase().let { raw ->
            if ("://" in raw) raw.toHttpUrlOrNull()?.host ?: raw else raw
        }
        memory.remove(normalized)
        ioScope.launch {
            try {
                appContext.cookieDataStore.edit { prefs -> prefs.remove(cookiesKey(normalized)) }
            } catch (_: Exception) {
                // best-effort: 退出登录必须可用, 持久化失败不阻塞。
            }
        }
    }

    private fun cached(host: String): List<Cookie> =
        memory[host] ?: loadPersisted(host).also { memory[host] = it }

    private fun loadPersisted(host: String): List<Cookie> {
        return try {
            runBlocking {
                val raw = appContext.cookieDataStore.data.first()[cookiesKey(host)].orEmpty()
                if (raw.isEmpty()) return@runBlocking emptyList()
                raw.mapNotNull { line ->
                    try {
                        toCookie(NetworkJson.json.decodeFromString(StoredCookie.serializer(), line))
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun persist(host: String, cookies: List<Cookie>) {
        try {
            val lines = cookies.map {
                NetworkJson.json.encodeToString(
                    StoredCookie.serializer(),
                    StoredCookie(
                        name = it.name,
                        value = it.value,
                        expiresAt = it.expiresAt,
                        domain = it.domain,
                        path = it.path,
                        secure = it.secure,
                        httpOnly = it.httpOnly,
                        hostOnly = it.hostOnly,
                    ),
                )
            }.toSet()
            appContext.cookieDataStore.edit { prefs -> prefs[cookiesKey(host)] = lines }
        } catch (_: Exception) {
            // best-effort。
        }
    }

    private fun toCookie(snapshot: StoredCookie): Cookie? = try {
        if (snapshot.name.isEmpty() || snapshot.domain.isEmpty()) {
            null
        } else {
            Cookie.Builder()
                .name(snapshot.name)
                .value(snapshot.value)
                .expiresAt(snapshot.expiresAt)
                .path(snapshot.path.ifEmpty { "/" })
                .apply { if (snapshot.hostOnly) hostOnlyDomain(snapshot.domain) else domain(snapshot.domain) }
                .apply { if (snapshot.secure) secure() }
                .apply { if (snapshot.httpOnly) httpOnly() }
                .build()
        }
    } catch (_: Exception) {
        null
    }

    private fun cookiesKey(host: String) = stringSetPreferencesKey(KEY_PREFIX + host)

    companion object {
        private const val KEY_PREFIX = "cookies|"
        private const val MAX_PER_HOST = 64
    }
}
