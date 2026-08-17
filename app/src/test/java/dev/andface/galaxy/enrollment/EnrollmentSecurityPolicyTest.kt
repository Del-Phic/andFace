package dev.andface.galaxy.enrollment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentSecurityPolicyTest {
    @Test
    fun profileFreshnessAcceptsRecentEnrollment() {
        val nowMs = 1_000_000_000_000L
        val createdAtMs = nowMs - EnrollmentSecurityPolicy.PROFILE_MAX_AGE_MS + 1_000L

        assertTrue(EnrollmentSecurityPolicy.isProfileFresh(createdAtMs, nowMs))
    }

    @Test
    fun profileFreshnessRejectsExpiredEnrollment() {
        val nowMs = 1_000_000_000_000L
        val createdAtMs = nowMs - EnrollmentSecurityPolicy.PROFILE_MAX_AGE_MS - 1_000L

        assertFalse(EnrollmentSecurityPolicy.isProfileFresh(createdAtMs, nowMs))
    }

    @Test
    fun profileFreshnessRejectsImplausibleFutureEnrollment() {
        val nowMs = 1_000_000_000_000L
        val createdAtMs = nowMs + EnrollmentSecurityPolicy.PROFILE_FUTURE_SKEW_MS + 1_000L

        assertFalse(EnrollmentSecurityPolicy.isProfileFresh(createdAtMs, nowMs))
    }
}
