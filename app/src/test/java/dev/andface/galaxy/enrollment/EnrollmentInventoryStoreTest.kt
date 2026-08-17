package dev.andface.galaxy.enrollment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnrollmentInventoryStoreTest {
    @Test
    fun matchingInventoryHasNoMismatch() {
        val mismatch = EnrollmentInventoryStore.mismatchUserIds(
            expectedUserIds = setOf("USER_1", "USER_3"),
            actualUserIds = setOf("USER_1", "USER_3")
        )

        assertEquals(emptySet<String>(), mismatch)
    }

    @Test
    fun missingStoredProfileIsMismatch() {
        val mismatch = EnrollmentInventoryStore.mismatchUserIds(
            expectedUserIds = setOf("USER_1", "USER_2"),
            actualUserIds = setOf("USER_1")
        )

        assertEquals(setOf("USER_2"), mismatch)
    }

    @Test
    fun unexpectedStoredProfileIsMismatch() {
        val mismatch = EnrollmentInventoryStore.mismatchUserIds(
            expectedUserIds = setOf("USER_1"),
            actualUserIds = setOf("USER_1", "USER_3")
        )

        assertEquals(setOf("USER_3"), mismatch)
    }

    @Test
    fun unsupportedSlotIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            EnrollmentInventoryStore.sanitizeRegisteredUserIds(setOf("USER_4"))
        }
    }

    @Test
    fun normalClearCanRewriteInventory() {
        val allowed = EnrollmentInventoryStore.shouldRewriteAfterClear(
            userId = "USER_1",
            profileExistedBeforeClear = true,
            mismatchUserIdsBeforeClear = emptySet(),
            inventoryStorageFailedBeforeClear = false,
            remainingUserIdsAfterClear = setOf("USER_2")
        )

        assertEquals(true, allowed)
    }

    @Test
    fun missingExpectedProfileClearCanRewriteWhenItIsTheOnlyMismatch() {
        val allowed = EnrollmentInventoryStore.shouldRewriteAfterClear(
            userId = "USER_2",
            profileExistedBeforeClear = false,
            mismatchUserIdsBeforeClear = setOf("USER_2"),
            inventoryStorageFailedBeforeClear = false,
            remainingUserIdsAfterClear = setOf("USER_1")
        )

        assertEquals(true, allowed)
    }

    @Test
    fun wrongSlotClearDoesNotTrustRemainingMismatchedProfiles() {
        val allowed = EnrollmentInventoryStore.shouldRewriteAfterClear(
            userId = "USER_1",
            profileExistedBeforeClear = true,
            mismatchUserIdsBeforeClear = setOf("USER_3"),
            inventoryStorageFailedBeforeClear = false,
            remainingUserIdsAfterClear = setOf("USER_3")
        )

        assertEquals(false, allowed)
    }

    @Test
    fun corruptedInventoryCanRewriteOnlyAfterAllProfilesAreCleared() {
        val allowedWithRemainingProfile = EnrollmentInventoryStore.shouldRewriteAfterClear(
            userId = "USER_1",
            profileExistedBeforeClear = true,
            mismatchUserIdsBeforeClear = emptySet(),
            inventoryStorageFailedBeforeClear = true,
            remainingUserIdsAfterClear = setOf("USER_2")
        )
        val allowedWhenEmpty = EnrollmentInventoryStore.shouldRewriteAfterClear(
            userId = "USER_2",
            profileExistedBeforeClear = true,
            mismatchUserIdsBeforeClear = emptySet(),
            inventoryStorageFailedBeforeClear = true,
            remainingUserIdsAfterClear = emptySet()
        )

        assertEquals(false, allowedWithRemainingProfile)
        assertEquals(true, allowedWhenEmpty)
    }
}
