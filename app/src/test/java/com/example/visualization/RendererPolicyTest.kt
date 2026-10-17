package com.example.visualization

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RendererPolicyTest {
    @Test fun onlyExactPackagedGetResourcesAllowed() {
        assertTrue(RendererPolicy.permitsRequest(RendererPolicy.INDEX, "GET"))
        assertTrue(RendererPolicy.permitsRequest("${RendererPolicy.ORIGIN}/assets/visualization/renderer.js", "GET"))
        val denied = listOf("http://appassets.androidplatform.net/assets/visualization/index.html", "https://appassets.androidplatform.net:443/assets/visualization/index.html", "https://appassets.androidplatform.net.evil/assets/visualization/index.html", "https://user@appassets.androidplatform.net/assets/visualization/index.html", "${RendererPolicy.INDEX}?x=1", "${RendererPolicy.INDEX}#x", "${RendererPolicy.ORIGIN}/assets/visualization/%69ndex.html", "${RendererPolicy.ORIGIN}/assets/visualization/../index.html", "${RendererPolicy.ORIGIN}/admin", "file:///assets/visualization/index.html", "content://provider/foo", "https://example.org/")
        denied.forEach { assertFalse(it, RendererPolicy.permitsRequest(it, "GET")) }
        assertFalse(RendererPolicy.permitsRequest(RendererPolicy.INDEX, "POST"))
    }

    @Test fun staleForgedSubframeAndOversizedMessagesCannotAcknowledgeRender() {
        val valid = """{"kind":"ready","token":"secret","sequence":4}"""
        assertEquals("ready", RendererPolicy.event(valid, RendererPolicy.ORIGIN, true, "secret", 4))
        assertNull(RendererPolicy.event(valid, RendererPolicy.ORIGIN, false, "secret", 4))
        assertNull(RendererPolicy.event(valid, "https://example.org", true, "secret", 4))
        assertNull(RendererPolicy.event(valid, RendererPolicy.ORIGIN, true, "other", 4))
        assertNull(RendererPolicy.event(valid, RendererPolicy.ORIGIN, true, "secret", 5))
        listOf(valid.replace("4}", "4.0}"), valid.replace("4}", "\"4\"}"), valid.replace("ready", "changeSpec"), valid.replace("}", ",\"extra\":1}"), "x".repeat(1025), "bad JSON").forEach {
            assertNull(RendererPolicy.event(it, RendererPolicy.ORIGIN, true, "secret", 4))
        }
    }

    @Test fun labelsAreQuotedAsJsonDataIncludingLegacyJavascriptLineSeparators() {
        val payload = "\"x');window.location='https://evil';//\u2028\u2029\\end"
        val quoted = RendererPolicy.quotePayload(payload)
        assertFalse(quoted.contains('\u2028'))
        assertFalse(quoted.contains('\u2029'))
        assertTrue(quoted.contains("\\u2028\\u2029"))
        assertEquals(payload, org.json.JSONArray("[$quoted]").getString(0))
    }
}
