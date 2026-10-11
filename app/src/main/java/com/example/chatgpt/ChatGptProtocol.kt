package com.example.chatgpt

import android.util.Base64
import com.example.visualization.StrictJson
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

internal const val ISSUER = "https://auth.openai.com"
internal const val RESOURCE = "https://api.openai.com/v1"
internal const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
internal const val USAGE_URL = "https://chatgpt.com/settings/usage"
internal const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"

internal fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
internal fun unb64(value: String): ByteArray {
    require(value.isNotEmpty() && value.matches(Regex("[A-Za-z0-9_-]+")))
    return Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
internal fun randomValue(): String = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
internal fun jsonObject(raw: String, limit: Int = 64 * 1024): JSONObject {
    require(StrictJson.parse(raw, limit, 24) is Map<*, *>)
    return JSONObject(raw)
}
internal fun JSONObject.requiredString(key: String, max: Int = 32768): String =
    (get(key) as? String)?.takeIf { it.isNotBlank() && it.length <= max } ?: error("Invalid response")
internal fun JSONObject.optionalString(key: String, max: Int = 32768): String? =
    if (!has(key) || isNull(key)) null else requiredString(key, max)
internal fun JSONObject.integer(key: String): Long {
    val value = get(key)
    require(value is Int || value is Long)
    return (value as Number).toLong()
}

internal data class Registration(val clientId: String, val subject: String? = null, val email: String? = null)
internal data class Credentials(
    val registration: Registration, val access: String, val refresh: String?, val idToken: String,
    val scopes: Set<String>, val expiresAt: Long,
) {
    val planEnabled get() = PLAN_SCOPE in scopes && "resource.invoke" in scopes
    override fun toString() = "Credentials(redacted)"
}

/** Ephemeral secrets never enter saved state, renderer messages, or logs. */
internal class AuthorizationAttempt(val redirect: String, val hostId: String, val registration: Registration?, val now: Long) {
    val state = randomValue()
    val nonce = randomValue()
    val verifier = randomValue()
    val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    private var consumed = false

    fun parameters(enablePlan: Boolean = false): Map<String, String> = linkedMapOf(
        "client_id" to (registration?.clientId ?: "dynamic_agent_client"),
        "ext_agent_host_id" to hostId, "response_type" to "code", "redirect_uri" to redirect,
        "scope" to SCOPES, "resource" to RESOURCE, "state" to state, "nonce" to nonce,
        "code_challenge_method" to "S256", "code_challenge" to challenge,
    ).apply {
        if (registration == null) put("agent_name_hint", "Miniscore")
        if (enablePlan) put("prompt", "consent")
    }

    fun consume(uri: String, currentTime: Long): Pair<String, String> {
        require(!consumed && currentTime >= now && currentTime - now < 5 * 60 * 1000) { "Sign-in expired" }
        val returned = URI(uri)
        val expected = URI(redirect)
        require(returned.scheme == expected.scheme && returned.rawAuthority == expected.rawAuthority && returned.rawPath == expected.rawPath && returned.fragment == null)
        val query = linkedMapOf<String, String>()
        require((returned.rawQuery?.length ?: 0) <= 8192)
        returned.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.forEach {
            val parts = it.split('=', limit = 2)
            require(parts.size == 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            require(!query.containsKey(key))
            query[key] = URLDecoder.decode(parts[1], "UTF-8")
        }
        require(MessageDigest.isEqual(query["state"].orEmpty().toByteArray(), state.toByteArray())) { "Sign-in could not be verified" }
        consumed = true
        if (query.containsKey("error")) throw ChatGptFailure("Sign-in was declined. Your chart is unchanged.")
        val client = query["client_id"] ?: registration?.clientId ?: throw ChatGptFailure("Sign-in registration was incomplete. Try again.")
        if (!client.matches(Regex("oaiapp_[A-Za-z0-9_-]{1,240}")) || registration != null && registration.clientId != client)
            throw ChatGptFailure("Sign-in returned a different registration. Your existing connection is unchanged.")
        val code = query["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 } ?: throw ChatGptFailure("Sign-in did not return a code. Try again.")
        return code to client
    }
    override fun toString() = "AuthorizationAttempt(redacted)"
}

/** RS256 only; other algorithms fail closed until verified against the provider's contract. */
internal fun verifyIdentity(raw: String, jwks: JSONObject, client: String, nonce: String?, now: Long): Registration {
    require(raw.length <= 32768)
    val parts = raw.split('.')
    require(parts.size == 3)
    val header = jsonObject(String(unb64(parts[0]), Charsets.UTF_8))
    require(header.requiredString("alg", 32) == "RS256" && !header.has("crit"))
    val kid = header.requiredString("kid", 256)
    val keys = jwks.getJSONArray("keys")
    require(keys.length() <= 32)
    val matches = (0 until keys.length()).map { keys.getJSONObject(it) }.filter { it.optionalString("kid", 256) == kid }
    require(matches.size == 1)
    val key = matches.single()
    require(key.requiredString("kty", 16) == "RSA")
    require(key.optionalString("use", 16).let { it == null || it == "sig" })
    require(key.optionalString("alg", 16).let { it == null || it == "RS256" })
    if (key.has("key_ops")) require((0 until key.getJSONArray("key_ops").length()).any { key.getJSONArray("key_ops").getString(it) == "verify" })
    val modulus = BigInteger(1, unb64(key.requiredString("n", 2048)))
    val exponent = BigInteger(1, unb64(key.requiredString("e", 16)))
    require(modulus.bitLength() in 2048..8192 && exponent >= BigInteger.valueOf(3))
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    require(Signature.getInstance("SHA256withRSA").run {
        initVerify(publicKey); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)); verify(unb64(parts[2]))
    })
    val claims = jsonObject(String(unb64(parts[1]), Charsets.UTF_8))
    require(claims.requiredString("iss", 256) == ISSUER)
    val aud = claims.get("aud")
    val audiences = when (aud) { is String -> listOf(aud); is JSONArray -> (0 until aud.length()).map { aud.get(it) as? String ?: error("Invalid audience") }; else -> error("Missing audience") }
    require(client in audiences && (audiences.size == 1 || claims.optionalString("azp", 256) == client))
    val seconds = now / 1000
    require(claims.integer("exp") > seconds - 5 && claims.integer("iat") <= seconds + 5)
    if (claims.has("nbf")) require(claims.integer("nbf") <= seconds + 5)
    if (nonce != null) require(claims.requiredString("nonce", 256) == nonce)
    return Registration(client, claims.requiredString("sub", 256), claims.optionalString("email", 320))
}

internal fun tokenCredentials(body: JSONObject, identity: Registration, previous: Credentials?, now: Long): Credentials {
    val granted = body.optionalString("scope", 2048)?.split(' ')?.filter { it.isNotBlank() }?.toSet()
        ?: previous?.scopes ?: emptySet()
    val access = body.optionalString("access_token") ?: ""
    require(access.isNotEmpty() || PLAN_SCOPE !in granted || "resource.invoke" !in granted)
    val expiry = if (access.isNotEmpty()) {
        require(body.requiredString("token_type", 16).equals("Bearer", true))
        body.integer("expires_in").also { require(it in 1..86400) }
    } else 0L
    return Credentials(identity, access, body.optionalString("refresh_token") ?: previous?.refresh,
        body.optionalString("id_token") ?: previous?.idToken ?: error("Missing ID token"), granted, now + expiry * 1000)
}

internal class ChatGptFailure(val userMessage: String, val code: String? = null, val status: Int? = null, val requestId: String? = null) : Exception(userMessage)
internal fun responseFailure(status: Int, body: JSONObject?, requestId: String? = null): ChatGptFailure {
    val code = body?.optJSONObject("error")?.optionalString("code", 128)
        ?: (body?.opt("error") as? String)?.take(128)
    val message = when (code) {
        "subscription_sharing_usage_limit_exceeded" -> "ChatGPT usage limit reached. Open Manage usage to review your limit."
        "subscription_sharing_user_not_eligible" -> "This account or workspace isn’t eligible to use its ChatGPT plan here."
        "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> "ChatGPT plan usage is temporarily unavailable. Try again later."
        "subscription_sharing_unsupported_capability", "subscription_sharing_route_not_supported" -> "This integration isn’t supported for this request."
        else -> when (status) {
            401 -> "ChatGPT couldn’t authorize this request. Sign in again."
            403 -> "ChatGPT plan access is unavailable for this account or integration."
            429 -> "ChatGPT usage limit reached. Open Manage usage to review your limit."
            else -> "Couldn’t reach ChatGPT. Your chart is unchanged. Try again later."
        }
    }
    return ChatGptFailure(message, code, status, requestId)
}
