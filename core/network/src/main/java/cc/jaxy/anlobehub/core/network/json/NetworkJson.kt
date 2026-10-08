package cc.jaxy.anlobehub.core.network.json

import kotlinx.serialization.json.Json

/**
 * 应用级 kotlinx.serialization [Json] 单例。
 *
 * 由 [cc.jaxy.anlobehub.core.network.di.NetworkModule] 以 Hilt 单例对外提供,
 * tRPC / Better Auth / Cookie 持久化共用同一份容错配置。
 */
object NetworkJson {

    val json: Json = Json {
        // 服务端字段只增不减, 未知字段直接忽略, 避免发版即崩。
        ignoreUnknownKeys = true
        // 序列化时省略 null, 反序列化缺 key 即用默认值。
        explicitNulls = false
        // 非法枚举 / null 填非空等情况回退默认值而非抛错。
        coerceInputValues = true
    }
}
