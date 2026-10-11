package com.example.chatgpt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.URI
import java.io.File

/** Uses synthetic credentials and a local callback only. Never logs in to an OpenAI account. */
@RunWith(AndroidJUnit4::class)
class ChatGptDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun keystoreEncryptionSurvivesReopenAndExcludesCredentialsFromBackup() {
        val store = ChatGptStorage(context)
        val before = store.load()
        val now = System.currentTimeMillis()
        val registration = Registration("oaiapp_device_test", "synthetic-subject", "fixture@example.com")
        val credentials = Credentials(registration, "synthetic-access-secret", "synthetic-refresh-secret", "synthetic-id-secret", SCOPES.split(' ').toSet(), now + 3600000)
        val expected = SavedConnections(listOf(SavedAccount(registration, credentials)), registration.clientId)
        try {
            store.save(expected)
            val persisted = File(context.noBackupFilesDir, "chatgpt/connections.aes").readBytes()
            assertFalse(String(persisted, Charsets.ISO_8859_1).contains("synthetic-access-secret"))
            assertFalse(String(persisted, Charsets.ISO_8859_1).contains("fixture@example.com"))
            val reopened = ChatGptStorage(context)
            assertEquals(expected, reopened.load()); assertEquals(store.hostId, reopened.hostId)
            persisted[persisted.lastIndex] = (persisted.last().toInt() xor 1).toByte()
            File(context.noBackupFilesDir, "chatgpt/connections.aes").writeBytes(persisted)
            assertThrows(Exception::class.java) { reopened.load() }
        } finally { store.save(before) }
    }
    @Test fun callbackReceivesAndroidLoopbackRequestAndRejectsReplay(): Unit = runBlocking {
        withTimeout(15000) {
            LoopbackCallback().use { callback ->
                val attempt = AuthorizationAttempt(callback.redirect, "urn:uuid:test", null, System.currentTimeMillis())
                val received = async(Dispatchers.IO) { callback.receive(attempt) }
                val target = URI(callback.redirect)
                withContext(Dispatchers.IO) {
                    Socket("127.0.0.1", target.port).use { socket ->
                        socket.soTimeout = 5000
                        socket.getOutputStream().write("GET /auth/callback?state=${attempt.state}&code=fixture-code&client_id=oaiapp_fixture HTTP/1.1\r\nHost: 127.0.0.1:${target.port}\r\n\r\n".toByteArray())
                        val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                        assertFalse(response.contains("fixture-code")); assertFalse(response.contains(attempt.state))
                    }
                }
                assertEquals("fixture-code" to "oaiapp_fixture", received.await())
                assertThrows(Exception::class.java) { attempt.consume("${callback.redirect}?state=${attempt.state}&code=fixture-code&client_id=oaiapp_fixture", System.currentTimeMillis()) }
            }
        }
    }
}
