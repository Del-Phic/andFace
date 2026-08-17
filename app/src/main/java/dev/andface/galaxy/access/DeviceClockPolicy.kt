package dev.andface.galaxy.access

object DeviceClockPolicy {
    const val WALL_CLOCK_ROLLBACK_TOLERANCE_MS = 10L * 60L * 1000L

    fun hasSuspiciousRollback(
        nowWallMs: Long,
        lastTrustedWallMs: Long?,
        toleranceMs: Long = WALL_CLOCK_ROLLBACK_TOLERANCE_MS
    ): Boolean {
        val lastSeen = lastTrustedWallMs ?: return false
        return nowWallMs + toleranceMs < lastSeen
    }
}
