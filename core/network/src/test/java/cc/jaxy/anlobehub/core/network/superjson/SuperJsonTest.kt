package cc.jaxy.anlobehub.core.network.superjson

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun unwrap(raw: String) =
        SuperJson.unwrap(json.parseToJsonElement(raw).jsonObject)

    @Test
    fun `plain json without meta returns as-is`() {
        val out = unwrap("""{"json":{"a":1}}""").jsonObject
        assertEquals(1, out["a"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `empty meta values returns as-is`() {
        val out = unwrap("""{"json":{"a":1},"meta":{"values":{}}}""").jsonObject
        assertEquals(1, out["a"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `date annotation keeps ISO string`() {
        val out = unwrap(
            """{"json":{"createdAt":"2026-10-08T00:00:00.000Z"},
                |"meta":{"values":{"createdAt":["Date"]}}}""".trimMargin(),
        ).jsonObject
        assertEquals("2026-10-08T00:00:00.000Z", out["createdAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `undefined annotation removes object key`() {
        val out = unwrap(
            """{"json":{"a":1,"b":2},"meta":{"values":{"b":["undefined"]}}}""",
        ).jsonObject
        assertEquals(1, out["a"]?.jsonPrimitive?.content?.toInt())
        assertNull(out["b"])
    }

    @Test
    fun `undefined annotation in array becomes null`() {
        val out = unwrap(
            """{"json":[1,2],"meta":{"values":{"1":["undefined"]}}}""",
        ) as JsonArray
        assertEquals(JsonNull, out[1])
    }

    @Test
    fun `nested date path resolves`() {
        val out = unwrap(
            """{"json":{"before":{"createdAt":"2026-01-01","id":"x"}},
                |"meta":{"values":{"before.createdAt":["Date"]}}}""".trimMargin(),
        ).jsonObject["before"]?.jsonObject
        assertEquals("2026-01-01", out?.get("createdAt")?.jsonPrimitive?.content)
    }

    @Test
    fun `map with string keys becomes object`() {
        val out = unwrap(
            """{"json":{"m":[["k","v"]]},"meta":{"values":{"m":["Map"]}}}""",
        ).jsonObject["m"]?.jsonObject
        assertEquals("v", out?.get("k")?.jsonPrimitive?.content)
    }

    @Test
    fun `unknown annotation and bad path are ignored`() {
        val out = unwrap(
            """{"json":{"a":1},"meta":{"values":{"nope.deeper":["BigInt"],"a":["Nope"]}}}""",
        ).jsonObject
        assertEquals(1, out["a"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `encode wraps input in json key`() {
        val encoded = SuperJson.encode(JsonPrimitive("x"))
        assertEquals("x", encoded["json"]?.jsonPrimitive?.content)
        assertTrue(encoded["meta"] == null)
    }
}
