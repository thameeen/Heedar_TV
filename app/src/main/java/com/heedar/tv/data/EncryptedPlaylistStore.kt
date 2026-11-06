package com.heedar.tv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.heedar.tv.domain.ServerConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the panel URL and username in SharedPreferences and keeps the password
 * encrypted with an AES-256-GCM key that never leaves the Android Keystore.
 *
 * minSdk is 23 because [KeyGenParameterSpec] (hardware-backed AES keys) is only
 * available from API 23; older devices would silently fall back to plaintext.
 */
class EncryptedPlaylistStore(context: Context) : PlaylistStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, encrypt(config.password))
            .apply()
    }

    override fun load(): ServerConfig? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val encryptedPassword = prefs.getString(KEY_PASSWORD, null) ?: return null
        val password = decrypt(encryptedPassword) ?: return null
        return ServerConfig(baseUrl = baseUrl, username = username, password = password)
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Returns null when the stored blob cannot be decrypted (e.g. key was invalidated). */
    private fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH) { "Stored password blob is too short" }

        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "heedar_tv_playlist_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val PREFS_NAME = "heedar_tv_playlist"
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password_enc"
    }
}
