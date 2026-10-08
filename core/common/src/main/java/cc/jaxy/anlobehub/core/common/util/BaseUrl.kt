package cc.jaxy.anlobehub.core.common.util

import cc.jaxy.anlobehub.core.common.result.AnError
import cc.jaxy.anlobehub.core.common.result.AnResult

/**
 * Pure syntactic normalization (no network reachability check).
 *
 * Rule: trim -> strip trailing `/` -> prepend `https://` when no scheme ->
 * reject non-http(s) -> require a valid HTTP(S) host.
 */
fun normalizeBaseUrl(raw: String): AnResult<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return AnResult.Err(AnError(code = "INVALID_INPUT", message = "Base URL is empty"))
    }
    val withoutTrailingSlashes = trimmed.trimEnd('/')
    if (withoutTrailingSlashes.isEmpty()) {
        return AnResult.Err(AnError(code = "INVALID_INPUT", message = "Base URL is empty"))
    }
    val withScheme =
        if ("://" in withoutTrailingSlashes) {
            withoutTrailingSlashes
        } else {
            "https://$withoutTrailingSlashes"
        }
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") {
        return AnResult.Err(
            AnError(code = "INVALID_INPUT", message = "Only http(s) URLs are supported: $trimmed"),
        )
    }
    val uri =
        try {
            java.net.URI(withScheme)
        } catch (t: Exception) {
            return AnResult.Err(
                AnError(code = "INVALID_INPUT", message = "Malformed base URL: $trimmed", cause = t),
            )
        }
    val host = uri.host
    if (host.isNullOrBlank()) {
        return AnResult.Err(
            AnError(code = "INVALID_INPUT", message = "Base URL has no host: $trimmed"),
        )
    }
    val authority = uri.rawAuthority.ifBlank { host }
    val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotEmpty() && it != "/" } ?: ""
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    val normalized = "$scheme://$authority$path$query"
    return AnResult.Ok(normalized)
}
