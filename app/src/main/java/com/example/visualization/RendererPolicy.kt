package com.example.visualization

import java.net.URI
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** The asset loader's null result must never become a network request. */
internal object RendererPolicy {
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val INDEX = "$ORIGIN/assets/visualization/index.html"
    private val paths = setOf("/assets/visualization/index.html", "/assets/visualization/renderer.js", "/assets/visualization/style.css")

    fun quotePayload(payload: String): String = JSONObject.quote(payload).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    fun permitsRequest(url: String, method: String): Boolean = runCatching {
        val uri = URI(url)
        method == "GET" && uri.scheme == "https" && uri.rawAuthority == "appassets.androidplatform.net" &&
            uri.rawPath in paths && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    /** Renderer messages acknowledge a native generation; they can never mutate data or specs. */
    fun event(raw: String?, origin: String, mainFrame: Boolean, token: String, sequence: Int): String? {
        if (!mainFrame || origin != ORIGIN || raw == null || raw.toByteArray(StandardCharsets.UTF_8).size > 1024) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.keys().asSequence().toSet() != setOf("kind", "token", "sequence")) return null
            if (json.get("token") !is String || json.getString("token") != token || json.get("kind") !is String) return null
            val number = json.get("sequence")
            if (number !is Int || number != sequence) return null
            json.getString("kind").takeIf { it in setOf("pageReady", "ready", "error") }
        }.getOrNull()
    }
}
