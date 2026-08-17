package dev.andface.galaxy.enrollment

object EnrollmentSecurityPolicy {
    const val REQUIRED_CLEAN_SAMPLE_COUNT = 45
    const val PROFILE_MAX_AGE_DAYS = 180L
    const val PROFILE_MAX_AGE_MS = PROFILE_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
    const val PROFILE_FUTURE_SKEW_MS = 24L * 60L * 60L * 1000L

    fun hasRequiredCleanSampleCount(sampleCount: Int): Boolean {
        return sampleCount >= REQUIRED_CLEAN_SAMPLE_COUNT
    }

    fun isProfileFresh(createdAtMs: Long, nowMs: Long): Boolean {
        if (createdAtMs <= 0L) return false
        if (createdAtMs > nowMs + PROFILE_FUTURE_SKEW_MS) return false
        return nowMs - createdAtMs <= PROFILE_MAX_AGE_MS
    }
}
