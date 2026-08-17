package dev.andface.galaxy.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLockoutStoreTest {
    @Test
    fun sameBootUsesMonotonicRemainingTime() {
        val remainingMs = AccessLockoutStore.restoredRemainingMs(
            nowElapsedMs = 10_000L,
            nowWallMs = 1_010_000L,
            storedUntilElapsedMs = 30_000L,
            storedUntilWallMs = 1_030_000L,
            storedBootMarkerMs = 1_000_000L
        )

        assertEquals(20_000L, remainingMs)
    }

    @Test
    fun largeForwardWallClockChangeDoesNotClearStoredLockout() {
        val remainingMs = AccessLockoutStore.restoredRemainingMs(
            nowElapsedMs = 11_000L,
            nowWallMs = 2_000_000L,
            storedUntilElapsedMs = 30_000L,
            storedUntilWallMs = 1_030_000L,
            storedBootMarkerMs = 1_000_000L
        )

        assertEquals(30_000L, remainingMs)
    }

    @Test
    fun uncertainBootWithFutureWallDeadlineRestoresRemainingWallTime() {
        val remainingMs = AccessLockoutStore.restoredRemainingMs(
            nowElapsedMs = 3_000L,
            nowWallMs = 1_020_000L,
            storedUntilElapsedMs = 30_000L,
            storedUntilWallMs = 1_045_000L,
            storedBootMarkerMs = 500_000L
        )

        assertEquals(25_000L, remainingMs)
    }

    @Test
    fun sameBootExpiredLockoutCanClear() {
        val remainingMs = AccessLockoutStore.restoredRemainingMs(
            nowElapsedMs = 40_000L,
            nowWallMs = 1_040_000L,
            storedUntilElapsedMs = 30_000L,
            storedUntilWallMs = 1_030_000L,
            storedBootMarkerMs = 1_000_000L
        )

        assertTrue("expected expired same-boot lockout to clear", remainingMs <= 0L)
    }

    @Test
    fun expiredLockoutClearFailureReturnsStorageError() {
        val result = AccessLockoutStore.restoreResultForRemaining(
            remainingMs = 0L,
            nowElapsedMs = 40_000L,
            clearStored = { false }
        )

        assertEquals(null, result.untilElapsedMs)
        assertTrue("expected storage error when expired lockout cannot be removed", result.storageError)
    }

    @Test
    fun expiredLockoutClearSuccessRestoresNoActiveLockout() {
        val result = AccessLockoutStore.restoreResultForRemaining(
            remainingMs = 0L,
            nowElapsedMs = 40_000L,
            clearStored = { true }
        )

        assertEquals(null, result.untilElapsedMs)
        assertEquals(false, result.storageError)
    }

    @Test
    fun activeRiskyFailureWindowRestoresFailureCount() {
        val result = AccessLockoutStore.restoredRiskyFailureState(
            nowElapsedMs = 15_000L,
            nowWallMs = 1_015_000L,
            storedCount = 5,
            storedWindowStartElapsedMs = 10_000L,
            storedLastCountedElapsedMs = 14_000L,
            storedWindowExpiresWallMs = 1_022_000L,
            storedBootMarkerMs = 1_000_000L,
            clearStored = { false }
        )

        assertEquals(false, result.storageError)
        assertEquals(5, result.state?.count)
        assertEquals(10_000L, result.state?.windowStartMs)
        assertEquals(14_000L, result.state?.lastCountedMs)
    }

    @Test
    fun expiredRiskyFailureWindowClearsStoredState() {
        var cleared = false

        val result = AccessLockoutStore.restoredRiskyFailureState(
            nowElapsedMs = 30_000L,
            nowWallMs = 1_030_000L,
            storedCount = 5,
            storedWindowStartElapsedMs = 10_000L,
            storedLastCountedElapsedMs = 14_000L,
            storedWindowExpiresWallMs = 1_022_000L,
            storedBootMarkerMs = 1_000_000L,
            clearStored = {
                cleared = true
                true
            }
        )

        assertEquals(false, result.storageError)
        assertEquals(null, result.state)
        assertTrue("expected expired risky failure state to be cleared", cleared)
    }
}
