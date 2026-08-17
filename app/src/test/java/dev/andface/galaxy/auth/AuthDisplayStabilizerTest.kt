package dev.andface.galaxy.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AuthDisplayStabilizerTest {
    private var nowMs = 10_000L
    private val stabilizer = AuthDisplayStabilizer(
        clockMs = { nowMs },
        successHoldMs = 1_500L,
        successRefreshMs = 800L,
        transientFailureFrameThreshold = 8,
        identityFailureFrameThreshold = 3
    )

    @Test
    fun successBecomesCompletedOnlyAfterSameIdentityHoldsForTwoSeconds() {
        val success = success()

        val pending = stabilizer.stabilize(success)
        assertEquals(AuthDecision.FAILED, pending?.decision)
        assertEquals(FailureReason.UNSTABLE_DECISION, pending?.failureReason)

        nowMs += 1_999L
        assertNull(stabilizer.stabilize(success))
        nowMs += 1L
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun briefNoFaceDoesNotRestartCompletionQualification() {
        val success = success()
        stabilizer.stabilize(success)
        nowMs += 1_500L
        assertNull(stabilizer.stabilize(AuthResult.failed(FailureReason.NO_FACE)))

        nowMs += 500L
        assertNull(stabilizer.stabilize(success))
        nowMs += 10L
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun differentIdentityFailureStillRestartsCompletionQualification() {
        val success = success()
        stabilizer.stabilize(success)
        nowMs += 1_500L
        stabilizer.stabilize(
            identityFailure(occlusionSummary = "clean").copy(matchedUserId = "USER_2")
        )

        nowMs += 10L
        val restarted = stabilizer.stabilize(success)
        assertEquals(FailureReason.UNSTABLE_DECISION, restarted?.failureReason)
        nowMs += 1_999L
        assertNull(stabilizer.stabilize(success))
        nowMs += 1L
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun briefSameIdentityNearMissDoesNotRestartCompletionQualification() {
        val success = success()
        stabilizer.stabilize(success)
        nowMs += 1_000L
        assertNull(stabilizer.stabilize(identityFailure(occlusionSummary = "lower")))
        nowMs += 1_000L
        assertNull(stabilizer.stabilize(success))
        nowMs += 10L
        assertSame(success, stabilizer.stabilize(success))
    }

    @Test
    fun prolongedNoFaceRestartsCompletionQualification() {
        val success = success()
        stabilizer.stabilize(success)
        nowMs += 2_501L
        stabilizer.stabilize(AuthResult.failed(FailureReason.NO_FACE))

        nowMs += 10L
        val restarted = stabilizer.stabilize(success)
        assertEquals(FailureReason.UNSTABLE_DECISION, restarted?.failureReason)
    }

    @Test
    fun completedSuccessRefreshesMetricsAtReadableIntervals() {
        completeSuccess()
        val updated = success(finalScore = 0.79)

        nowMs += 300L
        assertNull(stabilizer.stabilize(updated))
        nowMs += 500L
        assertSame(updated, stabilizer.stabilize(updated))
    }

    @Test
    fun completedSuccessStaysLatchedAcrossNoFaceUntilAnotherIdentityAppears() {
        val completed = success()
        completeSuccess(completed)
        val noFace = AuthResult.failed(FailureReason.NO_FACE)

        repeat(7) {
            nowMs += 100L
            assertNull(stabilizer.stabilize(noFace))
        }
        nowMs += 100L
        assertSame(completed, stabilizer.stabilize(noFace))

        repeat(7) {
            nowMs += 100L
            assertNull(stabilizer.stabilize(noFace))
        }
        nowMs += 100L
        assertSame(completed, stabilizer.stabilize(noFace))
    }

    @Test
    fun productionLatchSurvivesBriefIdentityDropWhilePersonLeavesFrame() {
        val production = AuthDisplayStabilizer(clockMs = { nowMs })
        val completed = success()
        assertEquals(FailureReason.UNSTABLE_DECISION, production.stabilize(completed)?.failureReason)
        nowMs += 1_000L
        assertNull(production.stabilize(completed))
        nowMs += 1_000L
        assertSame(completed, production.stabilize(completed))

        repeat(8) {
            nowMs += 35L
            assertNull(production.stabilize(identityFailure(occlusionSummary = "clean")))
        }
        val noFace = AuthResult.failed(FailureReason.NO_FACE)
        nowMs += 520L
        assertSame(completed, production.stabilize(noFace))
    }

    @Test
    fun cleanIdentityMismatchReleasesCompletedSuccessAfterThreeFrames() {
        completeSuccess()
        val mismatch = identityFailure(occlusionSummary = "clean")

        repeat(2) {
            nowMs += 30L
            assertNull(stabilizer.stabilize(mismatch))
        }
        nowMs += 30L
        assertSame(mismatch, stabilizer.stabilize(mismatch))
    }

    @Test
    fun maskAndEyePatchTransitionsUseEightFramesBeforeReleasingCompletedSuccess() {
        completeSuccess()
        val maskedMismatch = identityFailure(occlusionSummary = "lower")

        repeat(7) {
            nowMs += 30L
            stabilizer.stabilize(maskedMismatch)
        }
        nowMs += 30L
        assertSame(maskedMismatch, stabilizer.stabilize(maskedMismatch))

        completeSuccess(success(occlusionSummary = "left_eye"))
        val patchedMismatch = identityFailure(occlusionSummary = "left_eye")
        repeat(7) {
            nowMs += 30L
            stabilizer.stabilize(patchedMismatch)
        }
        nowMs += 30L
        assertSame(patchedMismatch, stabilizer.stabilize(patchedMismatch))
    }

    @Test
    fun sameCompletedUserCanMoveBetweenCleanMaskAndEyePatchModes() {
        completeSuccess()

        nowMs += 800L
        val masked = success(occlusionSummary = "lower")
        assertSame(masked, stabilizer.stabilize(masked))

        nowMs += 800L
        val patched = success(occlusionSummary = "right_eye")
        assertSame(patched, stabilizer.stabilize(patched))
    }

    @Test
    fun differentSuccessfulUserClearsOldCompletionAndNeedsOwnTwoSeconds() {
        completeSuccess(success(userId = "USER_1"))
        val other = success(userId = "USER_2")

        nowMs += 30L
        val pending = stabilizer.stabilize(other)
        assertEquals(AuthDecision.FAILED, pending?.decision)
        assertEquals("USER_2", pending?.matchedUserId)
        assertEquals(FailureReason.UNSTABLE_DECISION, pending?.failureReason)

        nowMs += 1_999L
        assertNull(stabilizer.stabilize(other))
        nowMs += 1L
        assertSame(other, stabilizer.stabilize(other))
    }

    @Test
    fun unsafeStatesReleaseCompletionImmediately() {
        completeSuccess()

        nowMs += 30L
        val multipleFaces = AuthResult.failed(FailureReason.MULTIPLE_FACES)
        assertSame(multipleFaces, stabilizer.stabilize(multipleFaces))

        completeSuccess()
        nowMs += 30L
        val excessiveOcclusion = AuthResult.failed(FailureReason.EXCESSIVE_OCCLUSION)
        assertSame(excessiveOcclusion, stabilizer.stabilize(excessiveOcclusion))

        completeSuccess()
        nowMs += 30L
        val security = AuthResult.failed(FailureReason.SECURE_STORAGE_ERROR)
        assertSame(security, stabilizer.stabilize(security))
    }

    @Test
    fun lowLivenessBeforeCompletionStillReceivesGrace() {
        val failure = AuthResult.failed(FailureReason.LOW_LIVENESS)

        repeat(25) {
            nowMs += 100L
            assertNull(stabilizer.stabilize(failure))
        }
        nowMs += 99L
        assertNull(stabilizer.stabilize(failure))
        nowMs += 1L
        assertSame(failure, stabilizer.stabilize(failure))
    }

    @Test
    fun resetClearsCompletedSuccess() {
        completeSuccess()
        stabilizer.reset()

        nowMs += 800L
        val noFace = AuthResult.failed(FailureReason.NO_FACE)
        assertSame(noFace, stabilizer.stabilize(noFace))
    }

    private fun completeSuccess(result: AuthResult = success()) {
        val pending = stabilizer.stabilize(result)
        assertEquals(FailureReason.UNSTABLE_DECISION, pending?.failureReason)
        nowMs += 1_000L
        assertNull(stabilizer.stabilize(result))
        nowMs += 1_000L
        assertSame(result, stabilizer.stabilize(result))
    }

    private fun identityFailure(occlusionSummary: String): AuthResult {
        return AuthResult.failed(FailureReason.LOW_SCORE).copy(
            matchedUserId = "USER_1",
            registeredUserCount = 1,
            coverage = 0.80,
            observableCount = 10,
            occlusionSummary = occlusionSummary
        )
    }

    private fun success(
        userId: String = "USER_1",
        finalScore: Double = 0.80,
        occlusionSummary: String = "clean"
    ): AuthResult {
        return AuthResult.failed(FailureReason.NONE).copy(
            decision = AuthDecision.SUCCESS,
            matchedUserId = userId,
            registeredUserCount = 1,
            finalScore = finalScore,
            coverage = 0.80,
            margin = 0.30,
            livenessScore = 0.90,
            livenessPassed = true,
            occlusionSummary = occlusionSummary
        )
    }
}
