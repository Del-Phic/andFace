package dev.andface.galaxy.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalDecisionGateTest {
    @Test
    fun cleanFaceRequiresFiveStableFramesBeforeSuccess() {
        val gate = TemporalDecisionGate()

        val results = (0 until 5).map {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        assertEquals(5, results.first().requiredFrames)
        assertEquals(1, results.first().stableFrames)
        assertTrue(results.take(4).all { !it.passed })
        assertTrue(results.last().passed)
    }

    @Test
    fun occludedFaceRequiresFiveStableFramesBeforeSuccess() {
        val gate = TemporalDecisionGate()

        val results = (0 until 5).map {
            gate.evaluate("USER_1", "lower", FailureReason.NONE)
        }

        assertEquals(5, results.first().requiredFrames)
        assertTrue(results.take(4).all { !it.passed })
        assertTrue(results.last().passed)
    }

    @Test
    fun singleIdentityFailureResetsStableWindow() {
        val gate = TemporalDecisionGate()
        repeat(4) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val failed = gate.evaluate("USER_1", "clean", FailureReason.LOW_SCORE)
        val restarted = gate.evaluate("USER_1", "clean", FailureReason.NONE)

        assertEquals(0, failed.stableFrames)
        assertFalse(failed.passed)
        assertEquals(1, restarted.stableFrames)
        assertFalse(restarted.passed)
    }

    @Test
    fun consecutiveIdentityFailuresKeepStableDecisionWindowReset() {
        val gate = TemporalDecisionGate()
        repeat(2) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val firstFailed = gate.evaluate("USER_1", "clean", FailureReason.LOW_SCORE)
        val secondFailed = gate.evaluate("USER_1", "clean", FailureReason.LOW_GLOBAL_CONSISTENCY)
        val restarted = gate.evaluate("USER_1", "clean", FailureReason.NONE)

        assertEquals(0, firstFailed.stableFrames)
        assertFalse(firstFailed.passed)
        assertEquals(0, secondFailed.stableFrames)
        assertFalse(secondFailed.passed)
        assertEquals(1, restarted.stableFrames)
        assertFalse(restarted.passed)
    }

    @Test
    fun hardFailureStillResetsStableDecisionWindow() {
        val gate = TemporalDecisionGate()
        repeat(5) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val failed = gate.evaluate("USER_1", "clean", FailureReason.LOW_LIVENESS)
        val restarted = gate.evaluate("USER_1", "clean", FailureReason.NONE)

        assertEquals(0, failed.stableFrames)
        assertFalse(failed.passed)
        assertEquals(1, restarted.stableFrames)
        assertFalse(restarted.passed)
    }

    @Test
    fun stableSuccessDoesNotAllowIdentityFailureGraceFrame() {
        val gate = TemporalDecisionGate()
        repeat(5) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val failed = gate.evaluate("USER_1", "clean", FailureReason.LOW_SCORE)
        val secondFailure = gate.evaluate("USER_1", "clean", FailureReason.LOW_GLOBAL_CONSISTENCY)

        assertFalse(failed.passed)
        assertFalse(failed.graceFrame)
        assertEquals(0, failed.stableFrames)
        assertFalse(secondFailure.passed)
        assertFalse(secondFailure.graceFrame)
        assertEquals(0, secondFailure.stableFrames)
    }

    @Test
    fun livenessFailureNeverUsesGraceFrame() {
        val gate = TemporalDecisionGate()
        repeat(5) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val failed = gate.evaluate("USER_1", "clean", FailureReason.LOW_LIVENESS)

        assertFalse(failed.passed)
        assertFalse(failed.graceFrame)
        assertEquals(0, failed.stableFrames)
    }
    @Test
    fun changedIdentityOrOcclusionSummaryStartsANewStableWindow() {
        val gate = TemporalDecisionGate()
        repeat(5) {
            gate.evaluate("USER_1", "clean", FailureReason.NONE)
        }

        val changedUser = gate.evaluate("USER_2", "clean", FailureReason.NONE)
        val changedOcclusion = gate.evaluate("USER_2", "lower", FailureReason.NONE)

        assertEquals(1, changedUser.stableFrames)
        assertFalse(changedUser.passed)
        assertEquals(1, changedOcclusion.stableFrames)
        assertFalse(changedOcclusion.passed)
        assertEquals(5, changedOcclusion.requiredFrames)
    }
}

