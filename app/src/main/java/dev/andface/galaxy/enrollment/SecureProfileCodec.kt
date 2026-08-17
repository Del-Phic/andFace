package dev.andface.galaxy.enrollment

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureProfileCodec private constructor(
    private val keyAlias: String,
    private val fallbackKeyAliases: List<String> = emptyList(),
    private val regenerateKeyOnProtectFailure: Boolean = false
) {
    fun protect(plainJson: String): String {
        return runCatching {
            protectWithKey(plainJson, getOrCreateKey(keyAlias))
        }.getOrElse { error ->
            if (!regenerateKeyOnProtectFailure) throw error
            deleteKey(keyAlias)
            protectWithKey(plainJson, getOrCreateKey(keyAlias))
        }
    }

    private fun protectWithKey(plainJson: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))
        return JSONObject()
            .put("v", VERSION)
            .put("alg", TRANSFORMATION)
            .put("kid", keyAlias)
            .put("iv", cipher.iv.toBase64())
            .put("ciphertext", ciphertext.toBase64())
            .toString()
    }

    fun unprotect(storedValue: String): String {
        check(isProtected(storedValue)) { "Stored value is not protected." }
        val json = JSONObject(storedValue)
        val iv = json.getString("iv").fromBase64()
        val ciphertext = json.getString("ciphertext").fromBase64()
        val storedKeyAlias = json.optString("kid", "")
        val candidates = allowedKeyCandidates(storedKeyAlias)

        var lastFailure: Throwable? = null
        for ((alias, createIfMissing) in candidates) {
            val key = if (createIfMissing) {
                getOrCreateKey(alias)
            } else {
                getExistingKey(alias) ?: continue
            }
            val plainText = runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            }
            plainText.onSuccess { return it }
            plainText.onFailure { lastFailure = it }
        }

        throw lastFailure ?: IllegalStateException("No allowed protection key could decrypt stored value.")
    }

    fun isProtected(storedValue: String): Boolean {
        return runCatching {
            val json = JSONObject(storedValue)
            json.optInt("v", 0) == VERSION &&
                json.optString("alg") == TRANSFORMATION &&
                json.has("iv") &&
                json.has("ciphertext")
        }.getOrDefault(false)
    }

    fun needsReprotection(storedValue: String): Boolean {
        return runCatching {
            check(isProtected(storedValue)) { "Stored value is not protected." }
            JSONObject(storedValue).optString("kid", "") != keyAlias
        }.getOrDefault(true)
    }

    private fun allowedKeyCandidates(storedKeyAlias: String): List<Pair<String, Boolean>> {
        if (storedKeyAlias.isNotBlank()) {
            check(storedKeyAlias == keyAlias || storedKeyAlias in fallbackKeyAliases) {
                "Stored value was protected with an unexpected key alias."
            }
            return listOf(storedKeyAlias to (storedKeyAlias == keyAlias))
        }
        return listOf(keyAlias to true) + fallbackKeyAliases.map { it to false }
    }

    private fun getExistingKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun deleteKey(alias: String) {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(alias, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        val spec = builder.build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    companion object {
        private const val VERSION = 1
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PROFILE_KEY_ALIAS = "andface.clean_profile_stats.v2"
        private const val PROFILE_KEY_ALIAS_V1 = "andface.clean_profile_stats.v1"
        private const val AUDIT_LOG_KEY_ALIAS = "andface.security_audit.v2"
        private const val AUDIT_LOG_KEY_ALIAS_V1 = "andface.security_audit.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128

        fun enrollmentProfiles(): SecureProfileCodec {
            return SecureProfileCodec(
                keyAlias = PROFILE_KEY_ALIAS,
                fallbackKeyAliases = listOf(PROFILE_KEY_ALIAS_V1),
                regenerateKeyOnProtectFailure = true
            )
        }

        fun auditLog(): SecureProfileCodec {
            return SecureProfileCodec(
                keyAlias = AUDIT_LOG_KEY_ALIAS,
                fallbackKeyAliases = listOf(AUDIT_LOG_KEY_ALIAS_V1, PROFILE_KEY_ALIAS_V1),
                regenerateKeyOnProtectFailure = true
            )
        }
    }
}
