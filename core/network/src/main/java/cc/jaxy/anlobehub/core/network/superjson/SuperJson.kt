package cc.jaxy.anlobehub.core.network.superjson

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * superjson (v2) 编解码的最小子集, 对应 tRPC `result.data = { json, meta? }` 包。
 *
 * 覆盖范围:
 * - `Date`: 服务端序列化为 ISO 字符串, 这里原样保留为 [JsonPrimitive],
 *   调用方按需解析 (如 `Instant.parse`)。
 * - `undefined`: 对象属性直接删除该 key; 数组元素或根节点还原为 [JsonNull]。
 * - `Map`: `[[k, v], ...]` 在 key 全为字符串时还原为 [JsonObject], 否则保留数组。
 * - `Set`: JSON 没有集合类型, 数组原样保留, 调用方按集合语义处理。
 * - `BigInt` / `URL` / `RegExp` / `Error` / `symbol` 等其余标记: 保留原始 JSON,
 *   不抛错 (lobe-chat mobile 路由实测只出现 Date/undefined, 见 docs/api-contract.md)。
 * - 只解析顶层 `meta.values`; 路径为点分路径, 纯数字段视为数组索引,
 *   `\.` 转义字面的点。注解按路径深度降序应用 (先叶子后父节点),
 *   保证嵌套 Map/Date 等组合还原正确。
 * - 未知标记 / 指向缺失节点的路径会被忽略, 绝不抛错。
 *
 * encode: 普通 input 无特殊类型时直接包一层 `{"json": input}` 即可 (省略 meta);
 * 若 input 内含 Date/Map 等特殊类型, 调用方需自行补 `meta.values`。
 */
object SuperJson {

    /** 把 tRPC input 包成 superjson 请求体 (无特殊类型时省略 meta)。 */
    fun encode(input: JsonElement?): JsonObject =
        buildJsonObject { put("json", input ?: JsonNull) }

    /**
     * 把 `{ json, meta? }` 还原为调用方可直接解码的 [JsonElement]。
     * 示例: `{"json":{"a":1},"meta":{"values":{}}}` -> `{"a":1}`。
     */
    fun unwrap(payload: JsonObject): JsonElement {
        val json = payload["json"] ?: JsonNull
        val values = (payload["meta"] as? JsonObject)?.get("values") as? JsonObject
            ?: return json
        if (values.isEmpty()) return json
        val annotations = values.mapNotNull { (path, raw) ->
            val kinds = when (raw) {
                is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(raw.contentOrNull)
                else -> null
            }
            if (kinds.isNullOrEmpty()) null else path to kinds
        }.sortedByDescending { (path, _) ->
            // 空路径代表根节点, 最后应用; 其余按深度降序 (先叶子后父节点)。
            if (path.isEmpty()) -1 else splitPath(path).size
        }
        var current = json
        for ((path, kinds) in annotations) {
            val segments = splitPath(path)
            for (kind in kinds) {
                current = if (segments.isEmpty()) {
                    applyToValue(current, kind)
                } else {
                    transformAt(current, segments, kind)
                }
            }
        }
        return current
    }

    private fun transformAt(node: JsonElement, segments: List<String>, kind: String): JsonElement {
        val key = segments.first()
        if (segments.size == 1) {
            return when (node) {
                is JsonObject -> {
                    val old = node[key] ?: return node
                    if (kind == UNDEFINED) {
                        JsonObject(node.filterKeys { it != key })
                    } else {
                        val replaced = applyToValue(old, kind)
                        if (replaced == old) {
                            node
                        } else {
                            JsonObject(node.toMutableMap().also { it[key] = replaced })
                        }
                    }
                }
                is JsonArray -> {
                    val index = key.toIntOrNull() ?: return node
                    if (index !in node.indices) return node
                    val replaced = applyToValue(node[index], kind)
                    if (replaced == node[index]) {
                        node
                    } else {
                        JsonArray(node.toMutableList().also { it[index] = replaced })
                    }
                }
                else -> node
            }
        }
        val rest = segments.drop(1)
        return when (node) {
            is JsonObject -> {
                val child = node[key] ?: return node
                val replaced = transformAt(child, rest, kind)
                if (replaced == child) {
                    node
                } else {
                    JsonObject(node.toMutableMap().also { it[key] = replaced })
                }
            }
            is JsonArray -> {
                val index = key.toIntOrNull() ?: return node
                if (index !in node.indices) return node
                val replaced = transformAt(node[index], rest, kind)
                if (replaced == node[index]) {
                    node
                } else {
                    JsonArray(node.toMutableList().also { it[index] = replaced })
                }
            }
            else -> node
        }
    }

    private fun applyToValue(value: JsonElement, kind: String): JsonElement = when (kind) {
        UNDEFINED -> JsonNull
        MAP -> mapToObject(value)
        // Date / Set / 未知标记: 原样保留。
        else -> value
    }

    private fun mapToObject(value: JsonElement): JsonElement {
        if (value !is JsonArray) return value
        val entries = value.map { entry ->
            val pair = (entry as? JsonArray) ?: return value
            if (pair.size != 2) return value
            val key = (pair[0] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: return value
            key to pair[1]
        }
        return JsonObject(entries.toMap())
    }

    /** 按未转义的点切分路径, `\.` 表示字面的点。 */
    private fun splitPath(path: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < path.length) {
            val c = path[i]
            when {
                c == '\\' && i + 1 < path.length && path[i + 1] == '.' -> {
                    current.append('.')
                    i += 2
                }
                c == '.' -> {
                    out.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        out.add(current.toString())
        return out
    }

    private const val UNDEFINED = "undefined"
    private const val MAP = "Map"
}
