package dev.andface.galaxy.enrollment

import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentBuilderTest {
    @Test
    fun bindsProfileToCurrentModelAndPolicyVersion() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            RawFeatureFrame(
                values = DoubleArray(FeatureType.COUNT) { featureIndex ->
                    0.5 + (index * 0.0001) + (featureIndex * 0.00001)
                },
                timestampMs = index.toLong()
            )
        }

        val profile = EnrollmentBuilder.build("USER_1", samples)

        assertEquals(BuildConfig.FACE_LANDMARKER_MODEL_SHA256, profile.modelSha256)
        assertEquals(EnrollmentProfile.CURRENT_POLICY_VERSION, profile.policyVersion)
        assertEquals(16, profile.policyVersion)
    }

    @Test
    fun marksOptionalIrisFeaturesUnavailableWhenEnrollmentSamplesLackIrisLandmarks() {
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            RawFeatureFrame(
                values = DoubleArray(FeatureType.COUNT) { featureIndex ->
                    0.35 + (featureIndex * 0.001) + (index % 3) * 0.0001
                },
                timestampMs = index.toLong(),
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = false)
            )
        }

        val profile = EnrollmentBuilder.build("USER_1", samples)

        FeatureType.ordered.filter { it.isIrisFeature }.forEach { type ->
            assertTrue(
                "expected $type to be marked as unreliable when enrollment lacks iris landmarks",
                profile.sigma(type) >= type.minimumSigma * 40.0
            )
        }
        assertTrue(
            "non-iris features should keep normal enrollment confidence",
            profile.sigma(FeatureType.EyeDistance) <= FeatureType.EyeDistance.minimumSigma * 1.25
        )
    }
    @Test
    fun consensusFilteringKeepsRegistrationStatisticsStableWhenLandmarkFramesSpike() {
        val base = DoubleArray(FeatureType.COUNT) { index ->
            0.30 + index * 0.001
        }.apply {
            this[FeatureType.EyeDistance.ordinal] = 0.48
            this[FeatureType.BrowDistance.ordinal] = 0.075
            this[FeatureType.NoseWidth.ordinal] = 0.22
            this[FeatureType.InnerEyeDistance.ordinal] = 0.18
            this[FeatureType.LeftEyeWidth.ordinal] = 0.20
            this[FeatureType.RightEyeWidth.ordinal] = 0.19
            this[FeatureType.LeftEyeHeight.ordinal] = 0.067
            this[FeatureType.RightEyeHeight.ordinal] = 0.067
            this[FeatureType.BrowWidth.ordinal] = 0.12
            this[FeatureType.NoseBridgeLength.ordinal] = 0.22
            this[FeatureType.EyeNoseLeft.ordinal] = 0.28
            this[FeatureType.EyeNoseRight.ordinal] = 0.27
            this[FeatureType.UpperFaceWidth.ordinal] = 0.82
            this[FeatureType.CheekboneWidth.ordinal] = 0.78
            this[FeatureType.UpperFaceAspect.ordinal] = 0.36
            this[FeatureType.LeftBrowEyeDistance.ordinal] = 0.10
            this[FeatureType.RightBrowEyeDistance.ordinal] = 0.10
            this[FeatureType.BrowEyeDistanceAsymmetry.ordinal] = 0.00
            this[FeatureType.NoseRootEyeLineDistance.ordinal] = 0.02
            this[FeatureType.NoseTipEyeLineDistance.ordinal] = 0.20
            this[FeatureType.NoseLateralOffset.ordinal] = 0.01
            this[FeatureType.LeftPeriocularArea.ordinal] = 0.012
            this[FeatureType.RightPeriocularArea.ordinal] = 0.013
            this[FeatureType.PeriocularAreaAsymmetry.ordinal] = 0.001
            this[FeatureType.LeftMidFaceTriangle.ordinal] = 0.055
            this[FeatureType.RightMidFaceTriangle.ordinal] = 0.057
            this[FeatureType.MidFaceTriangleAsymmetry.ordinal] = 0.002
            this[FeatureType.LeftIrisEyeOffset.ordinal] = 0.020
            this[FeatureType.RightIrisEyeOffset.ordinal] = 0.021
            this[FeatureType.IrisOffsetAsymmetry.ordinal] = 0.001
            this[FeatureType.IrisDistance.ordinal] = 0.48
            this[FeatureType.LeftIrisNoseRoot.ordinal] = 0.18
            this[FeatureType.RightIrisNoseRoot.ordinal] = 0.18
            this[FeatureType.IrisNoseRootAsymmetry.ordinal] = 0.00
            this[FeatureType.IrisSpanRatio.ordinal] = 1.00
        }
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = base.copyOf().apply {
                val cleanMotion = ((index % 5) - 2) * 0.0004
                this[FeatureType.EyeDistance.ordinal] += cleanMotion
                this[FeatureType.BrowDistance.ordinal] -= cleanMotion * 0.5
                this[FeatureType.NoseWidth.ordinal] += cleanMotion * 0.4
                if (index in 6 until 12) {
                    this[FeatureType.EyeDistance.ordinal] += 0.120
                    this[FeatureType.BrowDistance.ordinal] += 0.070
                    this[FeatureType.NoseWidth.ordinal] += 0.080
                    this[FeatureType.InnerEyeDistance.ordinal] += 0.090
                    this[FeatureType.LeftEyeWidth.ordinal] += 0.090
                    this[FeatureType.RightEyeWidth.ordinal] += 0.090
                    this[FeatureType.BrowWidth.ordinal] += 0.090
                    this[FeatureType.NoseBridgeLength.ordinal] += 0.090
                    this[FeatureType.EyeNoseLeft.ordinal] += 0.090
                    this[FeatureType.EyeNoseRight.ordinal] += 0.090
                    this[FeatureType.UpperFaceWidth.ordinal] += 0.160
                    this[FeatureType.CheekboneWidth.ordinal] += 0.160
                    this[FeatureType.UpperFaceAspect.ordinal] += 0.150
                    this[FeatureType.LeftBrowEyeDistance.ordinal] += 0.140
                    this[FeatureType.RightBrowEyeDistance.ordinal] += 0.130
                    this[FeatureType.BrowEyeDistanceAsymmetry.ordinal] += 0.120
                    this[FeatureType.NoseRootEyeLineDistance.ordinal] += 0.110
                    this[FeatureType.NoseTipEyeLineDistance.ordinal] += 0.150
                    this[FeatureType.NoseLateralOffset.ordinal] += 0.130
                    this[FeatureType.LeftPeriocularArea.ordinal] += 0.080
                    this[FeatureType.RightPeriocularArea.ordinal] += 0.080
                    this[FeatureType.PeriocularAreaAsymmetry.ordinal] += 0.070
                    this[FeatureType.LeftMidFaceTriangle.ordinal] += 0.120
                    this[FeatureType.RightMidFaceTriangle.ordinal] += 0.120
                    this[FeatureType.MidFaceTriangleAsymmetry.ordinal] += 0.100
                    this[FeatureType.LeftIrisEyeOffset.ordinal] += 0.200
                    this[FeatureType.RightIrisEyeOffset.ordinal] += 0.190
                    this[FeatureType.IrisOffsetAsymmetry.ordinal] += 0.180
                    this[FeatureType.IrisDistance.ordinal] += 0.220
                    this[FeatureType.LeftIrisNoseRoot.ordinal] += 0.160
                    this[FeatureType.RightIrisNoseRoot.ordinal] += 0.160
                    this[FeatureType.IrisNoseRootAsymmetry.ordinal] += 0.140
                    this[FeatureType.IrisSpanRatio.ordinal] += 0.260
                }
            }
            RawFeatureFrame(values = values, timestampMs = index * 33L)
        }

        val profile = EnrollmentBuilder.build("USER_1", samples)

        assertEquals(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT, profile.sampleCount)
        assertEquals(base[FeatureType.EyeDistance.ordinal], profile.mean(FeatureType.EyeDistance), 0.003)
        assertEquals(base[FeatureType.NoseWidth.ordinal], profile.mean(FeatureType.NoseWidth), 0.003)
        assertTrue(
            "expected eye-distance sigma to stay near enrollment floor but was ${profile.sigma(FeatureType.EyeDistance)}",
            profile.sigma(FeatureType.EyeDistance) <= FeatureType.EyeDistance.minimumSigma * 1.25
        )
        assertTrue(
            "expected upper-face sigma to stay bounded but was ${profile.sigma(FeatureType.UpperFaceWidth)}",
            profile.sigma(FeatureType.UpperFaceWidth) <= FeatureType.UpperFaceWidth.minimumSigma * 1.25
        )
        assertEquals(base[FeatureType.LeftBrowEyeDistance.ordinal], profile.mean(FeatureType.LeftBrowEyeDistance), 0.003)
        assertEquals(base[FeatureType.NoseRootEyeLineDistance.ordinal], profile.mean(FeatureType.NoseRootEyeLineDistance), 0.003)
        assertEquals(base[FeatureType.LeftPeriocularArea.ordinal], profile.mean(FeatureType.LeftPeriocularArea), 0.003)
        assertEquals(base[FeatureType.LeftMidFaceTriangle.ordinal], profile.mean(FeatureType.LeftMidFaceTriangle), 0.003)
        assertEquals(base[FeatureType.IrisDistance.ordinal], profile.mean(FeatureType.IrisDistance), 0.003)
        assertTrue(
            "expected detailed brow-eye sigma to stay bounded but was ${profile.sigma(FeatureType.LeftBrowEyeDistance)}",
            profile.sigma(FeatureType.LeftBrowEyeDistance) <= FeatureType.LeftBrowEyeDistance.minimumSigma * 1.25
        )
        assertTrue(
            "expected periocular-area sigma to stay bounded but was ${profile.sigma(FeatureType.LeftPeriocularArea)}",
            profile.sigma(FeatureType.LeftPeriocularArea) <= FeatureType.LeftPeriocularArea.minimumSigma * 1.25
        )
        assertTrue(
            "expected iris-distance sigma to stay bounded but was ${profile.sigma(FeatureType.IrisDistance)}",
            profile.sigma(FeatureType.IrisDistance) <= FeatureType.IrisDistance.minimumSigma * 1.25
        )
    }
    @Test
    fun consensusFilteringUsesNewestContourFeaturesWhenDroppingSpikeFrames() {
        val base = DoubleArray(FeatureType.COUNT) { index ->
            0.24 + index * 0.0004
        }.apply {
            this[FeatureType.EyeLineBrowLineGap.ordinal] = 0.10
            this[FeatureType.LeftOuterEyeNoseRoot.ordinal] = 0.25
            this[FeatureType.RightOuterEyeNoseRoot.ordinal] = 0.25
            this[FeatureType.OuterEyeNoseRootAsymmetry.ordinal] = 0.00
            this[FeatureType.LeftEyeForeheadDistance.ordinal] = 0.42
            this[FeatureType.RightEyeForeheadDistance.ordinal] = 0.42
            this[FeatureType.EyeForeheadAsymmetry.ordinal] = 0.00
            this[FeatureType.NoseRootForeheadDistance.ordinal] = 0.32
            this[FeatureType.UpperFaceDiagonalRatio.ordinal] = 0.60
            this[FeatureType.UpperFacePerimeterRatio.ordinal] = 0.82
            this[FeatureType.LeftJawCheekDistance.ordinal] = 0.12
            this[FeatureType.RightJawCheekDistance.ordinal] = 0.12
            this[FeatureType.JawCheekAsymmetry.ordinal] = 0.00
            this[FeatureType.ChinLateralOffset.ordinal] = 0.04
            this[FeatureType.LeftOrbitalTriangle.ordinal] = 0.012
            this[FeatureType.RightOrbitalTriangle.ordinal] = 0.012
            this[FeatureType.OrbitalTriangleAsymmetry.ordinal] = 0.00
            this[FeatureType.LeftInnerBrowNoseArea.ordinal] = 0.006
            this[FeatureType.RightInnerBrowNoseArea.ordinal] = 0.006
            this[FeatureType.InnerBrowNoseAreaAsymmetry.ordinal] = 0.00
            this[FeatureType.BrowSpanEyeSpanRatio.ordinal] = 1.00
            this[FeatureType.NoseRootToEyeSpanRatio.ordinal] = 0.02
        }
        val newestContourTypes = listOf(
            FeatureType.EyeLineBrowLineGap,
            FeatureType.LeftOuterEyeNoseRoot,
            FeatureType.RightOuterEyeNoseRoot,
            FeatureType.OuterEyeNoseRootAsymmetry,
            FeatureType.LeftEyeForeheadDistance,
            FeatureType.RightEyeForeheadDistance,
            FeatureType.EyeForeheadAsymmetry,
            FeatureType.NoseRootForeheadDistance,
            FeatureType.UpperFaceDiagonalRatio,
            FeatureType.UpperFacePerimeterRatio,
            FeatureType.LeftJawCheekDistance,
            FeatureType.RightJawCheekDistance,
            FeatureType.JawCheekAsymmetry,
            FeatureType.ChinLateralOffset,
            FeatureType.LeftOrbitalTriangle,
            FeatureType.RightOrbitalTriangle,
            FeatureType.OrbitalTriangleAsymmetry,
            FeatureType.LeftInnerBrowNoseArea,
            FeatureType.RightInnerBrowNoseArea,
            FeatureType.InnerBrowNoseAreaAsymmetry,
            FeatureType.BrowSpanEyeSpanRatio,
            FeatureType.NoseRootToEyeSpanRatio
        )
        val samples = List(EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT) { index ->
            val values = base.copyOf().apply {
                val cleanMotion = ((index % 5) - 2) * 0.00035
                newestContourTypes.forEach { type ->
                    this[type.ordinal] += cleanMotion
                }
                if (index in 8 until 14) {
                    this[FeatureType.EyeLineBrowLineGap.ordinal] += 0.16
                    this[FeatureType.LeftOuterEyeNoseRoot.ordinal] += 0.18
                    this[FeatureType.RightOuterEyeNoseRoot.ordinal] += 0.18
                    this[FeatureType.OuterEyeNoseRootAsymmetry.ordinal] += 0.18
                    this[FeatureType.LeftEyeForeheadDistance.ordinal] += 0.18
                    this[FeatureType.RightEyeForeheadDistance.ordinal] += 0.18
                    this[FeatureType.EyeForeheadAsymmetry.ordinal] += 0.18
                    this[FeatureType.NoseRootForeheadDistance.ordinal] += 0.18
                    this[FeatureType.UpperFaceDiagonalRatio.ordinal] += 0.22
                    this[FeatureType.UpperFacePerimeterRatio.ordinal] += 0.22
                    this[FeatureType.LeftJawCheekDistance.ordinal] += 0.16
                    this[FeatureType.RightJawCheekDistance.ordinal] += 0.16
                    this[FeatureType.JawCheekAsymmetry.ordinal] += 0.16
                    this[FeatureType.ChinLateralOffset.ordinal] += 0.16
                    this[FeatureType.LeftOrbitalTriangle.ordinal] += 0.12
                    this[FeatureType.RightOrbitalTriangle.ordinal] += 0.12
                    this[FeatureType.OrbitalTriangleAsymmetry.ordinal] += 0.12
                    this[FeatureType.LeftInnerBrowNoseArea.ordinal] += 0.10
                    this[FeatureType.RightInnerBrowNoseArea.ordinal] += 0.10
                    this[FeatureType.InnerBrowNoseAreaAsymmetry.ordinal] += 0.10
                    this[FeatureType.BrowSpanEyeSpanRatio.ordinal] += 0.26
                    this[FeatureType.NoseRootToEyeSpanRatio.ordinal] += 0.18
                }
            }
            RawFeatureFrame(values = values, timestampMs = index * 33L)
        }

        val profile = EnrollmentBuilder.build("USER_1", samples)

        newestContourTypes.forEach { type ->
            assertEquals(
                "expected newest contour feature $type to be centered from retained clean frames",
                base[type.ordinal],
                profile.mean(type),
                0.003
            )
            assertTrue(
                "expected newest contour sigma for $type to stay bounded but was ${profile.sigma(type)}",
                profile.sigma(type) <= type.minimumSigma * 1.25
            )
        }
    }
}

