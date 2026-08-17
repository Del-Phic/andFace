package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector

import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFrameStabilizerTest {
    @Test
    fun transientLandmarkOutliersAreDampedByRobustWindow() {
        val stabilizer = FeatureFrameStabilizer()
        val outputs = (0 until 7).map { index ->
            val values = BASE_FACE.copyOf()
            if (index in setOf(2, 5)) {
                values[FeatureType.EyeDistance.ordinal] += 0.045
                values[FeatureType.NoseWidth.ordinal] += 0.040
                values[FeatureType.FaceAspect.ordinal] -= 0.070
            }
            stabilizer.add(RawFeatureFrame(values, timestampMs = index * 33L))
        }

        val final = outputs.last()
        assertTrue(
            "expected transient eye-distance outliers to be damped but was ${final.value(FeatureType.EyeDistance)}",
            final.value(FeatureType.EyeDistance) < BASE_FACE[FeatureType.EyeDistance.ordinal] + 0.020
        )
        assertTrue(
            "expected transient nose-width outliers to be damped but was ${final.value(FeatureType.NoseWidth)}",
            final.value(FeatureType.NoseWidth) < BASE_FACE[FeatureType.NoseWidth.ordinal] + 0.020
        )
    }

    @Test
    fun lowerQualityEarlyOutlierCannotPullShortWindowAwayFromTrustedFrame() {
        val stabilizer = FeatureFrameStabilizer()
        stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = 0L))

        val noisy = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] += 0.040
        }
        val stabilized = stabilizer.add(
            RawFeatureFrame(
                noisy,
                timestampMs = 33L,
                quality = FaceFrameQuality.TRUSTED.copy(
                    centerX = 0.82,
                    meshSymmetryScore = 0.45,
                    landmarkTopologyScore = 0.74
                )
            )
        )

        assertEquals(
            BASE_FACE[FeatureType.EyeDistance.ordinal],
            stabilized.value(FeatureType.EyeDistance),
            1e-12
        )
    }
    @Test
    fun largeIdentityJumpStartsNewWindowInsteadOfDraggingPreviousFaceForward() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = index * 33L))
        }

        val changed = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] += 0.090
            this[FeatureType.BrowDistance.ordinal] += 0.060
            this[FeatureType.NoseWidth.ordinal] += 0.070
            this[FeatureType.FaceAspect.ordinal] -= 0.150
        }
        val resetFrame = stabilizer.add(RawFeatureFrame(changed, timestampMs = 8 * 33L))

        assertEquals(changed[FeatureType.EyeDistance.ordinal], resetFrame.value(FeatureType.EyeDistance), 1e-12)
        assertEquals(changed[FeatureType.NoseWidth.ordinal], resetFrame.value(FeatureType.NoseWidth), 1e-12)
        assertEquals(changed[FeatureType.FaceAspect.ordinal], resetFrame.value(FeatureType.FaceAspect), 1e-12)
    }

    @Test
    fun detailedLandmarkIdentityJumpStartsNewWindowInsteadOfAveragingFaces() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = index * 33L))
        }

        val changed = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftBrowEyeDistance.ordinal] += 0.060
            this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.070
            this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.060
        }
        val resetFrame = stabilizer.add(RawFeatureFrame(changed, timestampMs = 8 * 33L))

        assertEquals(
            changed[FeatureType.LeftBrowEyeDistance.ordinal],
            resetFrame.value(FeatureType.LeftBrowEyeDistance),
            1e-12
        )
        assertEquals(
            changed[FeatureType.NoseRootEyeLineDistance.ordinal],
            resetFrame.value(FeatureType.NoseRootEyeLineDistance),
            1e-12
        )
        assertEquals(
            changed[FeatureType.LeftMidFaceTriangle.ordinal],
            resetFrame.value(FeatureType.LeftMidFaceTriangle),
            1e-12
        )
    }

    @Test
    fun periocularIdentityJumpStartsNewWindowInsteadOfAveragingFaces() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = index * 33L))
        }

        val changed = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftOrbitalTriangle.ordinal] += 0.060
            this[FeatureType.LeftInnerBrowNoseArea.ordinal] += 0.055
            this[FeatureType.BrowSpanEyeSpanRatio.ordinal] += 0.180
        }
        val resetFrame = stabilizer.add(RawFeatureFrame(changed, timestampMs = 8 * 33L))

        assertEquals(changed[FeatureType.LeftOrbitalTriangle.ordinal], resetFrame.value(FeatureType.LeftOrbitalTriangle), 1e-12)
        assertEquals(changed[FeatureType.LeftInnerBrowNoseArea.ordinal], resetFrame.value(FeatureType.LeftInnerBrowNoseArea), 1e-12)
        assertEquals(changed[FeatureType.BrowSpanEyeSpanRatio.ordinal], resetFrame.value(FeatureType.BrowSpanEyeSpanRatio), 1e-12)
    }
    @Test
    fun eyelidAndNoseBridgeIdentityJumpStartsNewWindowInsteadOfAveragingFaces() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = index * 33L))
        }

        val changed = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftUpperEyelidArch.ordinal] += 0.055
            this[FeatureType.RightLowerEyelidArch.ordinal] += 0.055
            this[FeatureType.NoseBridgeEyeLineOffset.ordinal] += 0.070
        }
        val resetFrame = stabilizer.add(RawFeatureFrame(changed, timestampMs = 8 * 33L))

        assertEquals(changed[FeatureType.LeftUpperEyelidArch.ordinal], resetFrame.value(FeatureType.LeftUpperEyelidArch), 1e-12)
        assertEquals(changed[FeatureType.RightLowerEyelidArch.ordinal], resetFrame.value(FeatureType.RightLowerEyelidArch), 1e-12)
        assertEquals(changed[FeatureType.NoseBridgeEyeLineOffset.ordinal], resetFrame.value(FeatureType.NoseBridgeEyeLineOffset), 1e-12)
    }
    @Test
    fun irisAvailabilityChangeStartsNewWindowInsteadOfMixingFallbackAndObservedIris() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(
                RawFeatureFrame(
                    BASE_FACE.copyOf(),
                    timestampMs = index * 33L,
                    quality = trustedQuality(irisAvailable = false)
                )
            )
        }

        val observedIris = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftIrisEyeOffset.ordinal] += 0.120
            this[FeatureType.RightIrisEyeOffset.ordinal] += 0.115
            this[FeatureType.IrisDistance.ordinal] -= 0.085
            this[FeatureType.IrisSpanRatio.ordinal] -= 0.180
        }
        val resetFrame = stabilizer.add(
            RawFeatureFrame(
                observedIris,
                timestampMs = 8 * 33L,
                quality = trustedQuality(irisAvailable = true)
            )
        )

        assertEquals(observedIris[FeatureType.LeftIrisEyeOffset.ordinal], resetFrame.value(FeatureType.LeftIrisEyeOffset), 1e-12)
        assertEquals(observedIris[FeatureType.RightIrisEyeOffset.ordinal], resetFrame.value(FeatureType.RightIrisEyeOffset), 1e-12)
        assertEquals(observedIris[FeatureType.IrisDistance.ordinal], resetFrame.value(FeatureType.IrisDistance), 1e-12)
        assertEquals(observedIris[FeatureType.IrisSpanRatio.ordinal], resetFrame.value(FeatureType.IrisSpanRatio), 1e-12)
    }
    @Test
    fun longFrameGapStartsNewWindow() {
        val stabilizer = FeatureFrameStabilizer()
        repeat(7) { index ->
            stabilizer.add(RawFeatureFrame(BASE_FACE.copyOf(), timestampMs = index * 33L))
        }

        val changed = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] += 0.030
        }
        val resetFrame = stabilizer.add(RawFeatureFrame(changed, timestampMs = 2_000L))

        assertEquals(changed[FeatureType.EyeDistance.ordinal], resetFrame.value(FeatureType.EyeDistance), 1e-12)
    }

    private fun trustedQuality(irisAvailable: Boolean): FaceFrameQuality {
        return FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = irisAvailable)
    }
    private companion object {
        val BASE_FACE = completeFeatureVector(
            0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.02, 0.03, 0.00,
            0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03,
            0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000
        )
    }
}
