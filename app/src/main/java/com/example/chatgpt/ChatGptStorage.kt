package com.example.chatgpt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SavedAccount(val registration: Registration, val credentials: Credentials? = null, val welcomed: Boolean = false)
internal data class SavedConnections(val accounts: List<SavedAccount> = emptyList(), val active: String? = null)

/** Encrypted credentials and stable host identity are excluded from cloud backup and device transfer. */
internal class ChatGptStorage(context: Context) {
    private val directory = File(context.noBackupFilesDir, "chatgpt").apply { mkdirs() }
    private val stateFile = AtomicFile(File(directory, "connections.aes"))
    val hostId: String = File(directory, "host-id").let { file ->
        if (file.exists()) file.readText().also { require(it.matches(Regex("urn:uuid:[a-f0-9-]{36}"))) }
        else "urn:uuid:${UUID.randomUUID()}".also { writeAtomic(AtomicFile(file), it.toByteArray()) }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "miniscore-chatgpt-v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }
    fun load(): SavedConnections {
        if (!stateFile.baseFile.exists()) return SavedConnections()
        val encrypted = stateFile.openRead().use { input ->
            val bytes = input.readBytesBounded(512 * 1024)
            require(bytes.size > 29 && bytes[0] == 1.toByte())
            bytes
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
        cipher.updateAAD("miniscore-chatgpt-v1".toByteArray())
        return decodeConnections(String(cipher.doFinal(encrypted.copyOfRange(13, encrypted.size)), Charsets.UTF_8))
    }
    fun save(state: SavedConnections) {
        val clear = encodeConnections(state).toByteArray()
        require(clear.size <= 500 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("miniscore-chatgpt-v1".toByteArray())
        require(cipher.iv.size == 12)
        writeAtomic(stateFile, byteArrayOf(1) + cipher.iv + cipher.doFinal(clear))
    }
    fun clearCorruptState() = stateFile.delete()
    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (failure: Exception) { file.failWrite(out); throw failure }
    }
}

internal fun encodeConnections(state: SavedConnections): String {
    require(state.accounts.size <= 8 && state.accounts.map { it.registration.clientId }.distinct().size == state.accounts.size)
    require(state.active == null || state.accounts.any { it.registration.clientId == state.active })
    return JSONObject().put("version", 1).put("active", state.active ?: JSONObject.NULL)
        .put("accounts", JSONArray(state.accounts.map { saved ->
            val reg = saved.registration
            JSONObject().put("client", reg.clientId).put("subject", reg.subject ?: JSONObject.NULL)
                .put("email", reg.email ?: JSONObject.NULL).put("welcomed", saved.welcomed).also { obj ->
                    saved.credentials?.let { token ->
                        require(token.registration == reg)
                        obj.put("credentials", JSONObject().put("access", token.access.ifEmpty { null } ?: JSONObject.NULL).put("refresh", token.refresh ?: JSONObject.NULL)
                            .put("idToken", token.idToken).put("scopes", JSONArray(token.scopes.toList())).put("expiresAt", token.expiresAt))
                    }
                }
        })).toString()
}
internal fun decodeConnections(raw: String): SavedConnections {
    val json = jsonObject(raw, 500 * 1024)
    require(json.integer("version") == 1L)
    val array = json.getJSONArray("accounts")
    require(array.length() <= 8)
    val accounts = (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val registration = Registration(obj.requiredString("client", 256), obj.optionalString("subject", 256), obj.optionalString("email", 320))
        val token = obj.optJSONObject("credentials")?.let { saved ->
            require(registration.subject != null)
            val scopes = saved.getJSONArray("scopes")
            require(scopes.length() <= 32)
            Credentials(registration, saved.optionalString("access") ?: "", saved.optionalString("refresh"), saved.requiredString("idToken"),
                (0 until scopes.length()).map { scopes.get(it) as? String ?: error("Invalid scopes") }.toSet(), saved.integer("expiresAt"))
        }
        SavedAccount(registration, token, obj.get("welcomed") as? Boolean ?: error("Invalid state"))
    }
    return SavedConnections(accounts, json.optionalString("active", 256)).also { encodeConnections(it) }
}
