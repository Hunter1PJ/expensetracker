package com.suguru.expensetracker.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure Keystore-backed storage specifically for user-provided BYOK API keys.
 * Encrypts keys using AES/GCM/NoPadding with a hardware-backed or Android Keystore master key.
 * Never logs or exposes raw key plaintext.
 */
interface SecureKeyStorage {
    fun saveKey(key: String)
    fun getKey(): String?
    fun deleteKey()
    fun hasKey(): Boolean
}

class AndroidKeystoreSecureStorage(
    private val context: Context,
    private val prefName: String = "byok_secure_prefs",
    private val keyAlias: String = "gemini_byok_master_key"
) : SecureKeyStorage {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }

    private val isAndroidKeyStoreAvailable: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private val keyStore: KeyStore? by lazy {
        if (isAndroidKeyStoreAvailable) {
            try {
                KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    init {
        if (isAndroidKeyStoreAvailable) {
            try {
                ensureMasterKey()
            } catch (_: Exception) {
                // Key operations fail closed; storage remains unavailable until Android Keystore works.
            }
        }
    }

    @Synchronized
    private fun ensureMasterKey() {
        val ks = keyStore ?: return
        if (!ks.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore!!.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @Synchronized
    override fun saveKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            deleteKey()
            return
        }

        try {
            if (!isAndroidKeyStoreAvailable || keyStore == null) {
                throw IllegalStateException("Secure key storage is unavailable.")
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            sharedPreferences.edit()
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .apply()
        } catch (_: Exception) {
            throw IllegalStateException("Secure key storage is unavailable.")
        }
    }

    @Synchronized
    override fun getKey(): String? {
        val ivString = sharedPreferences.getString(KEY_IV, null) ?: return null
        val ciphertextString = sharedPreferences.getString(KEY_CIPHERTEXT, null) ?: return null
        if (ivString == "FALLBACK") {
            deleteKey()
            return null
        }
        if (!isAndroidKeyStoreAvailable || keyStore == null) return null

        return try {
            val iv = Base64.decode(ivString, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextString, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    override fun deleteKey() {
        sharedPreferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
    }

    override fun hasKey(): Boolean {
        if (sharedPreferences.getString(KEY_IV, null) == "FALLBACK") {
            deleteKey()
            return false
        }
        return isAndroidKeyStoreAvailable && sharedPreferences.contains(KEY_IV) && sharedPreferences.contains(KEY_CIPHERTEXT)
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_IV = "encrypted_key_iv"
        private const val KEY_CIPHERTEXT = "encrypted_key_ciphertext"
    }
}
