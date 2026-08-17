package dev.andface.galaxy.feature

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceFeatureExtractorTest {
    private val extractor = FaceFeatureExtractor()

    @Test
    fun rejectsFramesWithFewerThanFourHundredSixtyEightLandmarks() {
        val result = extractor.extract(
            landmarks = frontFaceLandmarks().take(467),
            facialTransformMatrix = null,
            timestampMs = 1_000L
        )

        assertNull(result)
    }

    @Test
    fun extractsExtendedNormalizedFeaturesFromFourHundredSixtyEightLandmarks() {
        val result = extractor.extract(
            landmarks = frontFaceLandmarks(),
            facialTransformMatrix = null,
            timestampMs = 1_000L
        )

        assertNotNull(result)
        val frame = requireNotNull(result)
        assertEquals(FeatureType.COUNT, frame.values.size)
        assertEquals(170, FeatureType.COUNT)
        assertTrue(frame.values.all { it.isFinite() })
        assertEquals(0.40, frame.value(FeatureType.EyeDistance), 1e-6)
        assertEquals(0.16, frame.value(FeatureType.NoseWidth), 1e-6)
        assertEquals(0.50, frame.value(FeatureType.NoseToChin), 1e-6)
        assertEquals(0.32, frame.value(FeatureType.MouthWidth), 1e-6)
        assertEquals(2.50, frame.value(FeatureType.JawWidth), 1e-6)
        assertEquals(1.20, frame.value(FeatureType.FaceAspect), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.Yaw), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.Pitch), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.Roll), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.InnerEyeDistance), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.LeftEyeWidth), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.RightEyeWidth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeOpenAsymmetry), 1e-6)
        assertEquals(0.12, frame.value(FeatureType.BrowWidth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowAsymmetry), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.NoseBridgeLength), 1e-6)
        assertEquals(0.260341, frame.value(FeatureType.EyeNoseLeft), 1e-6)
        assertEquals(0.260341, frame.value(FeatureType.EyeNoseRight), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeNoseSymmetry), 1e-6)
        assertEquals(0.80, frame.value(FeatureType.UpperFaceWidth), 1e-6)
        assertEquals(0.72, frame.value(FeatureType.CheekboneWidth), 1e-6)
        assertEquals(0.362215, frame.value(FeatureType.LeftCheekNose), 1e-6)
        assertEquals(0.362215, frame.value(FeatureType.RightCheekNose), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.CheekNoseSymmetry), 1e-6)
        assertEquals(0.066667, frame.value(FeatureType.LeftBrowEyeGap), 1e-6)
        assertEquals(0.066667, frame.value(FeatureType.RightBrowEyeGap), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowEyeGapAsymmetry), 1e-6)
        assertEquals(0.268742, frame.value(FeatureType.LeftUnderEyeCheek), 1e-6)
        assertEquals(0.268742, frame.value(FeatureType.RightUnderEyeCheek), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.UnderEyeCheekAsymmetry), 1e-6)
        assertEquals(0.40, frame.value(FeatureType.InterBrowDistance), 1e-6)
        assertEquals(0.194365, frame.value(FeatureType.LeftBrowNoseRoot), 1e-6)
        assertEquals(0.194365, frame.value(FeatureType.RightBrowNoseRoot), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowNoseRootAsymmetry), 1e-6)
        assertEquals(0.083333, frame.value(FeatureType.LeftInnerEyeNoseRoot), 1e-6)
        assertEquals(0.083333, frame.value(FeatureType.RightInnerEyeNoseRoot), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.InnerEyeNoseRootAsymmetry), 1e-6)
        assertEquals(0.188680, frame.value(FeatureType.LeftTempleEye), 1e-6)
        assertEquals(0.188680, frame.value(FeatureType.RightTempleEye), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.TempleEyeAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeWidthAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LeftBrowSlope), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.RightBrowSlope), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowSlopeAsymmetry), 1e-6)
        assertEquals(0.066667, frame.value(FeatureType.LeftEyeHeight), 1e-6)
        assertEquals(0.066667, frame.value(FeatureType.RightEyeHeight), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeHeightAsymmetry), 1e-6)
        assertEquals(0.12, frame.value(FeatureType.LeftBrowWidth), 1e-6)
        assertEquals(0.12, frame.value(FeatureType.RightBrowWidth), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.LeftBrowOuterEyeGap), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.RightBrowOuterEyeGap), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowOuterGapAsymmetry), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.LeftBrowInnerEyeGap), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.RightBrowInnerEyeGap), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowInnerGapAsymmetry), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.NoseRootToBrowLine), 1e-6)
        assertEquals(0.30, frame.value(FeatureType.ForeheadToEyeLine), 1e-6)
        assertEquals(0.45, frame.value(FeatureType.UpperFaceAspect), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.LeftBrowEyeDistance), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.RightBrowEyeDistance), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowEyeDistanceAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseRootEyeLineDistance), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.NoseTipEyeLineDistance), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseLateralOffset), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.LeftPeriocularArea), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.RightPeriocularArea), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.PeriocularAreaAsymmetry), 1e-6)
        assertEquals(0.039333, frame.value(FeatureType.LeftMidFaceTriangle), 1e-6)
        assertEquals(0.039333, frame.value(FeatureType.RightMidFaceTriangle), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.MidFaceTriangleAsymmetry), 1e-6)
        assertEquals(0.035, frame.value(FeatureType.LeftUpperMidfaceArea), 1e-6)
        assertEquals(0.035, frame.value(FeatureType.RightUpperMidfaceArea), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.UpperMidfaceAreaAsymmetry), 1e-6)
        assertEquals(0.036, frame.value(FeatureType.LeftNoseCheekArea), 1e-6)
        assertEquals(0.036, frame.value(FeatureType.RightNoseCheekArea), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseCheekAreaAsymmetry), 1e-6)
        assertEquals(0.995893, frame.value(FeatureType.LeftTempleCheekSlope), 1e-6)
        assertEquals(0.995893, frame.value(FeatureType.RightTempleCheekSlope), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.TempleCheekSlopeAsymmetry), 1e-6)
        assertEquals(0.90, frame.value(FeatureType.MidfaceWidthRatio), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.LeftBrowArchHeight), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.RightBrowArchHeight), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowArchAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowLineTilt), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LeftEyeCornerTilt), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.RightEyeCornerTilt), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeCornerTiltAsymmetry), 1e-6)
        assertEquals(0.02, frame.value(FeatureType.InnerEyeNoseTipTriangle), 1e-6)
        assertEquals(0.02, frame.value(FeatureType.BrowNoseRootTriangle), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseRootLateralOffset), 1e-6)
        assertEquals(0.203961, frame.value(FeatureType.LeftTempleBrowDistance), 1e-6)
        assertEquals(0.203961, frame.value(FeatureType.RightTempleBrowDistance), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.TempleBrowDistanceAsymmetry), 1e-6)
        assertEquals(0.04, frame.value(FeatureType.ForeheadBrowTriangle), 1e-6)
        assertEquals(0.06, frame.value(FeatureType.ForeheadEyeTriangle), 1e-6)
        assertEquals(0.50, frame.value(FeatureType.UpperFaceTaper), 1e-6)
        assertEquals(0.196116, frame.value(FeatureType.LeftBrowTempleSlope), 1e-6)
        assertEquals(0.196116, frame.value(FeatureType.RightBrowTempleSlope), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.BrowTempleSlopeAsymmetry), 1e-6)
        assertEquals(0.089443, frame.value(FeatureType.LeftNoseWing), 1e-6)
        assertEquals(0.089443, frame.value(FeatureType.RightNoseWing), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseWingAsymmetry), 1e-6)
        assertEquals(0.002667, frame.value(FeatureType.NoseBaseTriangle), 1e-6)
        assertEquals(0.066667, frame.value(FeatureType.MouthHeight), 1e-6)
        assertEquals(0.208333, frame.value(FeatureType.MouthAspect), 1e-6)
        assertEquals(0.266667, frame.value(FeatureType.ChinMouthDistance), 1e-6)
        assertEquals(0.50, frame.value(FeatureType.ChinJawOffset), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LeftIrisEyeOffset), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.RightIrisEyeOffset), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.IrisOffsetAsymmetry), 1e-6)
        assertEquals(0.40, frame.value(FeatureType.IrisDistance), 1e-6)
        assertEquals(0.166667, frame.value(FeatureType.LeftIrisNoseRoot), 1e-6)
        assertEquals(0.166667, frame.value(FeatureType.RightIrisNoseRoot), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.IrisNoseRootAsymmetry), 1e-6)
        assertEquals(1.0, frame.value(FeatureType.IrisSpanRatio), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.EyeLineBrowLineGap), 1e-6)
        assertEquals(0.25, frame.value(FeatureType.LeftOuterEyeNoseRoot), 1e-6)
        assertEquals(0.25, frame.value(FeatureType.RightOuterEyeNoseRoot), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.OuterEyeNoseRootAsymmetry), 1e-6)
        assertEquals(0.343188, frame.value(FeatureType.LeftEyeForeheadDistance), 1e-6)
        assertEquals(0.343188, frame.value(FeatureType.RightEyeForeheadDistance), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeForeheadAsymmetry), 1e-6)
        assertEquals(0.30, frame.value(FeatureType.NoseRootForeheadDistance), 1e-6)
        assertEquals(0.601332, frame.value(FeatureType.UpperFaceDiagonalRatio), 1e-6)
        assertEquals(0.773794, frame.value(FeatureType.UpperFacePerimeterRatio), 1e-6)
        assertEquals(0.121335, frame.value(FeatureType.LeftJawCheekDistance), 1e-6)
        assertEquals(0.121335, frame.value(FeatureType.RightJawCheekDistance), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.JawCheekAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.ChinLateralOffset), 1e-6)
        assertEquals(0.011667, frame.value(FeatureType.LeftOrbitalTriangle), 1e-6)
        assertEquals(0.011667, frame.value(FeatureType.RightOrbitalTriangle), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.OrbitalTriangleAsymmetry), 1e-6)
        assertEquals(0.005, frame.value(FeatureType.LeftInnerBrowNoseArea), 1e-6)
        assertEquals(0.005, frame.value(FeatureType.RightInnerBrowNoseArea), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.InnerBrowNoseAreaAsymmetry), 1e-6)
        assertEquals(1.0, frame.value(FeatureType.BrowSpanEyeSpanRatio), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseRootToEyeSpanRatio), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseTipDepth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LeftEyeNoseDepth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.RightEyeNoseDepth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeNoseDepthAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LeftCheekNoseDepth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.RightCheekNoseDepth), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.CheekNoseDepthAsymmetry), 1e-6)
        assertEquals(0.023333, frame.value(FeatureType.LeftForeheadTempleArea), 1e-6)
        assertEquals(0.023333, frame.value(FeatureType.RightForeheadTempleArea), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.ForeheadTempleAreaAsymmetry), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.LeftEyeBrowNoseTriangle), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.RightEyeBrowNoseTriangle), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.EyeBrowNoseTriangleAsymmetry), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseBridgeEyeTriangle), 1e-6)
        assertEquals(0.60, frame.value(FeatureType.NoseBridgeToEyeSpanRatio), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.LeftInnerEyeNoseBridgeTriangle), 1e-6)
        assertEquals(0.01, frame.value(FeatureType.RightInnerEyeNoseBridgeTriangle), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry), 1e-6)
        assertEquals(0.20, frame.value(FeatureType.NoseTipInnerEyeLineDistance), 1e-6)
        assertEquals(1.20, frame.value(FeatureType.NoseBridgeInnerEyeSpanRatio), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseTipDepthToEyeSpanRatio), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.LeftUpperEyelidArch), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.RightUpperEyelidArch), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.UpperEyelidArchAsymmetry), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.LeftLowerEyelidArch), 1e-6)
        assertEquals(0.033333, frame.value(FeatureType.RightLowerEyelidArch), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.LowerEyelidArchAsymmetry), 1e-6)
        assertEquals(0.10, frame.value(FeatureType.NoseBridgeBrowLineOffset), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.NoseBridgeEyeLineOffset), 1e-6)
        assertFalse(frame.quality.irisLandmarksAvailable)
        assertTrue(frame.quality.meshSymmetryScore > 0.98)
        assertTrue(frame.quality.landmarkTopologyScore > 0.98)
        assertTrue(frame.quality.acceptableForAccess)
    }

    @Test
    fun usesOptionalIrisLandmarksWhenAvailable() {
        val result = extractor.extract(
            landmarks = frontFaceLandmarksWithIris(),
            facialTransformMatrix = null,
            timestampMs = 1_000L
        )

        val frame = requireNotNull(result)
        assertEquals(0.053852, frame.value(FeatureType.LeftIrisEyeOffset), 1e-6)
        assertEquals(0.053852, frame.value(FeatureType.RightIrisEyeOffset), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.IrisOffsetAsymmetry), 1e-6)
        assertEquals(0.380084, frame.value(FeatureType.IrisDistance), 1e-6)
        assertEquals(0.158368, frame.value(FeatureType.LeftIrisNoseRoot), 1e-6)
        assertEquals(0.158368, frame.value(FeatureType.RightIrisNoseRoot), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.IrisNoseRootAsymmetry), 1e-6)
        assertEquals(0.950211, frame.value(FeatureType.IrisSpanRatio), 1e-6)
    }
    @Test
    fun mapsMediaPipeBlendshapesIntoFrameQualityWithoutAddingTemplateFeatures() {
        val result = extractor.extract(
            landmarks = frontFaceLandmarks(),
            facialTransformMatrix = null,
            timestampMs = 1_000L,
            faceBlendshapes = listOf(
                Category.create(0.02f, 0, "_neutral", ""),
                Category.create(0.64f, 1, "eyeBlinkLeft", ""),
                Category.create(0.11f, 2, "mouthSmileRight", "")
            )
        )

        val frame = requireNotNull(result)
        assertEquals(FeatureType.COUNT, frame.values.size)
        assertTrue(frame.quality.blendshapesAvailable)
        assertEquals(0.64, frame.quality.blendshapeActivityScore, 1e-6)
    }

    @Test
    fun usesFacialTransformMatrixForPoseWhenAvailable() {
        val result = extractor.extract(
            landmarks = frontFaceLandmarks(),
            facialTransformMatrix = yawRotationMatrix(degrees = 22.5),
            timestampMs = 1_000L
        )

        val frame = requireNotNull(result)
        assertEquals(0.50, frame.value(FeatureType.Yaw), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.Pitch), 1e-6)
        assertEquals(0.0, frame.value(FeatureType.Roll), 1e-6)
    }

    @Test
    fun marksHeavilyClippedLandmarkFramesAsPoorQuality() {
        val clipped = frontFaceLandmarks().toMutableList()
        repeat(40) { index ->
            clipped[index] = NormalizedLandmark.create(-0.20f, 0.50f, 0.0f)
        }

        val frame = requireNotNull(
            extractor.extract(
                landmarks = clipped,
                facialTransformMatrix = null,
                timestampMs = 1_000L
            )
        )

        assertFalse(frame.quality.acceptableForAccess)
        assertTrue(frame.quality.inFrameLandmarkRatio < 0.93)
    }

    @Test
    fun marksAsymmetricLandmarkMeshAsPoorQuality() {
        val warped = frontFaceLandmarks().toMutableList()
        warped[RIGHT_EYE_INNER] = point(0.58, 0.38)
        warped[RIGHT_EYE_OUTER] = point(0.98, 0.38)
        warped[RIGHT_CHEEK] = point(0.99, 0.86)
        warped[RIGHT_TEMPLE] = point(0.99, 0.18)
        warped[RIGHT_BROW_A] = point(0.60, 0.30)
        warped[RIGHT_BROW_B] = point(0.99, 0.46)
        warped[RIGHT_BROW_MID] = point(0.95, 0.50)

        val frame = requireNotNull(
            extractor.extract(
                landmarks = warped,
                facialTransformMatrix = null,
                timestampMs = 1_000L
            )
        )

        assertFalse(frame.quality.acceptableForAccess)
        assertTrue(frame.quality.meshSymmetryScore < 0.40)
    }

    @Test
    fun marksFoldedLandmarkTopologyAsPoorQuality() {
        val folded = frontFaceLandmarks().toMutableList()
        folded[MOUTH_LEFT] = point(0.42, 0.35)
        folded[MOUTH_RIGHT] = point(0.58, 0.35)
        folded[MOUTH_TOP] = point(0.50, 0.34)
        folded[MOUTH_BOTTOM] = point(0.50, 0.36)

        val frame = requireNotNull(
            extractor.extract(
                landmarks = folded,
                facialTransformMatrix = null,
                timestampMs = 1_000L
            )
        )

        assertFalse(frame.quality.acceptableForAccess)
        assertTrue(frame.quality.meshSymmetryScore > 0.80)
        assertTrue(frame.quality.landmarkTopologyScore < 0.70)
        assertEquals("noseMouth", frame.quality.landmarkTopologyHint)
    }

    @Test
    fun acceptsClosedMouthAsFlexibleApertureNotFoldedTopology() {
        val closedMouth = frontFaceLandmarks().toMutableList()
        closedMouth[MOUTH_TOP] = point(0.50, 0.64)
        closedMouth[MOUTH_BOTTOM] = point(0.50, 0.64)

        val frame = requireNotNull(
            extractor.extract(
                landmarks = closedMouth,
                facialTransformMatrix = null,
                timestampMs = 1_000L
            )
        )

        assertTrue(frame.quality.acceptableForAccess)
        assertTrue(frame.quality.landmarkTopologyScore > 0.90)
        assertEquals("mouthOpen", frame.quality.landmarkTopologyHint)
    }
    @Test
    fun acceptsHorizontallyMirroredFrontCameraLandmarkTopology() {
        val mirrored = frontFaceLandmarks().map { landmark ->
            NormalizedLandmark.create(1.0f - landmark.x(), landmark.y(), landmark.z())
        }

        val frame = requireNotNull(
            extractor.extract(
                landmarks = mirrored,
                facialTransformMatrix = null,
                timestampMs = 1_000L
            )
        )

        assertTrue(frame.quality.landmarkTopologyScore > 0.98)
        assertTrue(frame.quality.acceptableForAccess)
    }
    private fun frontFaceLandmarks(): List<NormalizedLandmark> {
        val landmarks = MutableList(REQUIRED_LANDMARKS) {
            NormalizedLandmark.create(0.50f, 0.50f, 0.0f)
        }

        landmarks[FOREHEAD] = point(0.50, 0.20)
        landmarks[CHIN] = point(0.50, 0.80)
        landmarks[JAW_LEFT] = point(0.25, 0.50)
        landmarks[JAW_RIGHT] = point(0.75, 0.50)
        landmarks[LEFT_TEMPLE] = point(0.30, 0.30)
        landmarks[RIGHT_TEMPLE] = point(0.70, 0.30)
        landmarks[LEFT_CHEEK] = point(0.32, 0.52)
        landmarks[RIGHT_CHEEK] = point(0.68, 0.52)

        landmarks[LEFT_EYE_OUTER] = point(0.35, 0.38)
        landmarks[LEFT_EYE_INNER] = point(0.45, 0.38)
        landmarks[LEFT_EYE_TOP] = point(0.40, 0.36)
        landmarks[LEFT_EYE_BOTTOM] = point(0.40, 0.40)

        landmarks[RIGHT_EYE_INNER] = point(0.55, 0.38)
        landmarks[RIGHT_EYE_OUTER] = point(0.65, 0.38)
        landmarks[RIGHT_EYE_TOP] = point(0.60, 0.36)
        landmarks[RIGHT_EYE_BOTTOM] = point(0.60, 0.40)

        landmarks[LEFT_BROW_A] = point(0.37, 0.32)
        landmarks[LEFT_BROW_B] = point(0.43, 0.32)
        landmarks[LEFT_BROW_MID] = point(0.40, 0.30)
        landmarks[RIGHT_BROW_A] = point(0.57, 0.32)
        landmarks[RIGHT_BROW_B] = point(0.63, 0.32)
        landmarks[RIGHT_BROW_MID] = point(0.60, 0.30)

        landmarks[NOSE_TIP] = point(0.50, 0.50)
        landmarks[NOSE_BRIDGE_TOP] = point(0.50, 0.38)
        landmarks[NOSE_LEFT] = point(0.46, 0.48)
        landmarks[NOSE_RIGHT] = point(0.54, 0.48)

        landmarks[MOUTH_LEFT] = point(0.42, 0.64)
        landmarks[MOUTH_RIGHT] = point(0.58, 0.64)
        landmarks[MOUTH_TOP] = point(0.50, 0.62)
        landmarks[MOUTH_BOTTOM] = point(0.50, 0.66)

        return landmarks
    }

    private fun frontFaceLandmarksWithIris(): List<NormalizedLandmark> {
        val landmarks = frontFaceLandmarks().toMutableList()
        while (landmarks.size < IRIS_LANDMARK_COUNT) {
            landmarks += point(0.50, 0.50)
        }

        RIGHT_IRIS_INDICES.forEachIndexed { index, landmarkIndex ->
            val dx = if (index % 2 == 0) -0.002 else 0.002
            val dy = if (index < 2) -0.002 else 0.002
            landmarks[landmarkIndex] = point(0.595 + dx, 0.378 + dy)
        }
        LEFT_IRIS_INDICES.forEachIndexed { index, landmarkIndex ->
            val dx = if (index % 2 == 0) -0.002 else 0.002
            val dy = if (index < 2) -0.002 else 0.002
            landmarks[landmarkIndex] = point(0.405 + dx, 0.382 + dy)
        }
        return landmarks
    }
    private fun point(x: Double, y: Double): NormalizedLandmark {
        return NormalizedLandmark.create(x.toFloat(), y.toFloat(), 0.0f)
    }

    private fun yawRotationMatrix(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        return floatArrayOf(
            cos, 0f, sin, 0f,
            0f, 1f, 0f, 0f,
            -sin, 0f, cos, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private companion object {
        private const val REQUIRED_LANDMARKS = 468
        private const val IRIS_LANDMARK_COUNT = 478
        private val LEFT_IRIS_INDICES = intArrayOf(474, 475, 476, 477)
        private val RIGHT_IRIS_INDICES = intArrayOf(469, 470, 471, 472)

        private const val FOREHEAD = 10
        private const val CHIN = 152
        private const val JAW_LEFT = 234
        private const val JAW_RIGHT = 454
        private const val LEFT_TEMPLE = 127
        private const val RIGHT_TEMPLE = 356
        private const val LEFT_CHEEK = 93
        private const val RIGHT_CHEEK = 323

        private const val LEFT_EYE_OUTER = 33
        private const val LEFT_EYE_INNER = 133
        private const val LEFT_EYE_TOP = 159
        private const val LEFT_EYE_BOTTOM = 145

        private const val RIGHT_EYE_INNER = 362
        private const val RIGHT_EYE_OUTER = 263
        private const val RIGHT_EYE_TOP = 386
        private const val RIGHT_EYE_BOTTOM = 374

        private const val LEFT_BROW_A = 70
        private const val LEFT_BROW_B = 63
        private const val LEFT_BROW_MID = 105
        private const val RIGHT_BROW_A = 300
        private const val RIGHT_BROW_B = 293
        private const val RIGHT_BROW_MID = 334

        private const val NOSE_TIP = 1
        private const val NOSE_BRIDGE_TOP = 168
        private const val NOSE_LEFT = 129
        private const val NOSE_RIGHT = 358

        private const val MOUTH_LEFT = 61
        private const val MOUTH_RIGHT = 291
        private const val MOUTH_TOP = 13
        private const val MOUTH_BOTTOM = 14
    }
}




