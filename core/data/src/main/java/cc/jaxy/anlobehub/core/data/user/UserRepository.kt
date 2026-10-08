package cc.jaxy.anlobehub.core.data.user

import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class UserProfile(
    val userId: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val username: String? = null,
)

interface UserRepository {
    suspend fun getUserState(baseUrl: String): AnResult<UserProfile>
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : UserRepository {

    override suspend fun getUserState(baseUrl: String): AnResult<UserProfile> =
        trpc.query(baseUrl, "user.getUserState", null) { it.toUserProfile() }
}

/** 全宽容解析：缺字段即 null，未知字段忽略。 */
fun JsonElement.toUserProfile(): UserProfile {
    val obj = this as? JsonObject ?: return UserProfile()
    return UserProfile(
        userId = obj.stringOrNull("userId") ?: obj.stringOrNull("id"),
        avatar = obj.avatarOrNull(),
        email = obj.stringOrNull("email"),
        fullName = obj.stringOrNull("fullName") ?: obj.stringOrNull("name"),
        username = obj.stringOrNull("username"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** avatar 可能是直链字符串，也可能是对象（取其 url 类字符字段，取不到即 null）。 */
private fun JsonObject.avatarOrNull(): String? {
    return when (val raw = this["avatar"]) {
        null -> null
        is JsonPrimitive -> raw.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            listOf("url", "avatar", "src", "value").firstNotNullOfOrNull { raw.stringOrNull(it) }
                ?: raw.values.firstNotNullOfOrNull { v ->
                    (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
        }
        else -> null
    }
}
