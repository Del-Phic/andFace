package dev.andface.galaxy.occlusion

import dev.andface.galaxy.completeFeatureVector

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcclusionAnalyzerTest {
    private val analyzer = OcclusionAnalyzer()

    @Test
    fun cleanHintKeepsAllObservableFeaturesObservable() {
        val result = analyzer.analyze(baseFrame(), profile = null, hint = OcclusionHint())

        assertEquals("clean", result.occlusionSummary)
        assertEquals(FeatureType.COUNT, result.observableCount)
        assertEquals(1.0, result.coverage, 1e-12)
        assertEquals(FeatureType.COUNT, result.observableEvidence.size)
    }

    @Test
    fun maskHintExcludesLowerFaceAndFaceAspectFeatures() {
        val result = analyzer.analyze(
            baseFrame(),
            profile = null,
            hint = OcclusionHint(lowerFaceCovered = true)
        )

        assertEquals("lower", result.occlusionSummary)
        listOf(
            FeatureType.NoseToChin,
            FeatureType.MouthWidth,
            FeatureType.JawWidth,
            FeatureType.NoseToMouth,
            FeatureType.MouthHeight,
            FeatureType.MouthAspect,
            FeatureType.ChinMouthDistance,
            FeatureType.ChinJawOffset,
            FeatureType.LeftJawCheekDistance,
            FeatureType.RightJawCheekDistance,
            FeatureType.JawCheekAsymmetry,
            FeatureType.ChinLateralOffset,
            FeatureType.FaceAspect
        ).forEach { type ->
            val evidence = result.evidenceFor(type)
            assertFalse("$type should be excluded under lower-face occlusion", evidence.observable)
            assertEquals(0.0, evidence.visibility, 1e-12)
            assertTrue(evidence.reason.contains("lower_face_occluded"))
        }
        listOf(
            FeatureType.NoseWidth,
            FeatureType.LeftNoseWing,
            FeatureType.RightNoseWing,
            FeatureType.LeftCheekNose,
            FeatureType.RightCheekNose
        ).forEach { type ->
            val evidence = result.evidenceFor(type)
            assertFalse("$type should be excluded by a manual mask hint", evidence.observable)
            assertTrue(evidence.reason.contains("manual_mask_covered"))
        }
        listOf(FeatureType.NoseBridgeLength, FeatureType.NoseRootToBrowLine, FeatureType.LeftBrowNoseRoot).forEach { type ->
            assertTrue("$type should remain available above the mask", result.evidenceFor(type).observable)
        }
        assertTrue(result.observableCount < FeatureType.COUNT - 13)
    }

    @Test
    fun glassesHintReducesEyeConfidenceButKeepsEyesObservable() {
        val result = analyzer.analyze(
            baseFrame(),
            profile = null,
            hint = OcclusionHint(glasses = true)
        )

        assertEquals("glasses", result.occlusionSummary)
        listOf(FeatureType.LeftEyeOpen, FeatureType.RightEyeOpen).forEach { type ->
            val evidence = result.evidenceFor(type)
            assertTrue("$type should remain observable with glasses hint", evidence.observable)
            assertEquals(0.55, evidence.visibility, 1e-12)
            assertTrue(evidence.reason.contains("glasses_eye_reduced"))
        }
        listOf(FeatureType.BrowDistance, FeatureType.NoseWidth).forEach { type ->
            assertTrue(result.evidenceFor(type).reason.contains("glasses_brow_nose_boosted"))
        }
    }

    @Test
    fun borderlineFrameQualityReducesVisibilityWithoutAddingTemplateFeatures() {
        val lowerQuality = baseFrame().copy(
            quality = FaceFrameQuality.TRUSTED.copy(
                centerX = 0.80,
                inFrameLandmarkRatio = 0.94,
                meshSymmetryScore = 0.50
            )
        )

        val result = analyzer.analyze(
            lowerQuality,
            profile = null,
            hint = OcclusionHint()
        )

        assertEquals("clean", result.occlusionSummary)
        assertEquals(FeatureType.COUNT, result.observableCount)
        assertTrue(result.coverage < 1.0)
        assertTrue(result.coverage > 0.55)
        val evidence = result.evidenceFor(FeatureType.EyeDistance)
        assertTrue(evidence.observable)
        assertTrue(evidence.visibility < 1.0)
        assertTrue(evidence.reason.contains("frame_quality_reduced"))
    }
    @Test
    fun unavailableOptionalIrisLandmarksExcludeIrisFeaturesFromScoring() {
        val base = baseFrame()
        val frameWithoutIris = base.copy(
            quality = base.quality.copy(irisLandmarksAvailable = false)
        )

        val result = analyzer.analyze(
            frameWithoutIris,
            profile = null,
            hint = OcclusionHint()
        )

        FeatureType.ordered.filter { it.isIrisFeature }.forEach { type ->
            val evidence = result.evidenceFor(type)
            assertFalse("$type should not be scored without actual iris landmarks", evidence.observable)
            assertEquals(0.0, evidence.visibility, 1e-12)
            assertTrue(evidence.reason.contains("iris_landmarks_unavailable"))
        }
        assertEquals(FeatureType.COUNT - FeatureType.ordered.count { it.isIrisFeature }, result.observableCount)
        assertTrue(result.coverage < 1.0)
    }

    @Test
    fun profileWithoutReliableEnrollmentIrisExcludesIrisFeaturesEvenWhenCurrentFrameHasIris() {
        val profile = profileFrom(baseFrame())
        FeatureType.ordered.filter { it.isIrisFeature }.forEach { type ->
            profile.sigmas[type.ordinal] = type.minimumSigma * 40.0
        }

        val result = analyzer.analyze(
            baseFrame(),
            profile = profile,
            hint = OcclusionHint()
        )

        FeatureType.ordered.filter { it.isIrisFeature }.forEach { type ->
            val evidence = result.evidenceFor(type)
            assertFalse("$type should not be scored when enrollment lacked reliable iris", evidence.observable)
            assertEquals(0.0, evidence.visibility, 1e-12)
            assertTrue(evidence.reason.contains("iris_unavailable_in_enrollment"))
        }
    }
    @Test
    fun unstableEnrollmentFeatureReducesVisibilityWithoutExcludingFeature() {
        val profile = profileFrom(baseFrame())
        profile.sigmas[FeatureType.EyeDistance.ordinal] = FeatureType.EyeDistance.minimumSigma * 12.0

        val result = analyzer.analyze(
            baseFrame(),
            profile = profile,
            hint = OcclusionHint()
        )

        val evidence = result.evidenceFor(FeatureType.EyeDistance)
        assertTrue(evidence.observable)
        assertEquals(0.45, evidence.visibility, 1e-12)
        assertTrue(evidence.reason.contains("unstable_enrollment_feature"))
        assertTrue(result.coverage < 1.0)
    }

    @Test
    fun leftEyePatchExcludesOnlyTheAffectedEyeFeature() {
        val result = analyzer.analyze(
            baseFrame(),
            profile = null,
            hint = OcclusionHint(leftEyePatch = true)
        )

        assertEquals("left_eye", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeOpen).observable)
        assertEquals(0.0, result.evidenceFor(FeatureType.LeftEyeOpen).visibility, 1e-12)
        assertFalse(result.evidenceFor(FeatureType.LeftOrbitalTriangle).observable)
        assertFalse(result.evidenceFor(FeatureType.LeftInnerBrowNoseArea).observable)
        assertFalse(result.evidenceFor(FeatureType.LeftInnerEyeNoseBridgeTriangle).observable)
        assertFalse(result.evidenceFor(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry).observable)
        assertFalse(result.evidenceFor(FeatureType.NoseTipInnerEyeLineDistance).observable)
        assertTrue(result.evidenceFor(FeatureType.RightEyeOpen).observable)
        assertTrue(result.evidenceFor(FeatureType.RightOrbitalTriangle).observable)
        assertEquals(1.0, result.evidenceFor(FeatureType.RightEyeOpen).visibility, 1e-12)
        val excludedByLeftPatch = FeatureType.ordered.count { it.dependsOnLeftEye }
        assertEquals(FeatureType.COUNT - excludedByLeftPatch, result.observableCount)
    }

    @Test
    fun leftEyePatchCanBeInferredFromNoseBridgeEyeOutliers() {
        val profile = profileFrom(baseFrame())
        val patched = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftInnerEyeNoseBridgeTriangle.ordinal] += 0.12
            values[FeatureType.InnerEyeNoseBridgeTriangleAsymmetry.ordinal] += 0.12
        }

        val result = analyzer.analyze(
            RawFeatureFrame(patched, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("left_eye", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftInnerEyeNoseBridgeTriangle).observable)
        assertFalse(result.evidenceFor(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry).observable)
        assertTrue(result.evidenceFor(FeatureType.RightInnerEyeNoseBridgeTriangle).observable)
    }
    @Test
    fun lowerFaceOcclusionCanBeInferredAgainstCleanProfileWithoutManualHint() {
        val profile = profileFrom(baseFrame())
        val occluded = baseFrame().copyValues().also { values ->
            values[FeatureType.MouthWidth.ordinal] += 0.30
            values[FeatureType.JawWidth.ordinal] += 0.35
            values[FeatureType.NoseToMouth.ordinal] += 0.25
        }

        val result = analyzer.analyze(
            RawFeatureFrame(occluded, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("lower", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.MouthWidth).observable)
        assertFalse(result.evidenceFor(FeatureType.JawWidth).observable)
        assertFalse(result.evidenceFor(FeatureType.NoseToMouth).observable)
    }

    @Test
    fun distributedFineLowerFaceOutliersInferMaskAgainstStrongUpperSupport() {
        val profile = profileFrom(baseFrame())
        val occluded = baseFrame().copyValues().also { values ->
            values[FeatureType.MouthHeight.ordinal] += 0.040
            values[FeatureType.MouthAspect.ordinal] += 0.040
            values[FeatureType.ChinMouthDistance.ordinal] += 0.040
            values[FeatureType.ChinJawOffset.ordinal] += 0.040
        }

        val result = analyzer.analyze(
            RawFeatureFrame(occluded, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("lower", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.MouthHeight).observable)
        assertFalse(result.evidenceFor(FeatureType.MouthAspect).observable)
        assertFalse(result.evidenceFor(FeatureType.ChinMouthDistance).observable)
        assertTrue(result.evidenceFor(FeatureType.BrowDistance).observable)
    }
    @Test
    fun glassesLikeEyeInstabilityCanBeInferredWithoutManualHint() {
        val profile = profileFrom(baseFrame())
        val occluded = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftEyeOpen.ordinal] = 0.0
            values[FeatureType.RightEyeOpen.ordinal] = 0.0
        }

        val result = analyzer.analyze(
            RawFeatureFrame(occluded, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("glasses", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeOpen).observable)
        assertFalse(result.evidenceFor(FeatureType.RightEyeOpen).observable)
        assertTrue(result.evidenceFor(FeatureType.LeftEyeOpen).reason.contains("glasses_left_eye_unstable"))
        assertTrue(result.evidenceFor(FeatureType.RightEyeOpen).reason.contains("glasses_right_eye_unstable"))
    }

    @Test
    fun leftEyePatchCanBeInferredFromMultipleDetailedEyeLandmarkOutliers() {
        val profile = profileFrom(baseFrame())
        val patched = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftEyeHeight.ordinal] += 0.12
            values[FeatureType.LeftEyeWidth.ordinal] += 0.12
            values[FeatureType.LeftIrisEyeOffset.ordinal] += 0.12
        }

        val result = analyzer.analyze(
            RawFeatureFrame(patched, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("left_eye", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeHeight).observable)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeWidth).observable)
        assertTrue(result.evidenceFor(FeatureType.RightEyeHeight).observable)
    }

    @Test
    fun leftEyePatchCanBeInferredFromEyelidArchOutliers() {
        val profile = profileFrom(baseFrame())
        val patched = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftUpperEyelidArch.ordinal] += 0.12
            values[FeatureType.LeftLowerEyelidArch.ordinal] += 0.12
        }

        val result = analyzer.analyze(
            RawFeatureFrame(patched, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("left_eye", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftUpperEyelidArch).observable)
        assertFalse(result.evidenceFor(FeatureType.LeftLowerEyelidArch).observable)
        assertTrue(result.evidenceFor(FeatureType.RightUpperEyelidArch).observable)
    }
    @Test
    fun glassesInstabilityCanBeInferredFromDetailedBothEyeLandmarkOutliers() {
        val profile = profileFrom(baseFrame())
        val glassesLike = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftEyeHeight.ordinal] += 0.12
            values[FeatureType.RightEyeHeight.ordinal] += 0.12
            values[FeatureType.LeftEyeWidth.ordinal] += 0.12
            values[FeatureType.RightEyeWidth.ordinal] += 0.12
        }

        val result = analyzer.analyze(
            RawFeatureFrame(glassesLike, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("glasses", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeHeight).observable)
        assertFalse(result.evidenceFor(FeatureType.RightEyeHeight).observable)
        assertTrue(result.evidenceFor(FeatureType.NoseWidth).observable)
    }


    @Test
    fun oneEyeAbsoluteClosureInfersPatchEvenWhenEnrollmentSigmaIsWide() {
        val profile = profileFrom(baseFrame())
        profile.sigmas[FeatureType.LeftEyeOpen.ordinal] = 0.50
        val patched = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftEyeOpen.ordinal] = 0.02
            values[FeatureType.RightEyeOpen.ordinal] = 0.10
        }

        val result = analyzer.analyze(
            RawFeatureFrame(patched, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("left_eye", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeOpen).observable)
        assertTrue(result.evidenceFor(FeatureType.RightEyeOpen).observable)
    }

    @Test
    fun bothEyesAbsoluteClosureIsGlassesLikeInstabilityNotSingleEyePatch() {
        val profile = profileFrom(baseFrame())
        profile.sigmas[FeatureType.LeftEyeOpen.ordinal] = 0.50
        profile.sigmas[FeatureType.RightEyeOpen.ordinal] = 0.50
        val bothEyesCovered = baseFrame().copyValues().also { values ->
            values[FeatureType.LeftEyeOpen.ordinal] = 0.02
            values[FeatureType.RightEyeOpen.ordinal] = 0.02
        }

        val result = analyzer.analyze(
            RawFeatureFrame(bothEyesCovered, timestampMs = 1_000L),
            profile = profile,
            hint = OcclusionHint()
        )

        assertEquals("glasses", result.occlusionSummary)
        assertFalse(result.evidenceFor(FeatureType.LeftEyeOpen).observable)
        assertFalse(result.evidenceFor(FeatureType.RightEyeOpen).observable)
    }
    private fun dev.andface.galaxy.feature.ObservableFeatureFrame.evidenceFor(type: FeatureType): FeatureEvidence {
        return evidence.first { item -> item.type == type }
    }

    private fun baseFrame(): RawFeatureFrame {
        return RawFeatureFrame(
            values = completeFeatureVector(
                0.50, 0.12, 0.24, 0.45, 0.42, 1.70, 0.10,
                0.10, 0.20, 1.05, 0.00, 0.00, 0.00,
                0.20, 0.20, 0.20, 0.00, 0.12, 0.00, 0.20, 0.26, 0.26, 0.00,
            0.80, 0.72, 0.36, 0.36, 0.00, 0.120, 0.120, 0.000, 0.280, 0.280, 0.000
        ),
            timestampMs = 0L
        )
    }

    private fun profileFrom(frame: RawFeatureFrame): EnrollmentProfile {
        val variance = 0.03 * 0.03
        return EnrollmentProfile(
            userId = "USER_1",
            createdAtMs = 0L,
            modelSha256 = "test",
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = 45,
            means = frame.copyValues(),
            sigmas = DoubleArray(FeatureType.COUNT) { 0.03 },
            covariance = Array(FeatureType.COUNT) { row ->
                DoubleArray(FeatureType.COUNT) { col ->
                    if (row == col) variance else 0.0
                }
            }
        )
    }
}



