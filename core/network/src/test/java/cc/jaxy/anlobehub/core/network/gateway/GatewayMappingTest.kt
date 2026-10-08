package cc.jaxy.anlobehub.core.network.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayMappingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val op = "op-1"

    private fun event(raw: String): JsonObject =
        json.parseToJsonElement(raw).jsonObject

    @Test
    fun `ws base converts http to ws`() {
        assertEquals("ws://h/gw", gatewayWsBase("http://h/gw/"))
        assertEquals("wss://h/gw", gatewayWsBase("https://h/gw"))
        assertEquals("wss://h/gw", gatewayWsBase("h/gw"))
    }

    @Test
    fun `v1 and v2 urls are well-formed`() {
        assertEquals("wss://h/ws?operationId=op-1", buildV1Url("https://h", op))
        assertEquals(
            "wss://h/v2/ws?token=t&clientId=c",
            buildV2Url("https://h/", "t", "c"),
        )
    }

    @Test
    fun `text chunk maps to StreamChunk`() {
        val out = mapAgentEvent(
            event("""{"type":"stream_chunk","operationId":"op-1","data":{"chunkType":"text","content":"hi"}}"""),
            envelopeOperationId = null,
            defaultOperationId = op,
        )
        assertEquals(GatewayEvent.StreamChunk("op-1", "hi", null), out)
    }

    @Test
    fun `reasoning chunk maps reasoning delta`() {
        val out = mapAgentEvent(
            event("""{"type":"stream_chunk","data":{"chunkType":"reasoning","reasoning":"r"}}"""),
            envelopeOperationId = op,
            defaultOperationId = op,
        )
        assertEquals(GatewayEvent.StreamChunk(op, "", "r"), out)
    }

    @Test
    fun `empty chunk is ignored`() {
        val out = mapAgentEvent(
            event("""{"type":"stream_chunk","data":{"chunkType":"text"}}"""),
            envelopeOperationId = op,
            defaultOperationId = op,
        )
        assertNull(out)
    }

    @Test
    fun `stream start captures assistant id`() {
        val out = mapAgentEvent(
            event("""{"type":"stream_start","data":{"assistantMessage":{"id":"a1"}}}"""),
            envelopeOperationId = op,
            defaultOperationId = op,
        )
        assertEquals(GatewayEvent.StreamStarted(op, "a1"), out)
    }

    @Test
    fun `own runtime end terminates`() {
        val out = mapAgentEvent(
            event("""{"type":"agent_runtime_end","operationId":"op-1"}"""),
            envelopeOperationId = op,
            defaultOperationId = op,
        )
        assertEquals(GatewayEvent.RunEnded(op, terminal = true), out)
    }

    @Test
    fun `mirrored member end never terminates`() {
        val out = mapAgentEvent(
            event("""{"type":"agent_runtime_end","operationId":"other"}"""),
            envelopeOperationId = "other",
            defaultOperationId = op,
        )
        assertNull(out)
        assertNull(
            mapAgentEvent(
                event("""{"type":"member_runtime_end","operationId":"op-1"}"""),
                envelopeOperationId = op,
                defaultOperationId = op,
            ),
        )
    }

    @Test
    fun `error event maps message`() {
        val out = mapAgentEvent(
            event("""{"type":"error","data":{"message":"boom"}}"""),
            envelopeOperationId = op,
            defaultOperationId = op,
        )
        assertEquals(GatewayEvent.RunError(op, "boom"), out)
    }

    @Test
    fun `unknown event types are ignored`() {
        assertNull(
            mapAgentEvent(
                event("""{"type":"step_start","data":{}}"""),
                envelopeOperationId = op,
                defaultOperationId = op,
            ),
        )
        assertNull(
            mapAgentEvent(
                event("""{"no-type":true}"""),
                envelopeOperationId = op,
                defaultOperationId = op,
            ),
        )
    }

    @Test
    fun `terminal statuses match`() {
        assertTrue(isTerminalStatus("completed"))
        assertTrue(isTerminalStatus("interrupted"))
        assertTrue(!isTerminalStatus("running"))
        assertTrue(!isTerminalStatus(null))
    }
}
