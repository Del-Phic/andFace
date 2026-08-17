package dev.andface.galaxy.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClockPolicyTest {
    @Test
    fun acceptsSmallWallClockAdjustments() {
        assertFalse(
            DeviceClockPolicy.hasSuspiciousRollback(
                nowWallMs = 1_000_000L,
                lastTrustedWallMs = 1_000_000L + DeviceClockPolicy.WALL_CLOCK_ROLLBACK_TOLERANCE_MS
            )
        )
    }

    @Test
    fun rejectsLargeWallClockRollback() {
        assertTrue(
            DeviceClockPolicy.hasSuspiciousRollback(
                nowWallMs = 1_000_000L,
                lastTrustedWallMs = 1_000_000L + DeviceClockPolicy.WALL_CLOCK_ROLLBACK_TOLERANCE_MS + 1L
            )
        )
    }

    @Test
    fun acceptsMissingLastTrustedTime() {
        assertFalse(
            DeviceClockPolicy.hasSuspiciousRollback(
                nowWallMs = 1_000_000L,
                lastTrustedWallMs = null
            )
        )
    }
}
