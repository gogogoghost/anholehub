package cc.jaxy.anlobehub.core.common.util

import cc.jaxy.anlobehub.core.common.result.AnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlTest {

    private fun ok(raw: String): String {
        val result = normalizeBaseUrl(raw)
        assertTrue("expected Ok for $raw, got $result", result is AnResult.Ok)
        return (result as AnResult.Ok).value
    }

    private fun errCode(raw: String): String {
        val result = normalizeBaseUrl(raw)
        assertTrue("expected Err for $raw, got $result", result is AnResult.Err)
        return (result as AnResult.Err).error.code
    }

    @Test
    fun `bare host gets https scheme`() {
        assertEquals("https://example.com", ok("example.com"))
    }

    @Test
    fun `trailing slash is stripped`() {
        assertEquals("https://a.com", ok("https://a.com/"))
    }

    @Test
    fun `http scheme is kept`() {
        assertEquals("http://x", ok("http://x"))
    }

    @Test
    fun `non-http scheme is rejected`() {
        assertEquals("INVALID_INPUT", errCode("ftp://x"))
    }

    @Test
    fun `blank input is rejected`() {
        assertEquals("INVALID_INPUT", errCode("   "))
    }

    @Test
    fun `port and path are preserved`() {
        assertEquals("http://h:3000/lobe", ok("http://h:3000/lobe/"))
    }
}
