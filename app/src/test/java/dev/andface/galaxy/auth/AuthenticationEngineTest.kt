package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector

import dev.andface.galaxy.enrollment.EnrollmentBuilder
import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationEngineTest {
    @Test
    fun cleanGenuineUserSucceedsAfterLivenessAndStability() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val results = liveFrames(BASE_FACE).map { frame ->
            engine.authenticate(frame, OcclusionHint())
        }
        val final = results.last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("USER_1", final.matchedUserId)
        assertTrue("expected final score >= 0.70 but was ${final.finalScore}", final.finalScore >= 0.70)
        assertTrue("expected liveness pass", final.livenessPassed)
        assertTrue("expected stable frames", final.stableFrameCount >= final.requiredStableFrames)
    }

    @Test
    fun broadImpostorFaceFailsAgainstSingleEnrollment() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(IMPOSTOR_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected score/support/global failure but was ${final.failureReason}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_COVERAGE
            )
        )
    }

    @Test
    fun scatteredObservableIdentityOutliersFailGlobalConsistencyGate() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val scatteredOutliers = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] += 0.050
            this[FeatureType.BrowDistance.ordinal] += 0.050
            this[FeatureType.NoseWidth.ordinal] += 0.050
            this[FeatureType.InnerEyeDistance.ordinal] += 0.050
            this[FeatureType.BrowWidth.ordinal] += 0.060
            this[FeatureType.BrowAsymmetry.ordinal] += 0.080
            this[FeatureType.NoseBridgeLength.ordinal] += 0.060
            this[FeatureType.EyeNoseLeft.ordinal] += 0.060
            this[FeatureType.EyeNoseRight.ordinal] += 0.060
            this[FeatureType.EyeNoseSymmetry.ordinal] += 0.100
            this[FeatureType.UpperFaceWidth.ordinal] += 0.100
            this[FeatureType.CheekboneWidth.ordinal] += 0.100
            this[FeatureType.LeftCheekNose.ordinal] += 0.060
            this[FeatureType.RightCheekNose.ordinal] += 0.060
            this[FeatureType.LeftBrowEyeGap.ordinal] += 0.050
            this[FeatureType.RightBrowEyeGap.ordinal] += 0.050
            this[FeatureType.LeftUnderEyeCheek.ordinal] += 0.050
            this[FeatureType.RightUnderEyeCheek.ordinal] += 0.050
            this[FeatureType.InterBrowDistance.ordinal] += 0.050
            this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.050
            this[FeatureType.RightBrowNoseRoot.ordinal] += 0.050
            this[FeatureType.LeftInnerEyeNoseRoot.ordinal] += 0.050
            this[FeatureType.RightInnerEyeNoseRoot.ordinal] += 0.050
            this[FeatureType.LeftTempleEye.ordinal] += 0.050
            this[FeatureType.RightTempleEye.ordinal] += 0.050
            this[FeatureType.LeftBrowWidth.ordinal] += 0.050
            this[FeatureType.RightBrowWidth.ordinal] += 0.050
            this[FeatureType.NoseRootToBrowLine.ordinal] += 0.050
            this[FeatureType.ForeheadToEyeLine.ordinal] += 0.050
            this[FeatureType.UpperFaceAspect.ordinal] += 0.080
            this[FeatureType.LeftBrowEyeDistance.ordinal] += 0.050
            this[FeatureType.RightBrowEyeDistance.ordinal] += 0.050
            this[FeatureType.BrowEyeDistanceAsymmetry.ordinal] += 0.060
            this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.080
            this[FeatureType.NoseTipEyeLineDistance.ordinal] += 0.060
            this[FeatureType.NoseLateralOffset.ordinal] += 0.080
            this[FeatureType.LeftPeriocularArea.ordinal] += 0.050
            this[FeatureType.RightPeriocularArea.ordinal] += 0.050
            this[FeatureType.PeriocularAreaAsymmetry.ordinal] += 0.050
            this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.060
            this[FeatureType.RightMidFaceTriangle.ordinal] += 0.060
            this[FeatureType.MidFaceTriangleAsymmetry.ordinal] += 0.060
        }

        val final = liveFrames(scatteredOutliers)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertEquals(FailureReason.LOW_GLOBAL_CONSISTENCY, final.failureReason)
    }
    @Test
    fun singleFeatureLookalikeFailsSupportDistributionGate() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(SINGLE_FEATURE_LOOKALIKE)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected identity support, coverage, or global consistency failure but was ${final.failureReason}",
            final.failureReason in setOf(
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_COVERAGE,
                FailureReason.LOW_GLOBAL_CONSISTENCY
            )
        )
    }

    @Test
    fun glassesLikeEyeOutlierStillCanSucceedForGenuineUser() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(GLASSES_EYE_OUTLIER)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertTrue("expected eyes to remain observable or be safely compensated", final.observableCount >= 11)
    }

    @Test
    fun explicitMaskWithEyePatchFailsAsExcessiveOcclusionForSecureAccess() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val maskedAndPatched = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
            this[FeatureType.LeftEyeOpen.ordinal] = 0.02
        }
        val final = liveFrames(maskedAndPatched)
            .map { frame ->
                engine.authenticate(
                    frame,
                    OcclusionHint(lowerFaceCovered = true, leftEyePatch = true)
                )
            }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertEquals(FailureReason.EXCESSIVE_OCCLUSION, final.failureReason)
    }

    @Test
    fun maskModeRejectsFineUpperMidMismatchEvenWhenLegacyFeaturesMatch() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val fineMismatch = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
            this[FeatureType.InterBrowDistance.ordinal] += 0.16
            this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.12
            this[FeatureType.RightBrowNoseRoot.ordinal] -= 0.10
            this[FeatureType.LeftInnerEyeNoseRoot.ordinal] += 0.08
            this[FeatureType.RightInnerEyeNoseRoot.ordinal] -= 0.06
            this[FeatureType.LeftTempleEye.ordinal] += 0.12
            this[FeatureType.RightTempleEye.ordinal] -= 0.08
            this[FeatureType.EyeWidthAsymmetry.ordinal] += 0.42
            this[FeatureType.LeftBrowSlope.ordinal] += 0.26
            this[FeatureType.RightBrowSlope.ordinal] += 0.22
            this[FeatureType.BrowSlopeAsymmetry.ordinal] += 0.20
        }

        val final = liveFrames(fineMismatch)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected fine-grained upper/mid-face mismatch to fail but was ${final.failureReason}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN
            )
        )
    }
    @Test
    fun maskModeRejectsPeriocularLookalikeEvenWhenLegacyFeaturesMatch() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val periocularLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
            this[FeatureType.LeftOrbitalTriangle.ordinal] += 0.080
            this[FeatureType.RightOrbitalTriangle.ordinal] += 0.070
            this[FeatureType.OrbitalTriangleAsymmetry.ordinal] += 0.080
            this[FeatureType.BrowSpanEyeSpanRatio.ordinal] += 0.220
            this[FeatureType.NoseRootToEyeSpanRatio.ordinal] += 0.160
        }

        val final = liveFrames(periocularLookalike)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected upper/mid-face disagreement to block masked authentication but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_SCORE,
                FailureReason.LOW_MARGIN
            )
        )
    }
    @Test
    fun maskModeRejectsEyeAndBrowOnlyLookalikeWhenMidFaceDoesNotAgree() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val eyeAndBrowOnlyLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.NoseWidth.ordinal] += 0.060
            this[FeatureType.NoseBridgeLength.ordinal] += 0.060
            this[FeatureType.EyeNoseLeft.ordinal] += 0.060
            this[FeatureType.EyeNoseRight.ordinal] += 0.060
            this[FeatureType.EyeNoseSymmetry.ordinal] += 0.090
            this[FeatureType.CheekboneWidth.ordinal] += 0.090
            this[FeatureType.LeftCheekNose.ordinal] += 0.060
            this[FeatureType.RightCheekNose.ordinal] += 0.060
            this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.060
            this[FeatureType.RightBrowNoseRoot.ordinal] -= 0.060
            this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.080
            this[FeatureType.NoseTipEyeLineDistance.ordinal] += 0.060
            this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.060
            this[FeatureType.RightMidFaceTriangle.ordinal] += 0.060
            this[FeatureType.MidFaceTriangleAsymmetry.ordinal] += 0.060
        }

        val final = liveFrames(eyeAndBrowOnlyLookalike)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected visible mid-face disagreement to block masked authentication but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.EXCESSIVE_OCCLUSION,
                FailureReason.LOW_SCORE,
                FailureReason.LOW_MARGIN
            )
        )
    }
    @Test
    fun maskModeRejectsOneSidedUpperFaceMismatchEvenWhenCenterLooksSimilar() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val oneSidedLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
            this[FeatureType.LeftTempleEye.ordinal] += 0.16
            this[FeatureType.LeftBrowWidth.ordinal] += 0.12
            this[FeatureType.LeftBrowOuterEyeGap.ordinal] += 0.12
            this[FeatureType.LeftBrowInnerEyeGap.ordinal] += 0.10
            this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.12
            this[FeatureType.LeftInnerEyeNoseRoot.ordinal] += 0.10
            this[FeatureType.LeftUnderEyeCheek.ordinal] += 0.10
            this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.10
            this[FeatureType.LeftUpperMidfaceArea.ordinal] += 0.08
            this[FeatureType.LeftNoseCheekArea.ordinal] += 0.08
            this[FeatureType.LeftTempleBrowDistance.ordinal] += 0.18
            this[FeatureType.LeftBrowTempleSlope.ordinal] += 0.26
        }

        val final = liveFrames(oneSidedLookalike)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertEquals(FailureReason.LOW_GLOBAL_CONSISTENCY, final.failureReason)
    }
    @Test
    fun automaticallyDetectedMaskCanAuthenticateGenuineUserWithoutManualHint() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val maskedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
        }

        val final = liveFrames(maskedFace)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertTrue("expected automatic lower-face occlusion summary but was ${final.occlusionSummary}", final.occlusionSummary.contains("lower"))
    }

    @Test
    fun automaticallyDetectedMaskStillRejectsImpostorWithoutManualHint() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val maskedImpostor = IMPOSTOR_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
        }

        val final = liveFrames(maskedImpostor)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected masked impostor without manual hint to fail but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_COVERAGE,
                FailureReason.TOO_FEW_FEATURES
            )
        )
    }
    @Test
    fun manualMaskHintCanProceedWhenLowerFaceChangeIsRealButAutoInferenceIsWeak() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val subtleMaskedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.MouthWidth.ordinal] -= 0.025
            this[FeatureType.NoseToMouth.ordinal] += 0.020
        }

        val final = liveFrames(subtleMaskedFace)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("lower", final.occlusionSummary)
    }
    @Test
    fun manualMaskAuthenticatesGenuineUserWhenCoveredNoseAndCheekFeaturesShift() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val maskedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseWidth.ordinal] += 0.20
            this[FeatureType.CheekboneWidth.ordinal] += 0.18
            this[FeatureType.LeftUnderEyeCheek.ordinal] += 0.12
            this[FeatureType.RightUnderEyeCheek.ordinal] += 0.12
            this[FeatureType.LeftNoseWing.ordinal] += 0.14
            this[FeatureType.RightNoseWing.ordinal] += 0.14
            this[FeatureType.NoseBaseTriangle.ordinal] += 0.08
            this[FeatureType.LeftNoseCheekArea.ordinal] += 0.08
            this[FeatureType.RightNoseCheekArea.ordinal] += 0.08
            this[FeatureType.NoseTipDepth.ordinal] += 0.18
        }

        val final = liveFrames(maskedFace)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("lower", final.occlusionSummary)
    }

    @Test
    fun manualMaskHintDoesNotTrustCleanLookingLowerFaceMesh() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(BASE_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("lower", final.occlusionSummary)
    }

    @Test
    fun manualMaskHintStillRejectsBroadImpostorFace() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(IMPOSTOR_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected identity gate failure under manual mask hint but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_SCORE,
                FailureReason.LOW_COVERAGE
            )
        )
    }

    @Test
    fun manualMaskHintRejectsNoseStructureLookalike() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val noseStructureLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
            this[FeatureType.NoseWidth.ordinal] += 0.07
            this[FeatureType.NoseBridgeLength.ordinal] += 0.07
            this[FeatureType.EyeNoseLeft.ordinal] += 0.08
            this[FeatureType.EyeNoseRight.ordinal] -= 0.08
            this[FeatureType.EyeNoseSymmetry.ordinal] += 0.16
            this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.12
            this[FeatureType.RightBrowNoseRoot.ordinal] -= 0.08
            this[FeatureType.BrowNoseRootAsymmetry.ordinal] += 0.20
            this[FeatureType.NoseRootToBrowLine.ordinal] += 0.12
            this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.16
            this[FeatureType.NoseTipEyeLineDistance.ordinal] += 0.12
            this[FeatureType.NoseLateralOffset.ordinal] += 0.20
            this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.10
            this[FeatureType.RightMidFaceTriangle.ordinal] += 0.10
            this[FeatureType.MidFaceTriangleAsymmetry.ordinal] += 0.18
            this[FeatureType.LeftNoseWing.ordinal] += 0.18
            this[FeatureType.RightNoseWing.ordinal] -= 0.06
            this[FeatureType.NoseWingAsymmetry.ordinal] += 0.24
            this[FeatureType.NoseBaseTriangle.ordinal] += 0.10
        }

        val final = liveFrames(noseStructureLookalike)
            .map { frame -> engine.authenticate(frame, OcclusionHint(lowerFaceCovered = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue("expected lower occlusion summary but was ${final.occlusionSummary}", final.occlusionSummary.contains("lower"))
        assertEquals(FailureReason.LOW_GLOBAL_CONSISTENCY, final.failureReason)
    }
    @Test
    fun automaticallyDetectedLeftEyePatchUsesOcclusionAwareLivenessWithoutManualHint() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val patchedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftEyeOpen.ordinal] = 0.02
        }

        val final = leftEyePatchLiveFrames(patchedFace)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("left_eye", final.occlusionSummary)
        assertTrue("expected liveness to use remaining eye and pose motion", final.livenessPassed)
    }

    @Test
    fun automaticallyDetectedRightEyePatchUsesOcclusionAwareLivenessWithoutManualHint() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val patchedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.RightEyeOpen.ordinal] = 0.02
        }

        val final = rightEyePatchLiveFrames(patchedFace)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("right_eye", final.occlusionSummary)
        assertTrue("expected liveness to use remaining eye and pose motion", final.livenessPassed)
    }

    @Test
    fun detailedLeftEyeLandmarkPatchCanBeDetectedWithoutManualHint() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val patchedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftEyeHeight.ordinal] += 0.10
            this[FeatureType.LeftEyeWidth.ordinal] += 0.10
            this[FeatureType.LeftIrisEyeOffset.ordinal] += 0.10
        }

        val final = liveFrames(patchedFace, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("left_eye", final.occlusionSummary)
        assertTrue("expected right eye and pose to keep liveness valid", final.livenessPassed)
    }
    @Test
    fun manualLeftEyePatchHintCanAuthenticateWhenMediaPipeEstimatesCoveredEye() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(BASE_FACE, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint(leftEyePatch = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("left_eye", final.occlusionSummary)
        assertTrue("expected remaining eye and pose to keep liveness valid", final.livenessPassed)
    }

    @Test
    fun manualRightEyePatchHintCanAuthenticateWhenMediaPipeEstimatesCoveredEye() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(BASE_FACE, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint(rightEyePatch = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertEquals("right_eye", final.occlusionSummary)
        assertTrue("expected remaining eye and pose to keep liveness valid", final.livenessPassed)
    }

    @Test
    fun manualLeftEyePatchHintStillRejectsBroadImpostorFace() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(IMPOSTOR_FACE, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint(leftEyePatch = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected impostor with manual eye-patch hint to fail but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_COVERAGE,
                FailureReason.TOO_FEW_FEATURES,
                FailureReason.EXCESSIVE_OCCLUSION
            )
        )
    }

    @Test
    fun manualRightEyePatchHintStillRejectsBroadImpostorFace() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(IMPOSTOR_FACE, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint(rightEyePatch = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected impostor with manual right-eye-patch hint to fail but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_COVERAGE,
                FailureReason.TOO_FEW_FEATURES,
                FailureReason.EXCESSIVE_OCCLUSION
            )
        )
    }

    @Test
    fun manualLeftEyePatchHintRejectsVisibleSideLookalike() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val visibleSideLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.RightEyeWidth.ordinal] += 0.10
            this[FeatureType.RightEyeHeight.ordinal] += 0.08
            this[FeatureType.RightBrowEyeDistance.ordinal] += 0.08
            this[FeatureType.RightPeriocularArea.ordinal] += 0.06
            this[FeatureType.RightEyeCornerTilt.ordinal] += 0.20
            this[FeatureType.RightBrowWidth.ordinal] += 0.10
            this[FeatureType.RightBrowOuterEyeGap.ordinal] += 0.10
            this[FeatureType.RightBrowInnerEyeGap.ordinal] += 0.10
            this[FeatureType.RightBrowNoseRoot.ordinal] += 0.10
            this[FeatureType.RightInnerEyeNoseRoot.ordinal] += 0.08
            this[FeatureType.RightTempleEye.ordinal] += 0.12
            this[FeatureType.RightUnderEyeCheek.ordinal] += 0.10
            this[FeatureType.RightMidFaceTriangle.ordinal] += 0.08
            this[FeatureType.RightUpperMidfaceArea.ordinal] += 0.08
            this[FeatureType.RightNoseCheekArea.ordinal] += 0.08
            this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.06
            this[FeatureType.NoseTipEyeLineDistance.ordinal] += 0.05
            this[FeatureType.NoseLateralOffset.ordinal] += 0.06
        }

        val final = liveFrames(visibleSideLookalike, count = 14)
            .map { frame -> engine.authenticate(frame, OcclusionHint(leftEyePatch = true)) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.FAILED, final.decision)
        assertTrue(
            "expected visible right-eye/mid-face disagreement to fail but was ${resultSummary(final)}",
            final.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.EXCESSIVE_OCCLUSION
            )
        )
    }

    @Test
    fun occlusionHintChangeRestartsLivenessChallenge() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val cleanFinal = liveFrames(BASE_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()
        assertEquals(resultSummary(cleanFinal), AuthDecision.SUCCESS, cleanFinal.decision)

        val maskedFace = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
        }
        val firstMaskedFrame = liveFrames(maskedFace, startMs = 5_000L, count = 1).first()

        val afterHintChange = engine.authenticate(firstMaskedFrame, OcclusionHint(lowerFaceCovered = true))

        assertEquals(resultSummary(afterHintChange), AuthDecision.FAILED, afterHintChange.decision)
        assertEquals(FailureReason.LOW_LIVENESS, afterHintChange.failureReason)
        assertEquals(1, afterHintChange.livenessFrameCount)
    }

    @Test
    fun stableSuccessDoesNotGraceCatastrophicIdentityDrop() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val genuineFinal = liveFrames(BASE_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()
        assertEquals(resultSummary(genuineFinal), AuthDecision.SUCCESS, genuineFinal.decision)

        val impostorFrame = liveFrames(IMPOSTOR_FACE, startMs = 6_000L, count = 1).first()
        val result = engine.authenticate(impostorFrame, OcclusionHint())

        assertEquals(resultSummary(result), AuthDecision.FAILED, result.decision)
        assertTrue(
            "catastrophic identity drop must not use temporal grace: ${resultSummary(result)}",
            result.failureReason in setOf(
                FailureReason.LOW_SCORE,
                FailureReason.LOW_IDENTITY_SUPPORT,
                FailureReason.LOW_GLOBAL_CONSISTENCY,
                FailureReason.LOW_MARGIN,
                FailureReason.LOW_COVERAGE
            )
        )
    }
    @Test
    fun repeatedRiskyFailuresDoNotLockDemoApp() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val clock = TestClock()
        var storedLockUntilMs = Long.MIN_VALUE
        val engine = AuthenticationEngine(
            securityClockMs = clock::now,
            onAccessLockout = { untilMs -> storedLockUntilMs = untilMs }
        )
        engine.setProfiles(listOf(profile))

        val impostorResult = liveFrames(IMPOSTOR_FACE, startMs = 10_000L, stepMs = 500L, count = 26)
            .map { frame ->
                clock.set(frame.timestampMs)
                engine.authenticate(frame, OcclusionHint())
            }
            .last()

        assertEquals(resultSummary(impostorResult), AuthDecision.FAILED, impostorResult.decision)
        assertTrue("demo app must not return lockout", impostorResult.failureReason != FailureReason.TOO_MANY_ATTEMPTS)
        assertEquals(Long.MIN_VALUE, storedLockUntilMs)

        val recovered = liveFrames(BASE_FACE, startMs = 60_000L, stepMs = 500L, count = 14)
            .map { frame ->
                clock.set(frame.timestampMs)
                engine.authenticate(frame, OcclusionHint())
            }
            .last()

        assertEquals(resultSummary(recovered), AuthDecision.SUCCESS, recovered.decision)
        assertEquals(FailureReason.NONE, recovered.failureReason)
    }

    @Test
    fun restoredRiskyFailureWindowIsIgnoredInDemoApp() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val clock = TestClock()
        var storedLockUntilMs = Long.MIN_VALUE
        val restartedEngine = AuthenticationEngine(
            securityClockMs = clock::now,
            onAccessLockout = { untilMs -> storedLockUntilMs = untilMs }
        )
        restartedEngine.setProfiles(listOf(profile))
        clock.set(12_000L)
        restartedEngine.restoreRiskyFailureState(
            AuthenticationEngine.RiskyFailureState(
                count = 7,
                windowStartMs = 10_000L,
                lastCountedMs = 10_000L
            )
        )

        val result = liveFrames(IMPOSTOR_FACE, startMs = 12_000L, stepMs = 500L, count = 16)
            .map { frame ->
                clock.set(frame.timestampMs)
                restartedEngine.authenticate(frame, OcclusionHint())
            }
            .last()

        assertEquals(resultSummary(result), AuthDecision.FAILED, result.decision)
        assertTrue("demo app must not restore lockout", result.failureReason != FailureReason.TOO_MANY_ATTEMPTS)
        assertEquals(Long.MIN_VALUE, storedLockUntilMs)
    }

    @Test
    fun repeatedMatureLivenessFailuresStayLivenessFailuresWithoutLock() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val attackClock = TestClock()
        val attackEngine = AuthenticationEngine(securityClockMs = attackClock::now)
        attackEngine.setProfiles(listOf(profile))

        val result = staticLiveFrames(BASE_FACE, startMs = 20_000L, stepMs = 500L, count = 20)
            .map { frame ->
                attackClock.set(frame.timestampMs)
                attackEngine.authenticate(frame, OcclusionHint())
            }
            .last()

        assertEquals(resultSummary(result), AuthDecision.FAILED, result.decision)
        assertEquals(FailureReason.LOW_LIVENESS, result.failureReason)
    }

    @Test
    fun cleanGenuineUserWithTransientLandmarkJitterStaysSuccessful() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = jitteryLiveFrames(BASE_FACE)
            .map { frame -> engine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertTrue("expected stable frames despite jitter", final.stableFrameCount >= final.requiredStableFrames)
    }

    @Test
    fun cleanGenuineUserWithSingleTransientGeometrySpikeStaysSuccessful() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val frames = liveFrames(BASE_FACE).mapIndexed { index, frame ->
            if (index == 11) {
                frame.copy(
                    values = frame.values.copyOf().apply {
                        this[FeatureType.EyeDistance.ordinal] = 1.20
                        this[FeatureType.UpperFaceWidth.ordinal] = 0.18
                    }
                )
            } else {
                frame
            }
        }
        val final = frames.map { frame -> engine.authenticate(frame, OcclusionHint()) }.last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals(FailureReason.NONE, final.failureReason)
        assertTrue("expected stable geometry after one bad MediaPipe frame", final.stableFrameCount >= final.requiredStableFrames)
    }
    @Test
    fun tinyOrOffCenterFaceFailsBeforeScoring() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val final = liveFrames(BASE_FACE)
            .map { frame ->
                engine.authenticate(
                    frame.copy(
                        quality = FaceFrameQuality(
                            faceWidthRatio = 0.10,
                            faceHeightRatio = 0.14,
                            centerX = 0.12,
                            centerY = 0.50,
                            inFrameLandmarkRatio = 0.82
                        )
                    ),
                    OcclusionHint()
                )
            }
            .last()

        assertEquals(AuthDecision.FAILED, final.decision)
        assertEquals(FailureReason.POOR_FACE_QUALITY, final.failureReason)
    }


    @Test
    fun structurallyInvalidLandmarkTopologyFailsBeforeScoringAndReportsQualityMetrics() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val result = engine.authenticate(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality.TRUSTED.copy(landmarkTopologyScore = 0.30)
            ),
            OcclusionHint()
        )

        assertEquals(AuthDecision.FAILED, result.decision)
        assertEquals(FailureReason.POOR_FACE_QUALITY, result.failureReason)
        assertEquals(0.0, result.faceQualityScore, 1e-6)
        assertEquals(1.0, result.meshSymmetryScore, 1e-6)
        assertEquals(0.30, result.landmarkTopologyScore, 1e-6)
    }
    @Test
    fun nonFiniteFeatureFrameFailsBeforeScoring() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val malformedValues = BASE_FACE.copyOf().also { values ->
            values[FeatureType.NoseWidth.ordinal] = Double.NaN
        }

        val result = engine.authenticate(
            RawFeatureFrame(malformedValues, timestampMs = 1_000L),
            OcclusionHint()
        )

        assertEquals(AuthDecision.FAILED, result.decision)
        assertEquals(FailureReason.POOR_FACE_QUALITY, result.failureReason)
        assertEquals(1, result.registeredUserCount)
    }

    @Test
    fun shortFeatureFrameFailsBeforeScoring() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))

        val result = engine.authenticate(
            RawFeatureFrame(DoubleArray(FeatureType.COUNT - 1) { 0.1 }, timestampMs = 1_000L),
            OcclusionHint()
        )

        assertEquals(AuthDecision.FAILED, result.decision)
        assertEquals(FailureReason.TOO_FEW_FEATURES, result.failureReason)
        assertEquals(1, result.registeredUserCount)
    }
    @Test
    fun implausibleUpperFaceGeometryFailsBeforeScoring() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val malformedValues = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] = 1.20
            this[FeatureType.UpperFaceWidth.ordinal] = 0.18
        }

        val result = engine.authenticate(
            RawFeatureFrame(malformedValues, timestampMs = 1_000L),
            OcclusionHint()
        )

        assertEquals(AuthDecision.FAILED, result.decision)
        assertEquals(FailureReason.POOR_FACE_QUALITY, result.failureReason)
        assertEquals(1, result.registeredUserCount)
    }

    @Test
    fun lowerFaceOutliersWithMaskHintAreHandledByOcclusionLogicNotQualityFailure() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(profile))
        val maskedValues = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] = 1.20
            this[FeatureType.MouthWidth.ordinal] = 0.03
            this[FeatureType.JawWidth.ordinal] = 7.40
            this[FeatureType.NoseToMouth.ordinal] = 0.02
            this[FeatureType.FaceAspect.ordinal] = 0.35
        }

        val result = engine.authenticate(
            RawFeatureFrame(maskedValues, timestampMs = 1_000L),
            OcclusionHint(lowerFaceCovered = true)
        )

        assertTrue(
            "mask-hidden lower face must be excluded, not rejected as mesh quality: ${resultSummary(result)}",
            result.failureReason != FailureReason.POOR_FACE_QUALITY
        )
    }

    @Test
    fun cleanEnrollmentPipelineBuildsProfileAcceptedByAuthenticator() {
        val enrollmentEngine = AuthenticationEngine()
        val samples = enrollmentSamples(BASE_FACE).mapIndexed { index, frame ->
            frame.copy(
                values = frame.values.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += if (index % 2 == 0) 0.018 else -0.018
                    this[FeatureType.RightEyeOpen.ordinal] += if (index % 3 == 0) 0.016 else -0.010
                    this[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
                    this[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
                }
            )
        }

        samples.forEach { sample ->
            assertEquals(FailureReason.NONE, enrollmentEngine.checkEnrollmentSample(sample, OcclusionHint()))
        }
        assertEquals(FailureReason.NONE, enrollmentEngine.checkEnrollmentBaseline(samples))

        val profile = EnrollmentBuilder.build("USER_1", samples)
        assertEquals(samples.size, profile.sampleCount)
        assertEquals(FailureReason.NONE, enrollmentEngine.checkEnrollmentSeparation(profile, existingProfiles = emptyList()))

        enrollmentEngine.setProfiles(listOf(profile))
        val final = liveFrames(BASE_FACE, count = 14)
            .map { frame -> enrollmentEngine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals("USER_1", final.matchedUserId)
    }
    @Test
    fun practicalCameraCoverageEnrollmentPipelineBuildsUsableProfile() {
        val enrollmentEngine = AuthenticationEngine()
        val samples = practicalCameraEnrollmentSamples(BASE_FACE)

        samples.forEach { sample ->
            assertEquals(
                enrollmentEngine.enrollmentSampleDiagnostic(sample, OcclusionHint()),
                FailureReason.NONE,
                enrollmentEngine.checkEnrollmentSample(sample, OcclusionHint())
            )
        }
        assertEquals(FailureReason.NONE, enrollmentEngine.checkEnrollmentBaseline(samples))

        val profile = EnrollmentBuilder.build("USER_1", samples)
        assertEquals(samples.size, profile.sampleCount)
        assertEquals(FailureReason.NONE, enrollmentEngine.checkEnrollmentSeparation(profile, existingProfiles = emptyList()))

        enrollmentEngine.setProfiles(listOf(profile))
        val final = liveFrames(BASE_FACE, count = 14)
            .map { frame -> enrollmentEngine.authenticate(frame, OcclusionHint()) }
            .last()

        assertEquals(resultSummary(final), AuthDecision.SUCCESS, final.decision)
        assertEquals("USER_1", final.matchedUserId)
    }
    @Test
    fun enrollmentAcceptsCleanSampleWithoutOptionalIrisLandmarks() {
        val engine = AuthenticationEngine()

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = false)
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.NONE, result)
    }
    @Test
    fun enrollmentAcceptsClosedMouthCleanSampleAfterTopologyRelaxation() {
        val engine = AuthenticationEngine()
        val closedMouthValues = BASE_FACE.copyOf().apply {
            this[FeatureType.MouthHeight.ordinal] = 0.0
            this[FeatureType.MouthAspect.ordinal] = 0.0
        }

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = closedMouthValues,
                timestampMs = 1_000L,
                quality = FaceFrameQuality.TRUSTED.copy(
                    landmarkTopologyScore = 0.95,
                    landmarkTopologyHint = "mouthOpen"
                )
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.NONE, result)
    }

    @Test
    fun enrollmentIgnoresIrisRangesWhenOptionalIrisLandmarksAreUnavailable() {
        val engine = AuthenticationEngine()
        val noIrisValues = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftIrisEyeOffset.ordinal] = 1.20
            this[FeatureType.RightIrisEyeOffset.ordinal] = 1.20
            this[FeatureType.IrisOffsetAsymmetry.ordinal] = 1.20
            this[FeatureType.IrisDistance.ordinal] = 0.02
            this[FeatureType.LeftIrisNoseRoot.ordinal] = 1.20
            this[FeatureType.RightIrisNoseRoot.ordinal] = 1.20
            this[FeatureType.IrisNoseRootAsymmetry.ordinal] = 1.20
            this[FeatureType.IrisSpanRatio.ordinal] = 2.20
        }

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = noIrisValues,
                timestampMs = 1_000L,
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = false)
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.NONE, result)
    }

    @Test
    fun enrollmentCaptureDefersTransientIrisOutlierToBaselineValidation() {
        val engine = AuthenticationEngine()
        val badIrisValues = BASE_FACE.copyOf().apply {
            this[FeatureType.LeftIrisEyeOffset.ordinal] = 1.20
            this[FeatureType.RightIrisEyeOffset.ordinal] = 1.20
            this[FeatureType.IrisOffsetAsymmetry.ordinal] = 1.20
            this[FeatureType.IrisDistance.ordinal] = 0.02
            this[FeatureType.LeftIrisNoseRoot.ordinal] = 1.20
            this[FeatureType.RightIrisNoseRoot.ordinal] = 1.20
            this[FeatureType.IrisNoseRootAsymmetry.ordinal] = 1.20
            this[FeatureType.IrisSpanRatio.ordinal] = 2.20
        }

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = badIrisValues,
                timestampMs = 1_000L,
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = true)
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.NONE, result)
    }
    @Test
    fun enrollmentRejectsImplausibleUpperFaceGeometryAsPoorQuality() {
        val engine = AuthenticationEngine()
        val malformedValues = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] = 1.20
            this[FeatureType.UpperFaceWidth.ordinal] = 0.18
        }

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(malformedValues, timestampMs = 1_000L),
            OcclusionHint()
        )

        assertEquals(FailureReason.POOR_FACE_QUALITY, result)
    }
    @Test
    fun enrollmentRejectsPoorFaceQualitySamples() {
        val engine = AuthenticationEngine()

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality(
                    faceWidthRatio = 0.12,
                    faceHeightRatio = 0.16,
                    centerX = 0.50,
                    centerY = 0.92,
                    inFrameLandmarkRatio = 0.90
                )
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.POOR_FACE_QUALITY, result)
    }

    @Test
    fun enrollmentRejectsAccessAcceptableButWeakRegistrationFraming() {
        val engine = AuthenticationEngine()

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality(
                    faceWidthRatio = 0.22,
                    faceHeightRatio = 0.27,
                    centerX = 0.84,
                    centerY = 0.50,
                    inFrameLandmarkRatio = 0.93
                )
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.POOR_FACE_QUALITY, result)
    }
    @Test
    fun enrollmentAcceptsBorderlineButGeometricallyCleanCameraFrame() {
        val engine = AuthenticationEngine()
        val frame = RawFeatureFrame(
            values = BASE_FACE.copyOf(),
            timestampMs = 1_000L,
            quality = FaceFrameQuality(
                faceWidthRatio = 0.18,
                faceHeightRatio = 0.25,
                centerX = 0.68,
                centerY = 0.70,
                inFrameLandmarkRatio = 0.94,
                meshSymmetryScore = 0.50,
                landmarkTopologyScore = 0.75
            )
        )

        val result = engine.checkEnrollmentSample(frame, OcclusionHint())

        assertEquals(engine.enrollmentSampleDiagnostic(frame, OcclusionHint()), FailureReason.NONE, result)
    }
    @Test
    fun enrollmentAndAuthenticationShareUsableCameraQualityGate() {
        val engine = AuthenticationEngine()
        val frame = RawFeatureFrame(
            values = BASE_FACE.copyOf(),
            timestampMs = 1_000L,
            quality = FaceFrameQuality(
                faceWidthRatio = 0.20,
                faceHeightRatio = 0.30,
                centerX = 0.50,
                centerY = 0.50,
                inFrameLandmarkRatio = 0.91,
                meshSymmetryScore = 0.35,
                landmarkTopologyScore = 0.55
            )
        )

        assertTrue(!frame.quality.acceptableForAccess)
        assertTrue(frame.quality.acceptableForEnrollmentCapture)
        assertEquals(FailureReason.NONE, engine.checkEnrollmentSample(frame, OcclusionHint()))

        engine.setProfiles(listOf(EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))))
        val authentication = engine.authenticate(frame, OcclusionHint())

        assertTrue(authentication.failureReason != FailureReason.POOR_FACE_QUALITY)
    }


    @Test
    fun enrollmentDiagnosticReportsCoverageAndFeatureGates() {
        val engine = AuthenticationEngine()

        val diagnostic = engine.enrollmentSampleDiagnostic(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality(
                    faceWidthRatio = 0.20,
                    faceHeightRatio = 0.34,
                    centerX = 0.50,
                    centerY = 0.50,
                    inFrameLandmarkRatio = 1.0
                )
            ),
            OcclusionHint()
        )

        assertTrue(diagnostic.contains("obs="))
        assertTrue(diagnostic.contains("eff="))
        assertTrue(diagnostic.contains("cov="))
    }
    @Test
    fun enrollmentAcceptsCleanFrameWithPracticalCameraCoverage() {
        val engine = AuthenticationEngine()

        val result = engine.checkEnrollmentSample(
            RawFeatureFrame(
                values = BASE_FACE.copyOf(),
                timestampMs = 1_000L,
                quality = FaceFrameQuality(
                    faceWidthRatio = 0.20,
                    faceHeightRatio = 0.34,
                    centerX = 0.50,
                    centerY = 0.50,
                    inFrameLandmarkRatio = 1.0
                )
            ),
            OcclusionHint()
        )

        assertEquals(FailureReason.NONE, result)
    }

    @Test
    fun enrollmentBaselineRequiresFullCleanSampleSet() {
        val engine = AuthenticationEngine()

        val result = engine.checkEnrollmentBaseline(enrollmentSamples(BASE_FACE).take(44))

        assertEquals(FailureReason.TOO_FEW_FEATURES, result)
    }

    @Test
    fun enrollmentSeparationRejectsNearlyDuplicateOtherUser() {
        val existing = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val nearDuplicateFace = BASE_FACE.copyOf().apply {
            this[FeatureType.EyeDistance.ordinal] += 0.003
            this[FeatureType.BrowDistance.ordinal] -= 0.002
            this[FeatureType.NoseWidth.ordinal] += 0.002
            this[FeatureType.MouthWidth.ordinal] += 0.002
            this[FeatureType.JawWidth.ordinal] -= 0.010
            this[FeatureType.CheekboneWidth.ordinal] += 0.002
        }
        val candidate = EnrollmentBuilder.build("USER_2", enrollmentSamples(nearDuplicateFace))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(existing))

        val result = engine.checkEnrollmentSeparation(candidate)

        assertEquals(FailureReason.LOW_MARGIN, result)
    }

    @Test
    fun enrollmentSeparationRejectsMaskAmbiguousOtherUser() {
        val existing = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val lowerFaceOnlyDifferent = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.22
            this[FeatureType.MouthWidth.ordinal] -= 0.16
            this[FeatureType.JawWidth.ordinal] += 0.52
            this[FeatureType.NoseToMouth.ordinal] += 0.16
            this[FeatureType.FaceAspect.ordinal] -= 0.30
        }
        val candidate = EnrollmentBuilder.build("USER_2", enrollmentSamples(lowerFaceOnlyDifferent))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(existing))

        val result = engine.checkEnrollmentSeparation(candidate)

        assertEquals(FailureReason.LOW_MARGIN, result)
    }
    @Test
    fun enrollmentSeparationRejectsMaskCriticalLookalikeWhenUpperSilhouetteDiffers() {
        val existing = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val maskCriticalLookalike = BASE_FACE.copyOf().apply {
            this[FeatureType.NoseToChin.ordinal] += 0.20
            this[FeatureType.MouthWidth.ordinal] -= 0.15
            this[FeatureType.JawWidth.ordinal] += 0.42
            this[FeatureType.NoseToMouth.ordinal] += 0.14
            this[FeatureType.FaceAspect.ordinal] -= 0.24
            this[FeatureType.UpperFaceWidth.ordinal] += 0.08
            this[FeatureType.CheekboneWidth.ordinal] += 0.07
            this[FeatureType.BrowWidth.ordinal] += 0.08
            this[FeatureType.BrowAsymmetry.ordinal] += 0.09
            this[FeatureType.LeftTempleEye.ordinal] += 0.09
            this[FeatureType.RightTempleEye.ordinal] -= 0.07
            this[FeatureType.TempleEyeAsymmetry.ordinal] += 0.16
            this[FeatureType.LeftBrowSlope.ordinal] += 0.18
            this[FeatureType.RightBrowSlope.ordinal] += 0.16
            this[FeatureType.BrowSlopeAsymmetry.ordinal] += 0.08
            this[FeatureType.ForeheadToEyeLine.ordinal] += 0.08
            this[FeatureType.UpperFaceAspect.ordinal] += 0.14
        }
        val candidate = EnrollmentBuilder.build("USER_2", enrollmentSamples(maskCriticalLookalike))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(existing))

        val result = engine.checkEnrollmentSeparation(candidate)

        assertEquals(FailureReason.LOW_MARGIN, result)
    }
    @Test
    fun enrollmentSeparationAcceptsDistinctOtherUser() {
        val existing = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        val candidate = EnrollmentBuilder.build("USER_2", enrollmentSamples(IMPOSTOR_FACE))
        val engine = AuthenticationEngine()
        engine.setProfiles(listOf(existing))

        val result = engine.checkEnrollmentSeparation(candidate)

        assertEquals(FailureReason.NONE, result)
    }
    private fun resultSummary(result: AuthResult): String {
        return "reason=${result.failureReason}, fuzzy=${result.fuzzyScore}, mahalanobis=${result.mahalanobisScore}, " +
            "final=${result.finalScore}, coverage=${result.coverage}, " +
            "support=${result.identitySupportCount}/${result.requiredSupportCount}, " +
            "consistency=${result.identityConsistencyScore}/${result.requiredIdentityConsistencyScore}, " +
            "outlier=${result.identityOutlierScore}/${result.requiredIdentityOutlierScore}, " +
            "observable=${result.observableCount}, " +
            "liveness=${result.livenessScore}/${result.livenessPassed}/${result.livenessFrameCount}, " +
            "stable=${result.stableFrameCount}/${result.requiredStableFrames}, " +
            "micro=${result.microRegionDiagnostics}, occlusion=${result.occlusionSummary}"
    }

    private fun enrollmentSamples(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 45).map { index ->
            val delta = ((index % 5) - 2) * 0.001
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.EyeDistance.ordinal] += delta
                    this[FeatureType.BrowDistance.ordinal] -= delta * 0.5
                    this[FeatureType.NoseWidth.ordinal] += delta * 0.4
                    this[FeatureType.MouthWidth.ordinal] -= delta * 0.3
                    this[FeatureType.Yaw.ordinal] += delta * 0.6
                    this[FeatureType.Pitch.ordinal] -= delta * 0.4
                },
                timestampMs = index * 33L
            )
        }
    }

    private fun practicalCameraEnrollmentSamples(base: DoubleArray): List<RawFeatureFrame> {
        return enrollmentSamples(base).mapIndexed { index, frame ->
            frame.copy(
                values = frame.values.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += if (index % 2 == 0) 0.018 else -0.018
                    this[FeatureType.RightEyeOpen.ordinal] += if (index % 3 == 0) 0.016 else -0.010
                    this[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
                    this[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
                },
                quality = FaceFrameQuality(
                    faceWidthRatio = 0.20,
                    faceHeightRatio = 0.34,
                    centerX = 0.50,
                    centerY = 0.50,
                    inFrameLandmarkRatio = 1.0
                )
            )
        }
    }
    private fun liveFrames(
        base: DoubleArray,
        startMs: Long = 2000L,
        stepMs: Long = 33L,
        count: Int = 12
    ): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val yawMotion = when (index % 5) {
                1 -> 0.040
                2 -> -0.040
                else -> 0.0
            }
            val pitchMotion = when (index % 5) {
                3 -> -0.034
                4 -> 0.034
                else -> 0.0
            }
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.014
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                },
                timestampMs = startMs + index * stepMs
            )
        }
    }

    private fun staticLiveFrames(
        base: DoubleArray,
        startMs: Long = 2000L,
        stepMs: Long = 33L,
        count: Int = 12
    ): List<RawFeatureFrame> {
        return (0 until count).map { index ->
            RawFeatureFrame(
                values = base.copyOf(),
                timestampMs = startMs + index * stepMs
            )
        }
    }

    private fun leftEyePatchLiveFrames(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 14).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val yawMotion = when (index % 5) {
                1 -> 0.040
                2 -> -0.040
                else -> 0.0
            }
            val pitchMotion = when (index % 5) {
                3 -> -0.034
                4 -> 0.034
                else -> 0.0
            }
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] = 0.02
                    this[FeatureType.RightEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                },
                timestampMs = 3_000L + index * 33L
            )
        }
    }

    private fun rightEyePatchLiveFrames(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 14).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val yawMotion = when (index % 5) {
                1 -> 0.040
                2 -> -0.040
                else -> 0.0
            }
            val pitchMotion = when (index % 5) {
                3 -> -0.034
                4 -> 0.034
                else -> 0.0
            }
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.RightEyeOpen.ordinal] = 0.02
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                },
                timestampMs = 3_000L + index * 33L
            )
        }
    }
    private fun jitteryLiveFrames(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 16).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val isOutlier = index in setOf(4, 9, 13)
            val yawMotion = when (index % 5) {
                1 -> 0.040
                2 -> -0.040
                else -> 0.0
            }
            val pitchMotion = when (index % 5) {
                3 -> -0.034
                4 -> 0.034
                else -> 0.0
            }
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.014
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                    if (isOutlier) {
                        this[FeatureType.EyeDistance.ordinal] += 0.034
                        this[FeatureType.BrowDistance.ordinal] -= 0.026
                        this[FeatureType.NoseWidth.ordinal] += 0.030
                        this[FeatureType.FaceAspect.ordinal] -= 0.055
                    }
                },
                timestampMs = 3000L + index * 33L
            )
        }
    }

    private companion object {
        val BASE_FACE = completeFeatureVector(
            0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.02, 0.03, 0.00,
            0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03,
            0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000
        )

        val IMPOSTOR_FACE = completeFeatureVector(
            0.58, 0.120, 0.30, 0.42, 0.52, 1.80, 0.08, 0.10, 0.29, 1.05, 0.10, -0.12, 0.05,
            0.27, 0.13, 0.14, 0.33, 0.20, 0.09, 0.34, 0.42, 0.39, 0.12,
            0.94, 0.88, 0.49, 0.43, 0.06, 0.120, 0.115, 0.005, 0.320, 0.300, 0.020
        )

        val SINGLE_FEATURE_LOOKALIKE = completeFeatureVector(
            0.48, 0.135, 0.31, 0.41, 0.53, 1.82, 0.08, 0.10, 0.30, 1.04, 0.11, -0.13, 0.06,
            0.27, 0.13, 0.14, 0.30, 0.20, 0.08, 0.33, 0.41, 0.38, 0.11,
            0.91, 0.86, 0.47, 0.41, 0.06, 0.118, 0.112, 0.006, 0.330, 0.310, 0.020
        )

        val GLASSES_EYE_OUTLIER = completeFeatureVector(
            0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.05, 0.05, 0.18, 1.28, 0.02, 0.03, 0.00,
            0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03,
            0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000
        )
    }

    private class TestClock {
        private var nowMs: Long = 0L

        fun now(): Long = nowMs

        fun set(value: Long) {
            nowMs = value
        }
    }
}










