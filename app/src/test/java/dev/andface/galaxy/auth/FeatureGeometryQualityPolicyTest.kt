package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector
import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureGeometryQualityPolicyTest {
    @Test
    fun contourFeaturesParticipateInAccessQualityCheck() {
        val invalid = baseFrame().withFeature(FeatureType.UpperFacePerimeterRatio, 1.95)

        assertEquals(
            FailureReason.POOR_FACE_QUALITY,
            FeatureGeometryQualityPolicy.validateForAccess(invalid, OcclusionHint())
        )
    }

    @Test
    fun contourFeaturesParticipateInCleanEnrollmentQualityCheck() {
        val suspiciousCleanEnrollment = baseFrame().withFeature(FeatureType.EyeLineBrowLineGap, 0.50)

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            FeatureGeometryQualityPolicy.validateForCleanEnrollment(suspiciousCleanEnrollment)
        )
    }

    @Test
    fun eyePatchAccessSkipsCoveredEyeContourQualityRanges() {
        val coveredLeftEye = baseFrame().withFeature(FeatureType.LeftOuterEyeNoseRoot, 1.10)

        assertEquals(
            FailureReason.NONE,
            FeatureGeometryQualityPolicy.validateForAccess(coveredLeftEye, OcclusionHint(leftEyePatch = true))
        )
    }
    @Test
    fun periocularFeaturesParticipateInAccessQualityCheck() {
        val invalid = baseFrame().withFeature(FeatureType.BrowSpanEyeSpanRatio, 1.80)

        assertEquals(
            FailureReason.POOR_FACE_QUALITY,
            FeatureGeometryQualityPolicy.validateForAccess(invalid, OcclusionHint())
        )
    }

    @Test
    fun eyePatchAccessSkipsCoveredOrbitalQualityRanges() {
        val coveredLeftEye = baseFrame().withFeature(FeatureType.LeftOrbitalTriangle, 0.22)

        assertEquals(
            FailureReason.NONE,
            FeatureGeometryQualityPolicy.validateForAccess(coveredLeftEye, OcclusionHint(leftEyePatch = true))
        )
    }

    @Test
    fun eyelidAndNoseBridgeFeaturesParticipateInAccessQualityCheck() {
        val invalidEyelid = baseFrame().withFeature(FeatureType.LeftUpperEyelidArch, 0.30)
        val invalidNoseBridge = baseFrame().withFeature(FeatureType.NoseBridgeEyeLineOffset, 0.48)

        assertEquals(
            FailureReason.POOR_FACE_QUALITY,
            FeatureGeometryQualityPolicy.validateForAccess(invalidEyelid, OcclusionHint())
        )
        assertEquals(
            FailureReason.POOR_FACE_QUALITY,
            FeatureGeometryQualityPolicy.validateForAccess(invalidNoseBridge, OcclusionHint())
        )
    }

    @Test
    fun eyePatchAccessSkipsCoveredEyelidQualityRanges() {
        val coveredLeftEye = baseFrame().withFeature(FeatureType.LeftUpperEyelidArch, 0.30)

        assertEquals(
            FailureReason.NONE,
            FeatureGeometryQualityPolicy.validateForAccess(coveredLeftEye, OcclusionHint(leftEyePatch = true))
        )
    }

    @Test
    fun eyelidAndNoseBridgeFeaturesParticipateInCleanEnrollmentQualityCheck() {
        val suspiciousCleanEnrollment = baseFrame().withFeature(FeatureType.NoseBridgeEyeLineOffset, 0.36)

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            FeatureGeometryQualityPolicy.validateForCleanEnrollment(suspiciousCleanEnrollment)
        )
    }
    private fun RawFeatureFrame.withFeature(type: FeatureType, value: Double): RawFeatureFrame {
        val copy = values.copyOf()
        copy[type.ordinal] = value
        return copy(values = copy)
    }

    private fun baseFrame(): RawFeatureFrame {
        return RawFeatureFrame(
            values = completeFeatureVector(
                0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.02, 0.03, 0.00,
                0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03,
                0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000
            ),
            timestampMs = 1_000L,
            quality = FaceFrameQuality.TRUSTED
        )
    }
}
