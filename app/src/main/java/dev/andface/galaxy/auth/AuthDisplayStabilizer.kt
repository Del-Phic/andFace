package dev.andface.galaxy.auth

/**
 * Converts frame-level authentication results into the state shown by the access UI.
 *
 * A frame-level SUCCESS is intentionally not considered complete immediately. The same
 * identity must keep producing SUCCESS for [COMPLETION_CONFIRM_MS]. Once complete, the
 * successful result survives missing/low-quality frames for at most [successHoldMs]
 * since the last real success. Refreshing the display never renews that deadline. Strong identity disagreement,
 * a different successful identity, or an unsafe system/occlusion state releases the latch.
 */
internal class AuthDisplayStabilizer(
    private val clockMs: () -> Long,
    private val successHoldMs: Long = DEFAULT_SUCCESS_HOLD_MS,
    private val successRefreshMs: Long = DEFAULT_SUCCESS_REFRESH_MS,
    private val transientFailureFrameThreshold: Int = DEFAULT_TRANSIENT_FAILURE_FRAMES,
    private val identityFailureFrameThreshold: Int = DEFAULT_IDENTITY_FAILURE_FRAMES
) {
    private var displayedResult: AuthResult? = null
    private var lastSuccessAtMs: Long = Long.MIN_VALUE
    private var lastRenderedAtMs: Long = Long.MIN_VALUE
    private var pendingFailureReason: FailureReason? = null
    private var pendingFailureFrames: Int = 0
    private var pendingFailureStartedAtMs: Long = Long.MIN_VALUE

    private var qualifyingUserId: String? = null
    private var qualificationStartedAtMs: Long = Long.MIN_VALUE
    private var qualificationLastSuccessAtMs: Long = Long.MIN_VALUE
    private var qualificationSuccessFrames: Int = 0
    private var completedSuccess: AuthResult? = null
    private var completedIdentityFailureFrames: Int = 0

    fun stabilize(incoming: AuthResult): AuthResult? {
        val nowMs = clockMs()
        if (completedSuccess != null &&
            (nowMs < lastSuccessAtMs || nowMs - lastSuccessAtMs >= successHoldMs)
        ) {
            clearCompletion()
            displayedResult = null
            if (incoming.decision != AuthDecision.SUCCESS) {
                clearQualification()
                return accept(incoming, nowMs)
            }
        }
        if (incoming.decision == AuthDecision.SUCCESS) {
            return stabilizeSuccess(incoming, nowMs)
        }

        if (qualifyingUserId != null &&
            incoming.failureReason in QUALIFICATION_TRANSIENT_FAILURES &&
            qualificationLastSuccessAtMs != Long.MIN_VALUE &&
            nowMs - qualificationLastSuccessAtMs <= QUALIFICATION_TRANSIENT_GRACE_MS
        ) {
            // CameraX/MediaPipe can briefly emit NO_FACE, poor-quality, or liveness
            // failures while the same face remains in view. Keep the visible
            // "confirming" state, but never complete without another SUCCESS frame.
            return null
        }

        clearQualification()
        val completed = completedSuccess
        if (completed != null) {
            return stabilizeCompletedFailure(completed, incoming, nowMs)
        }

        return stabilizeUncompletedFailure(incoming, nowMs)
    }

    fun reset() {
        displayedResult = null
        lastSuccessAtMs = Long.MIN_VALUE
        lastRenderedAtMs = Long.MIN_VALUE
        completedSuccess = null
        completedIdentityFailureFrames = 0
        clearQualification()
        clearPendingFailure()
    }

    private fun stabilizeSuccess(incoming: AuthResult, nowMs: Long): AuthResult? {
        val userId = incoming.matchedUserId
        if (userId.isNullOrBlank()) {
            clearCompletion()
            clearQualification()
            return accept(
                incoming.copy(decision = AuthDecision.FAILED, failureReason = FailureReason.UNSTABLE_DECISION),
                nowMs
            )
        }

        val completed = completedSuccess
        if (completed != null) {
            if (completed.matchedUserId != userId) {
                clearCompletion()
                beginQualification(userId, nowMs)
                return accept(incoming.asPendingCompletion(), nowMs)
            }

            completedSuccess = incoming
            completedIdentityFailureFrames = 0
            lastSuccessAtMs = nowMs
            clearPendingFailure()
            if (lastRenderedAtMs != Long.MIN_VALUE && nowMs - lastRenderedAtMs < successRefreshMs) {
                return null
            }
            return accept(incoming, nowMs)
        }

        if (qualifyingUserId != userId) {
            beginQualification(userId, nowMs)
            return accept(incoming.asPendingCompletion(), nowMs)
        }

        if (qualificationLastSuccessAtMs == Long.MIN_VALUE ||
            nowMs - qualificationLastSuccessAtMs > QUALIFICATION_TRANSIENT_GRACE_MS
        ) {
            beginQualification(userId, nowMs)
            return accept(incoming.asPendingCompletion(), nowMs)
        }

        qualificationLastSuccessAtMs = nowMs
        qualificationSuccessFrames += 1

        if (nowMs - qualificationStartedAtMs < COMPLETION_CONFIRM_MS ||
            qualificationSuccessFrames < MIN_QUALIFICATION_SUCCESS_FRAMES
        ) {
            return null
        }

        completedSuccess = incoming
        completedIdentityFailureFrames = 0
        clearQualification()
        lastSuccessAtMs = nowMs
        clearPendingFailure()
        return accept(incoming, nowMs)
    }

    private fun stabilizeCompletedFailure(
        completed: AuthResult,
        incoming: AuthResult,
        nowMs: Long
    ): AuthResult? {
        if (incoming.failureReason in IMMEDIATE_COMPLETION_REVOCATIONS) {
            clearCompletion()
            return accept(incoming, nowMs)
        }

        // An identity rejection is not camera jitter. Never display the
        // previous user's success over a current identity mismatch.
        if (incoming.failureReason in IDENTITY_FAILURES) {
            clearCompletion()
            return accept(incoming, nowMs)
        }

        if (lastRenderedAtMs != Long.MIN_VALUE && nowMs - lastRenderedAtMs < successRefreshMs) {
            return null
        }
        return accept(completed, nowMs)
    }

    private fun stabilizeUncompletedFailure(incoming: AuthResult, nowMs: Long): AuthResult? {
        if (incoming.failureReason in IMMEDIATE_FAILURES) {
            return accept(incoming, nowMs)
        }

        val heldSuccess = displayedResult?.takeIf { it.decision == AuthDecision.SUCCESS }
        val isLivenessFailure = incoming.failureReason == FailureReason.LOW_LIVENESS
        if (heldSuccess != null || isLivenessFailure) {
            if (pendingFailureReason == incoming.failureReason) {
                pendingFailureFrames += 1
            } else {
                pendingFailureReason = incoming.failureReason
                pendingFailureFrames = 1
                pendingFailureStartedAtMs = nowMs
            }
            val requiredFrames = if (incoming.failureReason in TRANSIENT_FAILURES) {
                transientFailureFrameThreshold
            } else {
                identityFailureFrameThreshold
            }
            val pendingDurationMs = nowMs - pendingFailureStartedAtMs
            val withinGeneralSuccessHold = lastSuccessAtMs != Long.MIN_VALUE &&
                nowMs - lastSuccessAtMs <= successHoldMs
            val shouldKeepSuccess = if (isLivenessFailure) {
                pendingFailureFrames < requiredFrames || pendingDurationMs < DEFAULT_LIVENESS_FAILURE_GRACE_MS
            } else {
                withinGeneralSuccessHold && pendingFailureFrames < requiredFrames
            }
            if (shouldKeepSuccess) return null
        }

        return accept(incoming, nowMs)
    }

    private fun beginQualification(userId: String, nowMs: Long) {
        qualifyingUserId = userId
        qualificationStartedAtMs = nowMs
        qualificationLastSuccessAtMs = nowMs
        qualificationSuccessFrames = 1
        clearPendingFailure()
    }

    private fun clearQualification() {
        qualifyingUserId = null
        qualificationStartedAtMs = Long.MIN_VALUE
        qualificationLastSuccessAtMs = Long.MIN_VALUE
        qualificationSuccessFrames = 0
    }

    private fun clearCompletion() {
        completedSuccess = null
        completedIdentityFailureFrames = 0
        lastSuccessAtMs = Long.MIN_VALUE
        clearPendingFailure()
    }

    private fun accept(result: AuthResult, nowMs: Long): AuthResult {
        displayedResult = result
        lastRenderedAtMs = nowMs
        if (result.decision != AuthDecision.SUCCESS) {
            lastSuccessAtMs = Long.MIN_VALUE
        }
        clearPendingFailure()
        return result
    }

    private fun AuthResult.asPendingCompletion(): AuthResult {
        return copy(
            decision = AuthDecision.FAILED,
            failureReason = FailureReason.UNSTABLE_DECISION,
            stableFrameCount = 0
        )
    }

    private fun clearPendingFailure() {
        pendingFailureReason = null
        pendingFailureFrames = 0
        pendingFailureStartedAtMs = Long.MIN_VALUE
    }

    companion object {
        private const val COMPLETION_CONFIRM_MS = 2_000L
        private const val QUALIFICATION_TRANSIENT_GRACE_MS = 500L
        private const val MIN_QUALIFICATION_SUCCESS_FRAMES = 3
        private const val DEFAULT_SUCCESS_HOLD_MS = 1_500L
        private const val DEFAULT_SUCCESS_REFRESH_MS = 800L
        private const val DEFAULT_LIVENESS_FAILURE_GRACE_MS = 2_500L
        private const val DEFAULT_TRANSIENT_FAILURE_FRAMES = 8
        // At ~30 fps, three frames are only about 0.1 s and are routinely produced
        // while the authenticated person turns away. Require a sustained identity
        // disagreement before interpreting it as a different person.
        private const val DEFAULT_IDENTITY_FAILURE_FRAMES = 15

        private val IDENTITY_FAILURES = setOf(
            FailureReason.LOW_SCORE,
            FailureReason.LOW_MARGIN,
            FailureReason.LOW_IDENTITY_COVERAGE,
            FailureReason.LOW_IDENTITY_SUPPORT,
            FailureReason.LOW_GLOBAL_CONSISTENCY
        )

        private val TRANSIENT_FAILURES = setOf(
            FailureReason.POOR_FACE_QUALITY,
            FailureReason.TOO_FEW_FEATURES,
            FailureReason.LOW_COVERAGE,
            FailureReason.LOW_LIVENESS,
            FailureReason.UNSTABLE_DECISION
        )

        private val QUALIFICATION_TRANSIENT_FAILURES = TRANSIENT_FAILURES + FailureReason.NO_FACE

        private val IMMEDIATE_COMPLETION_REVOCATIONS = setOf(
            FailureReason.NO_ENROLLMENT,
            FailureReason.PROFILE_EXPIRED,
            FailureReason.MULTIPLE_FACES,
            FailureReason.MODEL_NOT_READY,
            FailureReason.DEVICE_NOT_SECURE,
            FailureReason.DEVICE_LOCKED,
            FailureReason.WINDOW_NOT_SECURE,
            FailureReason.KIOSK_MODE_REQUIRED,
            FailureReason.RUNTIME_INTEGRITY_RISK,
            FailureReason.DEPLOYMENT_SIGNING_REQUIRED,
            FailureReason.SECURE_STORAGE_ERROR,
            FailureReason.EXCESSIVE_OCCLUSION,
            FailureReason.OCCLUSION_HINT_MISMATCH,
            FailureReason.SESSION_RESET
        )

        private val IMMEDIATE_FAILURES = FailureReason.entries.toSet() -
            TRANSIENT_FAILURES -
            IDENTITY_FAILURES
    }
}
