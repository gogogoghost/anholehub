package cc.jaxy.anlobehub.core.data.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `contentToText handles primitives`() {
        assertEquals("", contentToText(null))
        assertEquals("hi", contentToText(json.parseToJsonElement("\"hi\"")))
        assertEquals("3", contentToText(json.parseToJsonElement("3")))
    }

    @Test
    fun `contentToText joins text parts`() {
        val el = json.parseToJsonElement("""[{"type":"text","text":"a"},{"type":"text","text":"b"}]""")
        assertEquals("ab", contentToText(el.jsonArray))
    }

    @Test
    fun `contentToText prefers text field of object`() {
        val el = json.parseToJsonElement("""{"text":"z","other":1}""")
        assertEquals("z", contentToText(el.jsonObject))
    }

    @Test
    fun `toChatMessage fills defaults on missing fields`() {
        val msg = json.parseToJsonElement("""{"id":"m1"}""").toChatMessage()
        assertEquals("m1", msg.id)
        assertEquals("assistant", msg.role)
        assertEquals("", msg.content)
    }

    @Test
    fun `toChatMessage maps full shape`() {
        val msg = json.parseToJsonElement(
            """{"id":"m1","role":"user","content":"hi","createdAt":1700000000000,
               |"model":"gpt","provider":"openai","error":null}""".trimMargin(),
        ).toChatMessage()
        assertEquals("user", msg.role)
        assertEquals("hi", msg.content)
        assertEquals(1700000000000L, msg.createdAt)
        assertEquals("gpt", msg.model)
        assertEquals("openai", msg.provider)
        assertEquals(null, msg.error)
    }

    @Test
    fun `extractId accepts string or object`() {
        assertEquals("a", json.parseToJsonElement("\"a\"").extractId())
        assertEquals("b", json.parseToJsonElement("""{"id":"b"}""").extractId())
        assertEquals(null, json.parseToJsonElement("""{"x":1}""").extractId())
    }
}
