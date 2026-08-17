package dev.andface.galaxy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentCapturePolicyTest {
    @Test
    fun enrollmentFramesAreIgnoredDuringWarmup() {
        assertTrue(EnrollmentCapturePolicy.isWarmingUp(startedAtMs = 1_000L, nowMs = 1_999L))
        assertFalse(EnrollmentCapturePolicy.isWarmingUp(startedAtMs = 1_000L, nowMs = 2_000L))
    }

    @Test
    fun enrollmentSamplesNeedSpacingBetweenAcceptedFrames() {
        assertTrue(EnrollmentCapturePolicy.canAcceptSample(lastAcceptedAtMs = Long.MIN_VALUE, nowMs = 5_000L))
        assertFalse(EnrollmentCapturePolicy.canAcceptSample(lastAcceptedAtMs = 5_000L, nowMs = 5_179L))
        assertTrue(EnrollmentCapturePolicy.canAcceptSample(lastAcceptedAtMs = 5_000L, nowMs = 5_180L))
    }

    @Test
    fun enrollmentRequiresMinimumCollectionDuration() {
        assertFalse(EnrollmentCapturePolicy.hasMinimumCollectionDuration(startedAtMs = 1_000L, nowMs = 9_499L))
        assertTrue(EnrollmentCapturePolicy.hasMinimumCollectionDuration(startedAtMs = 1_000L, nowMs = 9_500L))
    }

    @Test
    fun enrollmentCompletesOnlyAfterRequiredSamplesAndMinimumDuration() {
        val startedAtMs = 1_000L
        val requiredSamples = 45

        assertFalse(
            EnrollmentCapturePolicy.canComplete(
                startedAtMs = startedAtMs,
                acceptedSampleCount = requiredSamples - 1,
                requiredSampleCount = requiredSamples,
                nowMs = startedAtMs + EnrollmentCapturePolicy.MIN_COLLECTION_MS + 1L
            )
        )
        assertFalse(
            EnrollmentCapturePolicy.canComplete(
                startedAtMs = startedAtMs,
                acceptedSampleCount = requiredSamples,
                requiredSampleCount = requiredSamples,
                nowMs = startedAtMs + EnrollmentCapturePolicy.MIN_COLLECTION_MS - 1L
            )
        )
        assertTrue(
            EnrollmentCapturePolicy.canComplete(
                startedAtMs = startedAtMs,
                acceptedSampleCount = requiredSamples,
                requiredSampleCount = requiredSamples,
                nowMs = startedAtMs + EnrollmentCapturePolicy.MIN_COLLECTION_MS
            )
        )
    }

    @Test
    fun fortyFiveSpacedAcceptedSamplesCanCompleteEnrollmentWindow() {
        val startedAtMs = 1_000L
        val requiredSamples = 45
        var lastAcceptedAtMs = Long.MIN_VALUE
        var acceptedCount = 0
        var nowMs = startedAtMs + EnrollmentCapturePolicy.WARMUP_MS

        while (acceptedCount < requiredSamples) {
            if (EnrollmentCapturePolicy.canAcceptSample(lastAcceptedAtMs, nowMs)) {
                acceptedCount += 1
                lastAcceptedAtMs = nowMs
            }
            nowMs += EnrollmentCapturePolicy.SAMPLE_INTERVAL_MS
        }

        assertTrue(acceptedCount == requiredSamples)
        assertTrue(EnrollmentCapturePolicy.canComplete(startedAtMs, acceptedCount, requiredSamples, lastAcceptedAtMs))
        assertTrue(
            EnrollmentCapturePolicy.canComplete(
                startedAtMs,
                acceptedCount,
                requiredSamples,
                startedAtMs + EnrollmentCapturePolicy.MIN_COLLECTION_MS
            )
        )
    }
    @Test
    fun temporaryCameraQualityFailuresAreSkippedInsteadOfEndingEnrollment() {
        assertTrue(EnrollmentCapturePolicy.isRetryableFrameFailure(FailureReason.POOR_FACE_QUALITY))
        assertTrue(EnrollmentCapturePolicy.isRetryableFrameFailure(FailureReason.LOW_COVERAGE))
        assertTrue(EnrollmentCapturePolicy.isRetryableFrameFailure(FailureReason.TOO_FEW_FEATURES))
        assertTrue(EnrollmentCapturePolicy.isRetryableFrameFailure(FailureReason.OCCLUDED_DURING_ENROLLMENT))
        assertFalse(EnrollmentCapturePolicy.isRetryableFrameFailure(FailureReason.LOW_LIVENESS))
    }

    @Test
    fun baselineMotionFailuresKeepEnrollmentSessionOpen() {
        assertTrue(EnrollmentCapturePolicy.isRetryableBaselineFailure(FailureReason.LOW_LIVENESS))
        assertTrue(EnrollmentCapturePolicy.isRetryableBaselineFailure(FailureReason.UNSTABLE_ENROLLMENT))
        assertFalse(EnrollmentCapturePolicy.isRetryableBaselineFailure(FailureReason.OCCLUDED_DURING_ENROLLMENT))
        assertFalse(EnrollmentCapturePolicy.isRetryableBaselineFailure(FailureReason.SECURE_STORAGE_ERROR))
    }
}