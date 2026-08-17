package dev.andface.galaxy.access

import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

object AssetIntegrityPolicy {
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02X".format(byte) }
    }

    fun matchesExpectedSha256(actual: String, expected: String): Boolean {
        val normalizedExpected = normalizeSha256(expected)
        if (normalizedExpected.isBlank()) return false
        return normalizeSha256(actual) == normalizedExpected
    }

    private fun normalizeSha256(value: String): String {
        return value
            .replace(":", "")
            .replace(" ", "")
            .uppercase(Locale.US)
    }
}
