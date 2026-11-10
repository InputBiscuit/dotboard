package io.github.inputbiscuit.dotboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureDataStore by preferencesDataStore(name = "secure")

/**
 * Stores the Rail Data Marketplace key encrypted with AES-256-GCM under an Android Keystore key, which never
 * leaves secure hardware. (androidx.security-crypto's EncryptedSharedPreferences is deprecated, so we don't use it.)
 */
class ApiKeyStore(private val context: Context) {

    /** Whether a key is saved (without decrypting it). */
    val hasKey: Flow<Boolean> = context.secureDataStore.data.map { !it[ENCRYPTED].isNullOrEmpty() }

    suspend fun get(): String {
        migratePlainKey()
        val stored = context.secureDataStore.data.first()[ENCRYPTED] ?: return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
    }

    suspend fun set(apiKey: String) {
        val trimmed = apiKey.trim()
        context.secureDataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(ENCRYPTED) else prefs[ENCRYPTED] = encrypt(trimmed)
        }
    }

    /** Older builds kept the key in plain settings; move it over once. */
    private suspend fun migratePlainKey() {
        val plain = context.settingsDataStore.data.first()[LEGACY_PLAIN_KEY] ?: return
        if (plain.isNotBlank()) set(plain)
        context.settingsDataStore.edit { it.remove(LEGACY_PLAIN_KEY) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(sealed)
    }

    private fun decrypt(stored: String): String {
        val (iv, sealed) = stored.split(":", limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "dotboard_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val ENCRYPTED = stringPreferencesKey("api_key_encrypted")
        val LEGACY_PLAIN_KEY = stringPreferencesKey("api_key")
    }
}
