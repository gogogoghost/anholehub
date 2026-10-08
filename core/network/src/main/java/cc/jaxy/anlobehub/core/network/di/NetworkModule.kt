package cc.jaxy.anlobehub.core.network.di

import android.content.Context
import android.util.Log
import cc.jaxy.anlobehub.core.network.cookies.PersistentCookieStore
import cc.jaxy.anlobehub.core.network.json.NetworkJson
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层 Hilt 装配:
 * - [NetworkJson]: 应用级 Json 单例。
 * - [PersistentCookieStore]: 应用级 cookie 持久化 (按 host 隔离)。
 * - [OkHttpClient]: 应用级单例 (cookieJar + 超时 + 日志)。
 * - [TrpcClient]: 应用级单例。
 *
 * BetterAuthApi / Reachability 走 `@Inject` 构造, 无需在此声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = NetworkJson.json

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): PersistentCookieStore =
        PersistentCookieStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: PersistentCookieStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // network 模块无 BuildConfig, 用 log 开关代替 DEBUG 判断。
            level = if (Log.isLoggable(TAG, Log.DEBUG)) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideTrpcClient(client: OkHttpClient, json: Json): TrpcClient =
        TrpcClient(client, json)

    private const val TAG = "anlobehub"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 60L
}
