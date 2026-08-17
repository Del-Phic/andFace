package dev.andface.galaxy.auth

import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessTrackerTest {
    @Test
    fun staticFaceDoesNotPassLivenessEvenAfterWindowIsFull() {
        val tracker = LivenessTracker()

        val final = staticFrames()
            .map { frame -> tracker.add(frame) }
            .last()

        assertFalse(final.passed)
        assertFalse(final.challengePassed)
        assertEquals(12, final.frameCount)
        assertEquals(0.0, final.score, 1e-12)
    }

    @Test
    fun motionMustReachMinimumFrameWindowBeforePassingChallenge() {
        val tracker = LivenessTracker()

        val firstSeven = challengeCompletingFrames().take(7)
            .map { frame -> tracker.add(frame) }
            .last()
        val eighth = tracker.add(challengeCompletingFrames()[7])

        assertFalse(firstSeven.passed)
        assertEquals(7, firstSeven.frameCount)
        assertTrue(eighth.passed)
        assertTrue(eighth.challengePassed)
        assertEquals(8, eighth.frameCount)
        assertNotNull(eighth.challengeLabel)
    }

    @Test
    fun oneEyePatchCanStillPassWithRemainingEyeAndPoseMotion() {
        val tracker = LivenessTracker()
        val hint = OcclusionHint(leftEyePatch = true)

        val final = challengeCompletingFrames()
            .map { frame -> tracker.add(frame, hint) }
            .last()

        assertTrue(final.passed)
        assertTrue(final.challengePassed)
    }

    @Test
    fun naturalMicroMotionCanPassPassiveLivenessWithoutExplicitChallenge() {
        val tracker = LivenessTracker()

        val final = passiveLivenessFrames()
            .map { frame -> tracker.add(frame) }
            .last()

        assertTrue(final.passed)
        assertFalse(final.challengePassed)
        assertTrue(final.passivePassed)
        assertEquals(12, final.frameCount)
    }
    @Test
    fun subtleMaskedMicroMotionCanPassPassiveLivenessWithoutLowerFaceReconstruction() {
        val tracker = LivenessTracker()
        val hint = OcclusionHint(lowerFaceCovered = true)

        val final = subtleOccludedPassiveLivenessFrames()
            .map { frame -> tracker.add(frame, hint) }
            .last()

        assertTrue(final.passed)
        assertFalse(final.challengePassed)
        assertTrue(final.passivePassed)
        assertEquals(12, final.frameCount)
    }

    @Test
    fun passiveLivenessRequiresNaturalElapsedTime() {
        val tracker = LivenessTracker()

        val final = passiveLivenessFrames().map { frame ->
            tracker.add(frame.copy(timestampMs = 1000L))
        }.last()

        assertFalse(final.passed)
        assertFalse(final.passivePassed)
    }
    @Test
    fun blendshapeMotionCanSupportPassiveLivenessWhenPoseMotionIsSubtle() {
        val tracker = LivenessTracker()

        val final = blendshapeSupportedPassiveFrames()
            .map { frame -> tracker.add(frame) }
            .last()

        assertTrue(final.passed)
        assertTrue(final.passivePassed)
        assertFalse(final.challengePassed)
    }

    @Test
    fun maskedBrowBlendshapeCanSupportVisiblePassiveLiveness() {
        val tracker = LivenessTracker()
        val hint = OcclusionHint(lowerFaceCovered = true)

        val final = maskedBrowBlendshapeFrames()
            .map { frame -> tracker.add(frame, hint) }
            .last()

        assertTrue(final.passed)
        assertTrue(final.passivePassed)
        assertFalse(final.challengePassed)
    }

    @Test
    fun maskedMouthBlendshapeMotionDoesNotBypassVisibleLiveness() {
        val tracker = LivenessTracker()
        val hint = OcclusionHint(lowerFaceCovered = true)

        val final = maskedMouthOnlyBlendshapeFrames()
            .map { frame -> tracker.add(frame, hint) }
            .last()

        assertFalse(final.passed)
        assertFalse(final.passivePassed)
    }
    @Test
    fun maskedStaticFaceStillFailsLiveness() {
        val tracker = LivenessTracker()
        val hint = OcclusionHint(lowerFaceCovered = true)

        val final = staticFrames()
            .map { frame -> tracker.add(frame, hint) }
            .last()

        assertFalse(final.passed)
        assertFalse(final.passivePassed)
        assertEquals(0.0, final.score, 1e-12)
    }

    @Test
    fun resetClearsCollectedFramesAndRequiresWarmupAgain() {
        val tracker = LivenessTracker()
        challengeCompletingFrames().forEach { frame -> tracker.add(frame) }

        tracker.reset()
        val afterReset = tracker.add(challengeCompletingFrames().first())

        assertFalse(afterReset.passed)
        assertFalse(afterReset.challengePassed)
        assertEquals(1, afterReset.frameCount)
        assertEquals(0.0, afterReset.score, 1e-12)
    }

    private fun staticFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = index * 33L
            )
        }
    }

    private fun passiveLivenessFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val wave = if (index == 0) 0.0 else if (index % 2 == 0) 1.0 else -1.0
            RawFeatureFrame(
                values = BASE_FACE.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.030
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.028
                    this[FeatureType.Yaw.ordinal] += wave * 0.024
                    this[FeatureType.Pitch.ordinal] -= wave * 0.022
                },
                timestampMs = index * 33L
            )
        }
    }
    private fun subtleOccludedPassiveLivenessFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val wave = if (index == 0) 0.0 else if (index % 2 == 0) 1.0 else -1.0
            RawFeatureFrame(
                values = BASE_FACE.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.014
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.013
                    this[FeatureType.Yaw.ordinal] += wave * 0.012
                    this[FeatureType.Pitch.ordinal] -= wave * 0.010
                },
                timestampMs = index * 33L
            )
        }
    }

    private fun blendshapeSupportedPassiveFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val wave = if (index == 0) 0.0 else if (index % 2 == 0) 1.0 else -1.0
            RawFeatureFrame(
                values = BASE_FACE.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.030
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.028
                    this[FeatureType.Yaw.ordinal] += wave * 0.006
                    this[FeatureType.Pitch.ordinal] -= wave * 0.005
                },
                timestampMs = index * 33L,
                quality = FaceFrameQuality.TRUSTED.copy(
                    blendshapesAvailable = true,
                    blendshapeActivityScore = if (index % 2 == 0) 0.26 else 0.02
                )
            )
        }
    }

    private fun maskedBrowBlendshapeFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val wave = if (index == 0) 0.0 else if (index % 2 == 0) 1.0 else -1.0
            val browActivity = if (index % 2 == 0) 0.42 else 0.02
            RawFeatureFrame(
                values = BASE_FACE.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.012
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.011
                    this[FeatureType.Yaw.ordinal] += wave * 0.002
                    this[FeatureType.Pitch.ordinal] -= wave * 0.002
                },
                timestampMs = index * 33L,
                quality = FaceFrameQuality.TRUSTED.copy(
                    blendshapesAvailable = true,
                    blendshapeActivityScore = browActivity,
                    blendshapeBrowActivityScore = browActivity,
                    blendshapeMouthActivityScore = if (index % 2 == 0) 0.80 else 0.0
                )
            )
        }
    }

    private fun maskedMouthOnlyBlendshapeFrames(count: Int = 12): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val mouthActivity = if (index % 2 == 0) 0.85 else 0.0
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = index * 33L,
                quality = FaceFrameQuality.TRUSTED.copy(
                    blendshapesAvailable = true,
                    blendshapeActivityScore = mouthActivity,
                    blendshapeMouthActivityScore = mouthActivity
                )
            )
        }
    }
    private fun challengeCompletingFrames(): List<RawFeatureFrame> {
        val yawMotion = doubleArrayOf(0.0, 0.042, -0.042, 0.0, 0.0, 0.039, -0.039, 0.0)
        val pitchMotion = doubleArrayOf(0.0, 0.0, 0.0, -0.036, 0.036, 0.0, 0.0, 0.034)
        return yawMotion.indices.map { index ->
            val wave = if (index == 0) 0.0 else if (index % 2 == 0) 1.0 else -1.0
            RawFeatureFrame(
                values = BASE_FACE.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.018
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.017
                    this[FeatureType.Yaw.ordinal] += yawMotion[index]
                    this[FeatureType.Pitch.ordinal] += pitchMotion[index]
                },
                timestampMs = index * 33L
            )
        }
    }

    private companion object {
        val BASE_FACE = doubleArrayOf(
            0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.0, 0.0, 0.0
        )
    }
}
