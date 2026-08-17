package dev.andface.galaxy.audit

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityAuditPrivacyContractTest {
    @Test
    fun auditLoggerDoesNotPersistBiometricTemplatesOrFrameData() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/audit/SecurityAuditLogger.kt")
        val persistedKeys = Regex("""\.put\("([^"]+)"""")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .toSet()

        val forbiddenKeys = setOf(
            "landmark",
            "landmarks",
            "rawFrame",
            "rawFeatureFrame",
            "feature",
            "features",
            "featureVector",
            "featureVectors",
            "values",
            "means",
            "sigmas",
            "covariance",
            "cameraFrame",
            "faceImage",
            "bitmap",
            "imageBytes",
            "fuzzyParameters",
            "mahalanobisCovariance"
        )
        val leakedKeys = persistedKeys.intersect(forbiddenKeys)

        assertTrue(
            "Security audit log must not persist biometric templates or frame data: ${leakedKeys.joinToString()}",
            leakedKeys.isEmpty()
        )
    }

    @Test
    fun auditLoggerDoesNotReintroduceRemovedPhysicalControlEvents() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/audit/SecurityAuditLogger.kt")
        val removedEventTerms = listOf(
            "AccessGrantController",
            "ACCESS_GRANT_CONSUMED",
            "ACCESS_GRANT_ISSUED",
            "DOOR_OPEN_DENIED",
            "DoorAccessAuthorizer",
            "DoorControllerIdPolicy",
            "controllerId"
        )
        val found = removedEventTerms.filter(source::contains)

        assertTrue(
            "Security audit log still references removed physical-control events: ${found.joinToString()}",
            found.isEmpty()
        )
    }


    @Test
    fun auditLoggerIsNoOpForDemoFaceAuthenticationApp() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/audit/SecurityAuditLogger.kt")

        assertTrue(source.contains("fun isHealthy(): Boolean = true"))
        assertTrue(source.contains("fun latestEventWallClockMs(): Long? = null"))
        assertTrue(source.contains("private fun append(eventType: String, fields: JSONObject): Boolean = true"))
    }
    @Test
    fun secureCodecRegeneratesUnusableKeystoreKeyWhenProtectFails() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/SecureProfileCodec.kt")

        assertTrue(source.contains("regenerateKeyOnProtectFailure = true"))
        assertTrue(source.contains("if (!regenerateKeyOnProtectFailure) throw error"))
        assertTrue(source.contains("deleteKey(keyAlias)"))
        assertTrue(source.contains("protectWithKey(plainJson, getOrCreateKey(keyAlias))"))
        assertTrue(source.contains("keyStore.deleteEntry(alias)"))
    }
    @Test
    fun enrollmentProfileCodecRegeneratesAnUnusableKeystoreKey() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/SecureProfileCodec.kt")
        val profileFactory = source.substringAfter("fun enrollmentProfiles()").substringBefore("fun auditLog()")

        assertTrue(profileFactory.contains("keyAlias = PROFILE_KEY_ALIAS"))
        assertTrue(profileFactory.contains("regenerateKeyOnProtectFailure = true"))
    }

    private fun readProjectFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        )
        val file = candidates.firstOrNull { it.isFile }
        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText()
    }
}
