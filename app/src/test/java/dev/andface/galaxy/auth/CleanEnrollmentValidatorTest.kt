package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector

import dev.andface.galaxy.enrollment.EnrollmentSecurityPolicy
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.FeatureGroup
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanEnrollmentValidatorTest {
    @Test
    fun enrollmentRejectsAnyManualOcclusionHint() {
        val frame = cleanFrame()

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(frame, OcclusionHint(lowerFaceCovered = true))
        )
        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(frame, OcclusionHint(glasses = true))
        )
        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(frame, OcclusionHint(leftEyePatch = true))
        )
    }

    @Test
    fun enrollmentRejectsNonFiniteFeatureValues() {
        val values = cleanValues()
        values[FeatureType.NoseWidth.ordinal] = Double.NaN

        assertEquals(
            FailureReason.TOO_FEW_FEATURES,
            CleanEnrollmentValidator.validateSample(RawFeatureFrame(values, timestampMs = 0L), OcclusionHint())
        )
    }

    @Test
    fun enrollmentRejectsNonFrontalSample() {
        val values = cleanValues()
        values[FeatureType.Yaw.ordinal] = 0.70

        assertEquals(
            FailureReason.LOW_COVERAGE,
            CleanEnrollmentValidator.validateSample(RawFeatureFrame(values, timestampMs = 0L), OcclusionHint())
        )
    }

    @Test
    fun enrollmentRejectsImplausibleMaskedLikeGeometryEvenWithoutHint() {
        val values = cleanValues()
        values[FeatureType.MouthWidth.ordinal] = 0.04

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(RawFeatureFrame(values, timestampMs = 0L), OcclusionHint())
        )
    }

    @Test
    fun enrollmentRejectsImplausibleS147EyelidOrNoseBridgeGeometryEvenWithoutHint() {
        val eyelidValues = cleanValues().apply {
            this[FeatureType.LeftUpperEyelidArch.ordinal] = 0.24
        }
        val noseBridgeValues = cleanValues().apply {
            this[FeatureType.NoseBridgeEyeLineOffset.ordinal] = 0.36
        }

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(RawFeatureFrame(eyelidValues, timestampMs = 0L), OcclusionHint())
        )
        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateSample(RawFeatureFrame(noseBridgeValues, timestampMs = 0L), OcclusionHint())
        )
    }
    @Test
    fun enrollmentBaselineRequiresEnoughLiveVariation() {
        val staticSamples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            RawFeatureFrame(cleanValues(), timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.LOW_LIVENESS,
            CleanEnrollmentValidator.validateBaseline(
                staticSamples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }


    @Test
    fun enrollmentBaselineRejectsPersistentEyePatchLikeSamples() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = cleanValues()
            values[FeatureType.LeftEyeOpen.ordinal] = 0.02
            values[FeatureType.RightEyeOpen.ordinal] += if (index % 2 == 0) 0.016 else -0.010
            values[FeatureType.EyeOpenAsymmetry.ordinal] = 1.30
            values[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
            values[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }

    @Test
    fun enrollmentBaselineRejectsPersistentEyelidArchPatchLikeSamples() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = liveCleanValues(index)
            values[FeatureType.LeftUpperEyelidArch.ordinal] = 0.01
            values[FeatureType.RightUpperEyelidArch.ordinal] = 0.11
            values[FeatureType.UpperEyelidArchAsymmetry.ordinal] = 0.35
            values[FeatureType.LeftLowerEyelidArch.ordinal] = 0.01
            values[FeatureType.RightLowerEyelidArch.ordinal] = 0.11
            values[FeatureType.LowerEyelidArchAsymmetry.ordinal] = 0.35
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.OCCLUDED_DURING_ENROLLMENT,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }
    @Test
    fun enrollmentBaselineRejectsUnstableIrisLandmarkSamples() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = cleanValues()
            values[FeatureType.LeftEyeOpen.ordinal] += if (index % 2 == 0) 0.018 else -0.018
            values[FeatureType.RightEyeOpen.ordinal] += if (index % 3 == 0) 0.016 else -0.010
            values[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
            values[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
            val irisWave = if (index % 2 == 0) 1.0 else -1.0
            values[FeatureType.IrisDistance.ordinal] += irisWave * 0.10
            values[FeatureType.LeftIrisNoseRoot.ordinal] += irisWave * 0.08
            values[FeatureType.RightIrisNoseRoot.ordinal] -= irisWave * 0.08
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.UNSTABLE_ENROLLMENT,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }

    @Test
    fun enrollmentBaselineRejectsRegionWideObservableIdentityInstability() {
        val unstableTypes = FeatureType.ordered.filter { type ->
            type.group == FeatureGroup.UPPER_FACE ||
                type.group == FeatureGroup.MID_FACE ||
                type.group == FeatureGroup.EYE
        }
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = liveCleanValues(index)
            val wave = if (index % 2 == 0) 1.0 else -1.0
            unstableTypes.forEach { type ->
                values[type.ordinal] += wave * type.minimumSigma * 4.1
            }
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.UNSTABLE_ENROLLMENT,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }

    @Test
    fun enrollmentBaselineToleratesLocalizedDetailNoiseWhenRegionsRemainStable() {
        val noisyDetailTypes = listOf(
            FeatureType.LeftTempleBrowDistance,
            FeatureType.RightBrowTempleSlope,
            FeatureType.UpperFacePerimeterRatio
        )
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = liveCleanValues(index)
            val wave = if (index % 2 == 0) 1.0 else -1.0
            noisyDetailTypes.forEach { type ->
                values[type.ordinal] += wave * type.minimumSigma * 4.1
            }
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.NONE,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }

    @Test
    fun enrollmentBaselineAcceptsCleanSamplesWithSmallLiveMotion() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = cleanValues()
            values[FeatureType.LeftEyeOpen.ordinal] += if (index % 2 == 0) 0.018 else -0.018
            values[FeatureType.RightEyeOpen.ordinal] += if (index % 3 == 0) 0.016 else -0.010
            values[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
            values[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
            RawFeatureFrame(values, timestampMs = index.toLong())
        }

        assertEquals(
            FailureReason.NONE,
            CleanEnrollmentValidator.validateBaseline(
                samples,
                EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
            )
        )
    }

    private fun liveCleanValues(index: Int): DoubleArray {
        val values = cleanValues()
        values[FeatureType.LeftEyeOpen.ordinal] += if (index % 2 == 0) 0.018 else -0.018
        values[FeatureType.RightEyeOpen.ordinal] += if (index % 3 == 0) 0.016 else -0.010
        values[FeatureType.Yaw.ordinal] += (index % 5 - 2) * 0.012
        values[FeatureType.Pitch.ordinal] += (index % 7 - 3) * 0.010
        return values
    }

    private fun cleanFrame(): RawFeatureFrame {
        return RawFeatureFrame(cleanValues(), timestampMs = 0L)
    }

    private fun cleanValues(): DoubleArray {
        return completeFeatureVector(
            0.50, 0.12, 0.24, 0.45, 0.42, 1.70, 0.10,
            0.10, 0.20, 1.05, 0.00, 0.00, 0.00,
            0.20, 0.20, 0.20, 0.00, 0.12, 0.00, 0.20, 0.26, 0.26, 0.00,
            0.80, 0.72, 0.36, 0.36, 0.00, 0.120, 0.120, 0.000, 0.280, 0.280, 0.000
        )
    }
}


