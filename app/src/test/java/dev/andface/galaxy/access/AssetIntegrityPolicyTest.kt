package dev.andface.galaxy.access

import java.io.ByteArrayInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetIntegrityPolicyTest {
    @Test
    fun computesSha256AndAcceptsEquivalentFormatting() {
        val actual = AssetIntegrityPolicy.sha256Hex(ByteArrayInputStream("andface".toByteArray()))

        assertTrue(AssetIntegrityPolicy.matchesExpectedSha256(actual, actual.lowercase()))
        assertTrue(AssetIntegrityPolicy.matchesExpectedSha256(actual, actual.chunked(2).joinToString(":")))
    }

    @Test
    fun rejectsBlankOrMismatchedExpectedHash() {
        val actual = AssetIntegrityPolicy.sha256Hex(ByteArrayInputStream("andface".toByteArray()))

        assertFalse(AssetIntegrityPolicy.matchesExpectedSha256(actual, ""))
        assertFalse(AssetIntegrityPolicy.matchesExpectedSha256(actual, "00"))
    }
}
