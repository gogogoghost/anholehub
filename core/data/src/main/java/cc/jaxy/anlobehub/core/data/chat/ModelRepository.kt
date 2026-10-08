package cc.jaxy.anlobehub.core.data.chat

import cc.jaxy.anlobehub.core.common.result.AnResult
import cc.jaxy.anlobehub.core.network.trpc.TrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface ModelRepository {
    suspend fun listModels(baseUrl: String): AnResult<List<AIModel>>
}

@Singleton
class ModelRepositoryImpl @Inject constructor(
    private val trpc: TrpcClient,
) : ModelRepository {

    override suspend fun listModels(baseUrl: String): AnResult<List<AIModel>> {
        val input = buildJsonObject { put("isLogin", true) }
        return trpc.query(baseUrl, "aiProvider.getAiProviderRuntimeState", input) { it.asAIModelList() }
    }
}
