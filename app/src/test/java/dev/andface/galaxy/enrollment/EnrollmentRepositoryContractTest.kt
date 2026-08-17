package dev.andface.galaxy.enrollment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnrollmentRepositoryContractTest {
    @Test
    fun repositoryPersistsOnlyCleanBaselineStatistics() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/EnrollmentRepository.kt")
        val persistedKeys = Regex("""\.put\("([^"]+)"""")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .toSet()

        assertTrue(persistedKeys.containsAll(setOf("userId", "createdAtMs", "modelSha256", "policyVersion", "sampleCount")))
        assertTrue(persistedKeys.containsAll(setOf("means", "sigmas", "covariance")))

        val forbiddenKeys = setOf(
            "rawSamples",
            "rawFrame",
            "landmarks",
            "faceImage",
            "maskTemplate",
            "glassesTemplate",
            "eyePatchTemplate",
            "occludedTemplate",
            "reconstructedRegion"
        )
        assertTrue(
            "Repository must not persist raw samples or occlusion templates: ${persistedKeys.intersect(forbiddenKeys)}",
            persistedKeys.intersect(forbiddenKeys).isEmpty()
        )
    }

    @Test
    fun repositoryRejectsUnprotectedLegacyPlaintextProfiles() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/EnrollmentRepository.kt")

        assertTrue(source.contains("secureProfileCodec.isProtected(rawJson)"))
        assertFalse(source.contains("fallback") && source.contains("plaintext"))
    }


    @Test
    fun repositoryTreatsObsoleteProtectedProfilesAsReEnrollmentInsteadOfStorageFailure() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/enrollment/EnrollmentRepository.kt")

        assertTrue(source.contains("isReEnrollmentRequiredProfile"))
        assertTrue(source.contains("preferences.edit().remove(keyForUser(userId)).commit()"))
        assertTrue(source.contains("return ProfileReadResult(profile = null, storageError = false)"))
        assertTrue(source.contains("policyVersion") && source.contains("FeatureType.COUNT"))
    }
    @Test
    fun completedEnrollmentPersistsTheCurrentProfileInventory() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")
        val inventorySave = source.substringAfter("private fun saveProfileInventoryFromStorage()")
            .substringBefore("private fun reconcileProfileInventoryAfterClear")

        assertTrue(inventorySave.contains("profileInventoryStore.replaceWith(storedProfileUserIds)"))
        assertTrue(inventorySave.contains("return !profileInventoryFailed"))
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
