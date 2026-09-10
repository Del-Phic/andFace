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
    fun auditLoggerUsesPersistentJournal() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/audit/SecurityAuditLogger.kt")
        assertTrue(source.contains("journal.append(eventType, fields.withAuditMetadata())"))
        assertTrue(source.contains("secureCodec.protect(events.toString())"))
        assertTrue(source.contains("journal.isHealthy()"))
    }

    @Test
    fun storageErrorsCannotSilentlyDeleteSharedKeystoreKeys() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/SecureProfileCodec.kt")
        assertTrue(!source.contains("deleteEntry("))
        assertTrue(!source.contains("regenerateKeyOnProtectFailure"))
        assertTrue(source.contains("return protectWithKey(plainJson, getOrCreateKey(keyAlias))"))
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
