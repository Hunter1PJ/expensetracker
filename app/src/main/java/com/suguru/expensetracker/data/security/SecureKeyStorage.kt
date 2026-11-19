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

    /** UID-scoped secure entries. Legacy implementations fall back for compatibility. */
    fun saveKey(uid: String, key: String) = saveKey(key)
    fun getKey(uid: String): String? = getKey()
    fun deleteKey(uid: String) = deleteKey()
    fun hasKey(uid: String): Boolean = hasKey()

    /** True when an unscoped legacy credential remains quarantined for explicit migration. */
    fun hasUnassignedLegacyKey(): Boolean = hasKey()
    fun migrateLegacyKeyToUid(uid: String): Boolean = false
}

class AndroidKeystoreSecureStorage(
    private val context: Context,
    private val prefName: String = "byok_secure_prefs",
    private val keyAlias: String = "gemini_byok_master_key",
    private val currentUidProvider: () -> String? = { null },
    private val uidScopedMode: Boolean = false
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

    private fun getSecretKey(alias: String = keyAlias): SecretKey {
        return (keyStore!!.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @Synchronized
    private fun ensureMasterKey(alias: String) {
        val ks = keyStore ?: return
        if (!ks.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
    }

    private fun scopedPrefix(uid: String): String {
        require(uid.isNotBlank() && uid == uid.trim()) { "Authenticated UID is required." }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "uid_$digest"
    }

    @Synchronized
    override fun saveKey(uid: String, key: String) {
        val trimmed = key.trim()
        val prefix = scopedPrefix(uid)
        val alias = "${keyAlias}_$prefix"
        if (trimmed.isEmpty()) {
            deleteKey(uid)
            return
        }
        try {
            if (!isAndroidKeyStoreAvailable || keyStore == null) error("secure storage unavailable")
            ensureMasterKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))
            sharedPreferences.edit()
                .putString("${prefix}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("${prefix}_ciphertext", Base64.encodeToString(cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
                .apply()
        } catch (_: Exception) {
            throw IllegalStateException("Secure key storage is unavailable.")
        }
    }

    @Synchronized
    override fun getKey(uid: String): String? {
        val prefix = scopedPrefix(uid)
        val ivString = sharedPreferences.getString("${prefix}_iv", null) ?: return null
        val ciphertextString = sharedPreferences.getString("${prefix}_ciphertext", null) ?: return null
        if (!isAndroidKeyStoreAvailable || keyStore == null) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey("${keyAlias}_$prefix"),
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivString, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertextString, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    @Synchronized
    override fun deleteKey(uid: String) {
        val prefix = scopedPrefix(uid)
        sharedPreferences.edit().remove("${prefix}_iv").remove("${prefix}_ciphertext").apply()
        runCatching { keyStore?.deleteEntry("${keyAlias}_$prefix") }
    }

    override fun hasKey(uid: String): Boolean {
        val prefix = scopedPrefix(uid)
        return isAndroidKeyStoreAvailable &&
            sharedPreferences.contains("${prefix}_iv") &&
            sharedPreferences.contains("${prefix}_ciphertext")
    }

    override fun hasUnassignedLegacyKey(): Boolean =
        isAndroidKeyStoreAvailable && sharedPreferences.contains(KEY_IV) && sharedPreferences.contains(KEY_CIPHERTEXT)

    @Synchronized
    override fun migrateLegacyKeyToUid(uid: String): Boolean {
        if (!hasUnassignedLegacyKey()) return false
        val legacyKey = decryptLegacyKey() ?: return false
        return try {
            saveKey(uid, legacyKey)
            if (getKey(uid) != legacyKey) return false
            sharedPreferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).commit()
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    private fun decryptLegacyKey(): String? {
        val ivString = sharedPreferences.getString(KEY_IV, null) ?: return null
        val ciphertextString = sharedPreferences.getString(KEY_CIPHERTEXT, null) ?: return null
        if (ivString == "FALLBACK" || !isAndroidKeyStoreAvailable || keyStore == null) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivString, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertextString, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    @Synchronized
    override fun saveKey(key: String) {
        val uid = currentUidProvider()
        if (uidScopedMode) {
            if (uid.isNullOrBlank()) throw IllegalStateException("Sign in to save a Gemini API key.")
            saveKey(uid, key)
            return
        }
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
        val uid = currentUidProvider()
        if (uidScopedMode) return uid?.takeIf { it.isNotBlank() }?.let(::getKey)
        return decryptLegacyKey()
    }

    @Synchronized
    override fun deleteKey() {
        val uid = currentUidProvider()
        if (uidScopedMode) {
            uid?.takeIf { it.isNotBlank() }?.let(::deleteKey)
            return
        }
        sharedPreferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
    }

    override fun hasKey(): Boolean {
        val uid = currentUidProvider()
        if (uidScopedMode) return uid?.takeIf { it.isNotBlank() }?.let(::hasKey) ?: false
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
