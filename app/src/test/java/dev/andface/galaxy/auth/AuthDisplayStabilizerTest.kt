package dev.andface.galaxy.auth

import org.junit.Assert.*
import org.junit.Test

class AuthDisplayStabilizerTest {
    private var nowMs = 10_000L
    private val stabilizer = AuthDisplayStabilizer(clockMs = { nowMs })

    @Test
    fun continuousSameIdentityRequiresTwoSecondsBeforeCompletion() {
        val success = success()
        assertPending(stabilizer.stabilize(success))
        repeat(7) { nowMs += 250L; assertNull(stabilizer.stabilize(success)) }
        nowMs += 249L
        assertNull(stabilizer.stabilize(success))
        nowMs++
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun briefNoFaceWithinHalfSecondDoesNotRestartQualification() {
        val success = success()
        assertPending(stabilizer.stabilize(success))
        nowMs += 200L
        assertNull(stabilizer.stabilize(AuthResult.failed(FailureReason.NO_FACE)))
        nowMs += 200L
        assertNull(stabilizer.stabilize(success))
        repeat(7) { nowMs += 200L; assertNull(stabilizer.stabilize(success)) }
        nowMs += 200L
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun sparseSuccessfulFramesCannotQualify() {
        repeat(5) {
            assertPending(stabilizer.stabilize(success()))
            nowMs += 1000L
        }
    }

    @Test
    fun identityMismatchRestartsQualificationEvenWhenNearestCandidateIsTheSameUser() {
        val success = success()
        stabilizer.stabilize(success)
        repeat(6) { nowMs += 250L; stabilizer.stabilize(success) }
        nowMs += 30L
        stabilizer.stabilize(identityFailure("clean"))
        nowMs += 30L
        assertPending(stabilizer.stabilize(success))
    }

    @Test
    fun prolongedNoFaceRestartsQualification() {
        stabilizer.stabilize(success())
        nowMs += 501L
        assertEquals(FailureReason.NO_FACE, stabilizer.stabilize(AuthResult.failed(FailureReason.NO_FACE))?.failureReason)
        nowMs++
        assertPending(stabilizer.stabilize(success()))
    }

    @Test
    fun successfulFramesRefreshMetricsAtReadableIntervals() {
        completeSuccess()
        val updated = success(finalScore = 0.79)
        nowMs += 300L
        assertNull(stabilizer.stabilize(updated))
        nowMs += 500L
        assertSame(updated, stabilizer.stabilize(updated))
    }

    @Test
    fun noFaceDisplayRefreshDoesNotExtendLastRealSuccessDeadline() {
        val success = success()
        completeSuccess(success)
        nowMs += 800L
        assertSame(success, stabilizer.stabilize(AuthResult.failed(FailureReason.NO_FACE)))
        nowMs += 700L
        val noFace = AuthResult.failed(FailureReason.NO_FACE)
        assertSame(noFace, stabilizer.stabilize(noFace))
    }

    @Test
    fun successAfterAFrameGapMustQualifyAgain() {
        completeSuccess()
        nowMs += 1501L
        assertPending(stabilizer.stabilize(success()))
    }

    @Test
    fun alternatingTransientFailuresCannotKeepSuccessAlive() {
        completeSuccess()
        nowMs += 800L
        stabilizer.stabilize(AuthResult.failed(FailureReason.LOW_LIVENESS))
        nowMs += 700L
        val failure = AuthResult.failed(FailureReason.POOR_FACE_QUALITY)
        assertSame(failure, stabilizer.stabilize(failure))
    }

    @Test
    fun livenessGraceCannotDelayAnExpiredCompletedSuccess() {
        completeSuccess()
        nowMs += 1500L
        val failure = AuthResult.failed(FailureReason.LOW_LIVENESS)
        assertSame(failure, stabilizer.stabilize(failure))
    }

    @Test
    fun identityFailureImmediatelyRevokesCompletionInEveryOcclusionMode() {
        for (mode in listOf("clean", "lower", "glasses", "lower+glasses", "left_eye", "right_eye")) {
            stabilizer.reset()
            completeSuccess(success(occlusionSummary = mode))
            nowMs += 30L
            val mismatch = identityFailure(mode)
            assertSame("mode=$mode", mismatch, stabilizer.stabilize(mismatch))
            nowMs += 30L
            assertPending(stabilizer.stabilize(success(occlusionSummary = mode)))
        }
    }

    @Test
    fun sameUserSuccessfulOcclusionTransitionsPreserveCompletion() {
        completeSuccess()
        for (mode in listOf("lower", "glasses", "lower+glasses", "right_eye")) {
            nowMs += 800L
            val incoming = success(occlusionSummary = mode)
            assertSame(incoming, stabilizer.stabilize(incoming))
        }
    }

    @Test
    fun differentSuccessfulUserNeedsOwnConfirmation() {
        completeSuccess()
        nowMs += 30L
        val other = success(userId = "USER_2")
        val pending = stabilizer.stabilize(other)
        assertPending(pending)
        assertEquals("USER_2", pending?.matchedUserId)
        repeat(7) { nowMs += 250L; assertNull(stabilizer.stabilize(other)) }
        nowMs += 250L
        assertSame(other, stabilizer.stabilize(other))
    }

    @Test
    fun unsafeStatesAndSessionResetRevokeImmediately() {
        for (reason in listOf(FailureReason.MULTIPLE_FACES, FailureReason.EXCESSIVE_OCCLUSION,
            FailureReason.SECURE_STORAGE_ERROR, FailureReason.MODEL_NOT_READY,
            FailureReason.SESSION_RESET)) {
            stabilizer.reset()
            completeSuccess()
            nowMs += 30L
            val failure = AuthResult.failed(reason)
            assertSame(failure, stabilizer.stabilize(failure))
        }
    }

    @Test
    fun resetClearsCompletion() {
        completeSuccess()
        stabilizer.reset()
        assertPending(stabilizer.stabilize(success()))
    }

    private fun completeSuccess(result: AuthResult = success()) {
        assertPending(stabilizer.stabilize(result))
        repeat(7) { nowMs += 250L; assertNull(stabilizer.stabilize(result)) }
        nowMs += 250L
        assertSame(result, stabilizer.stabilize(result))
    }

    private fun assertPending(result: AuthResult?) {
        assertEquals(AuthDecision.FAILED, result?.decision)
        assertEquals(FailureReason.UNSTABLE_DECISION, result?.failureReason)
    }

    private fun identityFailure(mode: String) = AuthResult.failed(FailureReason.LOW_SCORE).copy(
        matchedUserId = "USER_1", occlusionSummary = mode
    )

    private fun success(userId: String = "USER_1", finalScore: Double = 0.80,
        occlusionSummary: String = "clean") = AuthResult.failed(FailureReason.NONE).copy(
        decision = AuthDecision.SUCCESS, matchedUserId = userId, registeredUserCount = 1,
        finalScore = finalScore, coverage = 0.80, margin = 0.30,
        livenessScore = 0.90, livenessPassed = true, occlusionSummary = occlusionSummary
    )
}
