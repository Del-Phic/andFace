package dev.andface.galaxy.auth

object EnrollmentCapturePolicy {
    const val WARMUP_MS = 1_000L
    const val SAMPLE_INTERVAL_MS = 180L
    const val MIN_COLLECTION_MS = 8_500L

    fun elapsedMs(startedAtMs: Long, nowMs: Long): Long {
        if (startedAtMs == Long.MIN_VALUE) return 0L
        return (nowMs - startedAtMs).coerceAtLeast(0L)
    }

    fun isWarmingUp(startedAtMs: Long, nowMs: Long): Boolean {
        return elapsedMs(startedAtMs, nowMs) < WARMUP_MS
    }

    fun canAcceptSample(lastAcceptedAtMs: Long, nowMs: Long): Boolean {
        return lastAcceptedAtMs == Long.MIN_VALUE || nowMs - lastAcceptedAtMs >= SAMPLE_INTERVAL_MS
    }

    fun hasMinimumCollectionDuration(startedAtMs: Long, nowMs: Long): Boolean {
        return elapsedMs(startedAtMs, nowMs) >= MIN_COLLECTION_MS
    }

    fun canComplete(
        startedAtMs: Long,
        acceptedSampleCount: Int,
        requiredSampleCount: Int,
        nowMs: Long
    ): Boolean {
        return acceptedSampleCount >= requiredSampleCount &&
            hasMinimumCollectionDuration(startedAtMs, nowMs)
    }

    fun isRetryableFrameFailure(failure: FailureReason): Boolean {
        return failure == FailureReason.POOR_FACE_QUALITY ||
            failure == FailureReason.LOW_COVERAGE ||
            failure == FailureReason.TOO_FEW_FEATURES ||
            failure == FailureReason.OCCLUDED_DURING_ENROLLMENT
    }

    fun isRetryableBaselineFailure(failure: FailureReason): Boolean {
        return failure == FailureReason.LOW_LIVENESS ||
            failure == FailureReason.UNSTABLE_ENROLLMENT
    }
}