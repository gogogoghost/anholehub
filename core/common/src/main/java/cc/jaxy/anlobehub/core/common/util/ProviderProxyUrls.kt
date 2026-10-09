package cc.jaxy.anlobehub.core.common.util

/**
 * Provider endpoint defaults from web (`model-bank provider cards`
 * `settings.proxyUrl.placeholder`). Shown as the endpoint hint; providers
 * listed here (or custom ones) get an editable endpoint field.
 */
private val PROXY_URLS = mapOf(
    "ai302" to "https://api.302.ai/v1",
    "aihubmix" to "https://aihubmix.com",
    "anthropic" to "https://api.anthropic.com",
    "baichuan" to "https://api.baichuan-ai.com/v1",
    "cerebras" to "https://api.cerebras.ai/v1",
    "cohere" to "https://api.cohere.ai/compatibility/v1",
    "deepseek" to "https://api.deepseek.com",
    "fireworksai" to "https://api.fireworks.ai/inference/v1",
    "giteeai" to "https://ai.gitee.com/v1",
    "google" to "https://generativelanguage.googleapis.com",
    "groq" to "https://api.groq.com/openai/v1",
    "hunyuan" to "https://tokenhub.tencentmaas.com/v1",
    "internlm" to "https://chat.intern-ai.org.cn/api/v1",
    "lmstudio" to "http://127.0.0.1:1234/v1",
    "minimax" to "https://api.minimaxi.com/v1",
    "mistral" to "https://api.mistral.ai",
    "modelscope" to "https://api-inference.modelscope.cn/v1",
    "moonshot" to "https://api.moonshot.cn/v1",
    "nebius" to "https://api.studio.nebius.com/v1",
    "novita" to "https://api.novita.ai/v3/openai",
    "openrouter" to "https://openrouter.ai/api/v1",
    "perplexity" to "https://api.perplexity.ai",
    "qwen" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "stepfun" to "https://api.stepfun.com/v1",
    "togetherai" to "https://api.together.xyz/v1",
    "upstage" to "https://api.upstage.ai/v1/solar",
    "vllm" to "http://localhost:8000/v1",
    "wenxin" to "https://qianfan.baidubce.com/v2",
    "xai" to "https://api.x.ai/v1",
    "xinference" to "http://localhost:9997/v1",
    "zenmux" to "https://zenmux.ai",
    "zhipu" to "https://open.bigmodel.cn/api/paas/v4",
)

/** Official cloud provider: no user-editable endpoint/credentials. */
private const val OFFICIAL_PROVIDER_ID = "lobehub"

/**
 * Mirror of web `ProviderConfig` gating: API key editable unless OAuth-only
 * (unknown client-side, default true); endpoint editable when the card
 * declares a proxyUrl or the provider is custom. The official `lobehub`
 * provider shows neither.
 */
fun providerProxyUrl(providerId: String): String? = PROXY_URLS[providerId.lowercase()]

fun isOfficialProvider(providerId: String): Boolean =
    providerId.equals(OFFICIAL_PROVIDER_ID, ignoreCase = true)

fun isEditableProvider(providerId: String, source: String?): Boolean {
    if (isOfficialProvider(providerId)) return false
    return source == "custom" || providerProxyUrl(providerId) != null
}
