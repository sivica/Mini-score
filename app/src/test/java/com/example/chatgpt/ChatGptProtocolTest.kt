package com.example.chatgpt

import com.example.PreseasonGameData
import com.example.FeedProvenance
import com.example.visualization.VisualizationContract
import com.example.visualization.VisualizationSessionController
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatGptProtocolTest {
    private val now = 1_800_000_000_000L
    private val client = "oaiapp_test"
    private fun attempt(registration: Registration? = null) = AuthorizationAttempt("http://127.0.0.1:3456/auth/callback", "urn:uuid:test", registration, now)
    private fun callback(attempt: AuthorizationAttempt, extra: String = "") = "${attempt.redirect}?state=${attempt.state}&code=secret&client_id=$client$extra"
    private fun rejected(block: () -> Unit) { assertThrows(Exception::class.java, block) }

    @Test fun pkceAndSecretsAreFreshAndNotPrinted() {
        val first = attempt(); val second = attempt()
        assertNotEquals(first.state, second.state); assertNotEquals(first.nonce, second.nonce); assertNotEquals(first.verifier, second.verifier)
        assertEquals(b64(java.security.MessageDigest.getInstance("SHA-256").digest(first.verifier.toByteArray())), first.challenge)
        assertFalse(first.toString().contains(first.verifier))
        assertEquals("dynamic_agent_client", first.parameters()["client_id"])
        assertEquals("Miniscore", first.parameters()["agent_name_hint"])
        assertEquals(SCOPES, first.parameters()["scope"])
    }
    @Test fun callbackIsOneTimeAndExpires() {
        val auth = attempt(); assertEquals("secret" to client, auth.consume(callback(auth), now))
        rejected { auth.consume(callback(auth), now) }
        val expired = attempt(); rejected { expired.consume(callback(expired), now + 300_000) }
    }
    @Test fun rejectsWrongStateDuplicateParametersAndWrongOriginPathPort() {
        val auth = attempt()
        rejected { auth.consume(callback(auth).replace(auth.state, "wrong"), now) }
        rejected { auth.consume(callback(auth, "&state=${auth.state}"), now) }
        rejected { auth.consume(callback(auth).replace("127.0.0.1", "localhost"), now) }
        rejected { auth.consume(callback(auth).replace("3456", "3457"), now) }
        rejected { auth.consume(callback(auth).replace("/auth/callback", "/callback"), now) }
        assertEquals(client, auth.consume(callback(auth), now).second)
    }
    @Test fun denialIsHandledBeforeCodeExchange() {
        val auth = attempt()
        val failure = assertThrows(ChatGptFailure::class.java) { auth.consume(callback(auth, "&error=access_denied"), now) }
        assertTrue(failure.userMessage.contains("declined"))
        rejected { auth.consume(callback(auth), now) }
    }
    @Test fun returningRegistrationNeverUsesDynamicIdOrChangesClient() {
        val auth = attempt(Registration(client, "subject"))
        assertEquals(client, auth.parameters()["client_id"]); assertNull(auth.parameters()["agent_name_hint"])
        assertEquals("consent", auth.parameters(true)["prompt"])
        assertEquals(client, auth.consume("${auth.redirect}?state=${auth.state}&code=x", now).second)
        val other = attempt(Registration(client, "subject"))
        rejected { other.consume(callback(other).replace(client, "oaiapp_other"), now) }
    }

    private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private fun jwks(): JSONObject {
        val rsa = pair.public as RSAPublicKey
        fun unsigned(value: java.math.BigInteger) = value.toByteArray().let { if (it[0] == 0.toByte()) it.drop(1).toByteArray() else it }
        return JSONObject().put("keys", JSONArray().put(JSONObject().put("kid", "test-key").put("kty", "RSA").put("alg", "RS256")
            .put("n", b64(unsigned(rsa.modulus))).put("e", b64(unsigned(rsa.publicExponent)))))
    }
    private fun claims() = JSONObject().put("iss", ISSUER).put("aud", client).put("sub", "subject").put("email", "test@example.com")
        .put("exp", now / 1000 + 60).put("iat", now / 1000).put("nonce", "nonce")
    private fun jwt(claims: JSONObject = claims(), alg: String = "RS256"): String {
        val data = b64(JSONObject().put("alg", alg).put("kid", "test-key").toString().toByteArray()) + "." + b64(claims.toString().toByteArray())
        val signed = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(data.toByteArray()); sign() }
        return "$data.${b64(signed)}"
    }
    @Test fun acceptsCryptographicallyValidatedIdentity() {
        val identity = verifyIdentity(jwt(), jwks(), client, "nonce", now)
        assertEquals("subject", identity.subject); assertEquals(client, identity.clientId)
    }
    @Test fun rejectsInvalidSignatureIssuerAudienceNonceExpiredOrFutureToken() {
        val valid = jwt()
        rejected { verifyIdentity(valid.substringBeforeLast('.') + "." + b64(ByteArray(256)), jwks(), client, "nonce", now) }
        listOf(claims().put("iss", "https://evil.example"), claims().put("aud", "oaiapp_other"), claims().put("nonce", "wrong"),
            claims().put("exp", now / 1000 - 6), claims().put("iat", now / 1000 + 6), claims().put("exp", "1800000060")).forEach {
            rejected { verifyIdentity(jwt(it), jwks(), client, "nonce", now) }
        }
        rejected { verifyIdentity(jwt(alg = "none"), jwks(), client, "nonce", now) }
    }
    @Test fun multipleAudiencesRequireAuthorizedParty() {
        val data = claims().put("aud", JSONArray().put(client).put("other"))
        rejected { verifyIdentity(jwt(data), jwks(), client, "nonce", now) }
        assertEquals(client, verifyIdentity(jwt(data.put("azp", client)), jwks(), client, "nonce", now).clientId)
    }
    @Test fun duplicateSigningKeysFailClosed() {
        val keys = jwks(); keys.getJSONArray("keys").put(keys.getJSONArray("keys").get(0))
        rejected { verifyIdentity(jwt(), keys, client, "nonce", now) }
    }
    private fun token(scopes: String = SCOPES) = JSONObject().put("token_type", "Bearer").put("access_token", "access-secret")
        .put("refresh_token", "refresh-secret").put("id_token", "id-secret").put("expires_in", 3600).put("scope", scopes)
    @Test fun identitySignInDoesNotEnableInference() {
        val credentials = tokenCredentials(token("openid profile email"), Registration(client, "subject"), null, now)
        assertFalse(credentials.planEnabled)
        assertTrue(tokenCredentials(token(), Registration(client, "subject"), null, now).planEnabled)
        assertFalse(credentials.toString().contains("access-secret"))
        val identityOnly = tokenCredentials(JSONObject().put("id_token", "id-secret").put("scope", "openid profile email"), Registration(client, "subject"), null, now)
        assertFalse(identityOnly.planEnabled)
        val saved = SavedConnections(listOf(SavedAccount(identityOnly.registration, identityOnly)), client)
        assertEquals(saved, decodeConnections(encodeConnections(saved)))
    }
    @Test fun strictTokenExpiryAndRefreshRotation() {
        rejected { tokenCredentials(token().put("expires_in", "3600"), Registration(client, "s"), null, now) }
        rejected { tokenCredentials(token().put("expires_in", 3600.0), Registration(client, "s"), null, now) }
        val old = tokenCredentials(token(), Registration(client, "s"), null, now)
        val updated = tokenCredentials(token().put("refresh_token", "rotated").apply { remove("scope"); remove("id_token") }, old.registration, old, now + 1000)
        assertEquals("rotated", updated.refresh); assertEquals(old.scopes, updated.scopes); assertEquals(old.idToken, updated.idToken)
    }
    @Test fun protectedStorageFormatKeepsAccountsSeparate() {
        val first = tokenCredentials(token(), Registration(client, "s", "same@example.com"), null, now)
        val second = tokenCredentials(token(), Registration("oaiapp_other", "other", "same@example.com"), null, now)
        val saved = SavedConnections(listOf(SavedAccount(first.registration, first), SavedAccount(second.registration, second)), client)
        assertEquals(saved, decodeConnections(encodeConnections(saved)))
        rejected { encodeConnections(saved.copy(accounts = saved.accounts + saved.accounts.first())) }
        rejected { encodeConnections(saved.copy(active = "unregistered")) }
    }

    private fun event(type: String, body: JSONObject = JSONObject()) = "data: ${body.put("type", type)}\n\n"
    private fun completion(text: String, status: String = "completed") = event("response.completed", JSONObject().put("response", JSONObject()
        .put("status", status).put("output", JSONArray().put(JSONObject().put("type", "message").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))))
    private suspend fun stream(raw: String) = readChartStream(ByteArrayInputStream(raw.toByteArray()))
    @Test fun completedStreamProducesOnlyMatchingFinalText() = runTest {
        assertEquals("{}", stream(event("response.output_text.delta", JSONObject().put("delta", "{}")) + completion("{}")))
        assertEquals("{}", stream(completion("{}")))
    }
    @Test fun interruptedIncompleteRefusedAndMismatchStreamsAreRejected() = runTest {
        val bad = listOf(event("response.output_text.delta", JSONObject().put("delta", "{}")), completion("{}", "incomplete"),
            event("response.refusal.done"), event("response.incomplete"), "data: [DONE]\n\n",
            event("response.output_text.delta", JSONObject().put("delta", "{}")) + completion("{\"x\":1}"))
        for (input in bad) { try { stream(input); fail("Accepted interrupted or invalid stream") } catch (_: Exception) {} }
    }
    @Test fun streamedLimitAfterPartialTextIsStillAFailure() = runTest {
        val body = JSONObject().put("response", JSONObject().put("error", JSONObject().put("code", "subscription_sharing_usage_limit_exceeded")))
        try { stream(event("response.output_text.delta", JSONObject().put("delta", "{}")) + event("response.failed", body)); fail() }
        catch (failure: ChatGptFailure) { assertTrue(failure.userMessage.contains("Manage usage")); assertEquals("subscription_sharing_usage_limit_exceeded", failure.code) }
    }
    @Test fun oversizedModelOutputCannotReachChart() = runTest {
        try { stream(event("response.output_text.delta", JSONObject().put("delta", "x".repeat(16384))) + event("response.output_text.delta", JSONObject().put("delta", "x"))); fail() }
        catch (_: Exception) {}
    }
    @Test fun generatedSettingsRejectWrongGameArbitraryCodeAndUnsupportedFilterAndPreserveChart() = runTest {
        val controller = VisualizationSessionController(this, { error("No refresh") })
        controller.open(PreseasonGameData.createGame(), FeedProvenance())
        val original = controller.session.value!!
        assertFalse(controller.applyGeneratedSpec(original, original.spec.copy(gameId = "other")))
        assertEquals(original, controller.session.value)
        listOf("{\"unsupported\":true}", "<script>alert(1)</script>", "{\"schemaVersion\":1,\"template\":\"playerStats\",\"gameId\":\"${original.snapshot.gameId}\",\"starters\":true}").forEach {
            rejected { VisualizationContract.decodeSpec(it, original.snapshot) }; assertEquals(original, controller.session.value)
        }
        assertTrue(controller.applyGeneratedSpec(original, original.spec.copy(metric = "rebounds")))
        assertEquals("rebounds", controller.session.value!!.spec.metric)
    }
    @Test fun changingFiltersOrClosingRejectsLateAIResponse() = runTest {
        val controller = VisualizationSessionController(this, { error("No refresh") })
        controller.open(PreseasonGameData.createGame(), FeedProvenance())
        val original = controller.session.value!!
        controller.changeSpec(original.spec.copy(metric = "assists"))
        assertFalse(controller.applyGeneratedSpec(original, original.spec.copy(metric = "rebounds")))
        assertEquals("assists", controller.session.value!!.spec.metric)
        controller.changeSpec(original.spec)
        assertFalse(controller.applyGeneratedSpec(original, original.spec.copy(metric = "rebounds")))
        controller.close()
        assertFalse(controller.applyGeneratedSpec(original, original.spec))
        controller.open(PreseasonGameData.createGame(), FeedProvenance())
        assertFalse(controller.applyGeneratedSpec(original, original.spec))
    }
}
