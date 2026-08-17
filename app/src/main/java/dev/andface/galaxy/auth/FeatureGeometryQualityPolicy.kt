package dev.andface.galaxy.auth

import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint

object FeatureGeometryQualityPolicy {
    fun validateForAccess(rawFrame: RawFeatureFrame, hint: OcclusionHint): FailureReason {
        invalidVectorReason(rawFrame)?.let { return it }

        if (!isWithinAll(rawFrame, ACCESS_ALWAYS_VISIBLE_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        if (!hint.lowerFaceCovered && !isWithinAll(rawFrame, ACCESS_LOWER_FACE_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        if (!hint.leftEyePatch && !isWithinAll(rawFrame, ACCESS_LEFT_EYE_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        if (!hint.rightEyePatch && !isWithinAll(rawFrame, ACCESS_RIGHT_EYE_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        if (!hint.leftEyePatch && !hint.rightEyePatch && !isWithinAll(rawFrame, ACCESS_BOTH_EYE_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        if (rawFrame.quality.irisLandmarksAvailable && !isWithinAll(rawFrame, ACCESS_IRIS_RANGES)) {
            return FailureReason.POOR_FACE_QUALITY
        }
        return FailureReason.NONE
    }

    fun validateForCleanEnrollment(rawFrame: RawFeatureFrame): FailureReason {
        invalidVectorReason(rawFrame)?.let { return it }
        if (!isWithinAll(rawFrame, ACCESS_ALWAYS_VISIBLE_RANGES) ||
            !isWithinAll(rawFrame, ACCESS_LEFT_EYE_RANGES) ||
            !isWithinAll(rawFrame, ACCESS_RIGHT_EYE_RANGES) ||
            !isWithinAll(rawFrame, ACCESS_BOTH_EYE_RANGES) ||
            (rawFrame.quality.irisLandmarksAvailable && !isWithinAll(rawFrame, ACCESS_IRIS_RANGES))
        ) {
            return FailureReason.POOR_FACE_QUALITY
        }
        return if (isPlausibleCleanEnrollment(rawFrame)) {
            FailureReason.NONE
        } else {
            FailureReason.OCCLUDED_DURING_ENROLLMENT
        }
    }
    fun cleanEnrollmentDiagnostic(rawFrame: RawFeatureFrame): String {
        invalidVectorReason(rawFrame)?.let { return "vector:$it" }
        firstOutOfRange(rawFrame, ACCESS_ALWAYS_VISIBLE_RANGES)?.let { return "access:${it.describe(rawFrame)}" }
        firstOutOfRange(rawFrame, ACCESS_LEFT_EYE_RANGES)?.let { return "leftEye:${it.describe(rawFrame)}" }
        firstOutOfRange(rawFrame, ACCESS_RIGHT_EYE_RANGES)?.let { return "rightEye:${it.describe(rawFrame)}" }
        firstOutOfRange(rawFrame, ACCESS_BOTH_EYE_RANGES)?.let { return "bothEye:${it.describe(rawFrame)}" }
        if (rawFrame.quality.irisLandmarksAvailable) {
            firstOutOfRange(rawFrame, ACCESS_IRIS_RANGES)?.let { return "iris:${it.describe(rawFrame)}" }
        }
        val cleanRanges = if (rawFrame.quality.irisLandmarksAvailable) {
            CLEAN_ENROLLMENT_RANGES
        } else {
            CLEAN_ENROLLMENT_RANGES.filterNot { it.type.isIrisFeature }
        }
        firstOutOfRange(rawFrame, cleanRanges)?.let { return "clean:${it.describe(rawFrame)}" }
        return "ok"
    }


    fun isPlausibleCleanEnrollment(rawFrame: RawFeatureFrame): Boolean {
        val ranges = if (rawFrame.quality.irisLandmarksAvailable) {
            CLEAN_ENROLLMENT_RANGES
        } else {
            CLEAN_ENROLLMENT_RANGES.filterNot { it.type.isIrisFeature }
        }
        return isWithinAll(rawFrame, ranges)
    }

    private fun invalidVectorReason(rawFrame: RawFeatureFrame): FailureReason? {
        if (rawFrame.values.size < FeatureType.COUNT) return FailureReason.TOO_FEW_FEATURES
        if (rawFrame.values.size != FeatureType.COUNT) return FailureReason.POOR_FACE_QUALITY
        return if (rawFrame.values.any { !it.isFinite() }) FailureReason.POOR_FACE_QUALITY else null
    }

    private fun isWithinAll(rawFrame: RawFeatureFrame, checks: List<RangeCheck>): Boolean {
        return checks.all { check -> rawFrame.value(check.type) in check.range }
    }
    private fun firstOutOfRange(rawFrame: RawFeatureFrame, checks: List<RangeCheck>): RangeCheck? {
        return checks.firstOrNull { check -> rawFrame.value(check.type) !in check.range }
    }
    private data class RangeCheck(
        val type: FeatureType,
        val range: ClosedFloatingPointRange<Double>
    ) {
        fun describe(rawFrame: RawFeatureFrame): String {
            return "${type.name}=${rawFrame.value(type).formatDiagnostic()} in ${range.start.formatDiagnostic()}..${range.endInclusive.formatDiagnostic()}"
        }
    }

    private fun Double.formatDiagnostic(): String = String.format(java.util.Locale.US, "%.4f", this)

    private val ACCESS_ALWAYS_VISIBLE_RANGES = listOf(
        RangeCheck(FeatureType.EyeDistance, 0.22..0.85),
        RangeCheck(FeatureType.BrowDistance, 0.00..0.24),
        RangeCheck(FeatureType.NoseWidth, 0.05..0.48),
        RangeCheck(FeatureType.InnerEyeDistance, 0.04..0.58),
        RangeCheck(FeatureType.BrowWidth, 0.02..0.38),
        RangeCheck(FeatureType.BrowAsymmetry, 0.00..0.48),
        RangeCheck(FeatureType.LeftBrowWidth, 0.02..0.38),
        RangeCheck(FeatureType.RightBrowWidth, 0.02..0.38),
        RangeCheck(FeatureType.NoseBridgeLength, 0.02..0.55),
        RangeCheck(FeatureType.NoseRootToBrowLine, 0.00..0.50),
        RangeCheck(FeatureType.EyeNoseLeft, 0.06..0.72),
        RangeCheck(FeatureType.EyeNoseRight, 0.06..0.72),
        RangeCheck(FeatureType.EyeNoseSymmetry, 0.00..0.60),
        RangeCheck(FeatureType.UpperFaceWidth, 0.34..1.38),
        RangeCheck(FeatureType.UpperFaceAspect, 0.08..1.30),
        RangeCheck(FeatureType.CheekboneWidth, 0.30..1.38),
        RangeCheck(FeatureType.LeftCheekNose, 0.10..0.94),
        RangeCheck(FeatureType.RightCheekNose, 0.10..0.94),
        RangeCheck(FeatureType.CheekNoseSymmetry, 0.00..0.74),
        RangeCheck(FeatureType.LeftBrowEyeGap, 0.02..0.46),
        RangeCheck(FeatureType.RightBrowEyeGap, 0.02..0.46),
        RangeCheck(FeatureType.BrowEyeGapAsymmetry, 0.00..0.48),
        RangeCheck(FeatureType.LeftUnderEyeCheek, 0.08..0.58),
        RangeCheck(FeatureType.RightUnderEyeCheek, 0.08..0.58),
        RangeCheck(FeatureType.UnderEyeCheekAsymmetry, 0.00..0.54),
        RangeCheck(FeatureType.InterBrowDistance, 0.03..0.78),
        RangeCheck(FeatureType.LeftBrowNoseRoot, 0.02..0.56),
        RangeCheck(FeatureType.RightBrowNoseRoot, 0.02..0.56),
        RangeCheck(FeatureType.BrowNoseRootAsymmetry, 0.00..0.56),
        RangeCheck(FeatureType.LeftInnerEyeNoseRoot, 0.01..0.44),
        RangeCheck(FeatureType.RightInnerEyeNoseRoot, 0.01..0.44),
        RangeCheck(FeatureType.InnerEyeNoseRootAsymmetry, 0.00..0.44),
        RangeCheck(FeatureType.LeftTempleEye, 0.02..0.70),
        RangeCheck(FeatureType.RightTempleEye, 0.02..0.70),
        RangeCheck(FeatureType.TempleEyeAsymmetry, 0.00..0.70),
        RangeCheck(FeatureType.LeftBrowSlope, 0.00..1.00),
        RangeCheck(FeatureType.RightBrowSlope, 0.00..1.00),
        RangeCheck(FeatureType.BrowSlopeAsymmetry, 0.00..0.90),
        RangeCheck(FeatureType.LeftNoseWing, 0.02..0.42),
        RangeCheck(FeatureType.RightNoseWing, 0.02..0.42),
        RangeCheck(FeatureType.NoseWingAsymmetry, 0.00..0.42),
        RangeCheck(FeatureType.NoseBaseTriangle, 0.00..0.14),
        RangeCheck(FeatureType.LeftNoseCheekArea, 0.00..0.18),
        RangeCheck(FeatureType.RightNoseCheekArea, 0.00..0.18),
        RangeCheck(FeatureType.NoseCheekAreaAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.LeftTempleCheekSlope, 0.00..1.05),
        RangeCheck(FeatureType.RightTempleCheekSlope, 0.00..1.05),
        RangeCheck(FeatureType.TempleCheekSlopeAsymmetry, 0.00..0.90),
        RangeCheck(FeatureType.MidfaceWidthRatio, 0.30..1.45),
        RangeCheck(FeatureType.LeftBrowArchHeight, 0.00..0.24),
        RangeCheck(FeatureType.RightBrowArchHeight, 0.00..0.24),
        RangeCheck(FeatureType.BrowArchAsymmetry, 0.00..0.24),
        RangeCheck(FeatureType.BrowLineTilt, 0.00..0.95),
        RangeCheck(FeatureType.InnerEyeNoseTipTriangle, 0.00..0.18),
        RangeCheck(FeatureType.BrowNoseRootTriangle, 0.00..0.14),
        RangeCheck(FeatureType.NoseRootLateralOffset, 0.00..0.38),
        RangeCheck(FeatureType.NoseRootForeheadDistance, 0.04..0.90),
        RangeCheck(FeatureType.UpperFaceDiagonalRatio, 0.20..1.55),
        RangeCheck(FeatureType.UpperFacePerimeterRatio, 0.25..1.80),
        RangeCheck(FeatureType.LeftTempleBrowDistance, 0.02..0.82),
        RangeCheck(FeatureType.RightTempleBrowDistance, 0.02..0.82),
        RangeCheck(FeatureType.TempleBrowDistanceAsymmetry, 0.00..0.72),
        RangeCheck(FeatureType.ForeheadBrowTriangle, 0.00..0.22),
        RangeCheck(FeatureType.ForeheadEyeTriangle, 0.00..0.28),
        RangeCheck(FeatureType.LeftForeheadTempleArea, 0.00..0.24),
        RangeCheck(FeatureType.RightForeheadTempleArea, 0.00..0.24),
        RangeCheck(FeatureType.ForeheadTempleAreaAsymmetry, 0.00..0.18),
        RangeCheck(FeatureType.UpperFaceTaper, 0.12..1.25),
        RangeCheck(FeatureType.LeftBrowTempleSlope, 0.00..1.05),
        RangeCheck(FeatureType.RightBrowTempleSlope, 0.00..1.05),
        RangeCheck(FeatureType.BrowTempleSlopeAsymmetry, 0.00..0.90),
        RangeCheck(FeatureType.Yaw, -1.10..1.10),
        RangeCheck(FeatureType.Pitch, -1.10..1.10),
        RangeCheck(FeatureType.Roll, -1.00..1.00)
    )

    private val ACCESS_LOWER_FACE_RANGES = listOf(
        RangeCheck(FeatureType.NoseToChin, 0.20..0.88),
        RangeCheck(FeatureType.MouthWidth, 0.14..0.82),
        RangeCheck(FeatureType.JawWidth, 0.95..5.20),
        RangeCheck(FeatureType.NoseToMouth, 0.04..0.42),
        RangeCheck(FeatureType.MouthHeight, 0.00..0.30),
        RangeCheck(FeatureType.MouthAspect, 0.00..1.40),
        RangeCheck(FeatureType.ChinMouthDistance, 0.04..0.72),
        RangeCheck(FeatureType.ChinJawOffset, 0.16..1.20),
        RangeCheck(FeatureType.FaceAspect, 0.68..2.02),
        RangeCheck(FeatureType.LeftJawCheekDistance, 0.02..0.65),
        RangeCheck(FeatureType.RightJawCheekDistance, 0.02..0.65),
        RangeCheck(FeatureType.JawCheekAsymmetry, 0.00..0.45),
        RangeCheck(FeatureType.ChinLateralOffset, 0.00..0.42)
    )

    private val ACCESS_LEFT_EYE_RANGES = listOf(
        RangeCheck(FeatureType.LeftEyeOpen, 0.00..0.72),
        RangeCheck(FeatureType.LeftEyeWidth, 0.04..0.48),
        RangeCheck(FeatureType.LeftEyeHeight, 0.00..0.24),
        RangeCheck(FeatureType.LeftUpperEyelidArch, 0.00..0.22),
        RangeCheck(FeatureType.LeftLowerEyelidArch, 0.00..0.22),
        RangeCheck(FeatureType.LeftBrowEyeDistance, 0.00..0.55),
        RangeCheck(FeatureType.LeftPeriocularArea, 0.00..0.14),
        RangeCheck(FeatureType.LeftMidFaceTriangle, 0.00..0.26),
        RangeCheck(FeatureType.LeftUpperMidfaceArea, 0.00..0.18),
        RangeCheck(FeatureType.LeftBrowOuterEyeGap, 0.00..0.50),
        RangeCheck(FeatureType.LeftBrowInnerEyeGap, 0.00..0.50),
        RangeCheck(FeatureType.LeftEyeCornerTilt, 0.00..1.00),
        RangeCheck(FeatureType.LeftOuterEyeNoseRoot, 0.02..0.78),
        RangeCheck(FeatureType.LeftEyeForeheadDistance, 0.08..1.05),
        RangeCheck(FeatureType.LeftOrbitalTriangle, 0.00..0.16),
        RangeCheck(FeatureType.LeftInnerBrowNoseArea, 0.00..0.14),
        RangeCheck(FeatureType.LeftEyeBrowNoseTriangle, 0.00..0.16),
        RangeCheck(FeatureType.LeftInnerEyeNoseBridgeTriangle, 0.00..0.16),
        RangeCheck(FeatureType.LeftEyeNoseDepth, 0.00..1.20)
    )

    private val ACCESS_RIGHT_EYE_RANGES = listOf(
        RangeCheck(FeatureType.RightEyeOpen, 0.00..0.72),
        RangeCheck(FeatureType.RightEyeWidth, 0.04..0.48),
        RangeCheck(FeatureType.RightEyeHeight, 0.00..0.24),
        RangeCheck(FeatureType.RightUpperEyelidArch, 0.00..0.22),
        RangeCheck(FeatureType.RightLowerEyelidArch, 0.00..0.22),
        RangeCheck(FeatureType.RightBrowEyeDistance, 0.00..0.55),
        RangeCheck(FeatureType.RightPeriocularArea, 0.00..0.14),
        RangeCheck(FeatureType.RightMidFaceTriangle, 0.00..0.26),
        RangeCheck(FeatureType.RightUpperMidfaceArea, 0.00..0.18),
        RangeCheck(FeatureType.RightBrowOuterEyeGap, 0.00..0.50),
        RangeCheck(FeatureType.RightBrowInnerEyeGap, 0.00..0.50),
        RangeCheck(FeatureType.RightEyeCornerTilt, 0.00..1.00),
        RangeCheck(FeatureType.RightOuterEyeNoseRoot, 0.02..0.78),
        RangeCheck(FeatureType.RightEyeForeheadDistance, 0.08..1.05),
        RangeCheck(FeatureType.RightOrbitalTriangle, 0.00..0.16),
        RangeCheck(FeatureType.RightInnerBrowNoseArea, 0.00..0.14),
        RangeCheck(FeatureType.RightEyeBrowNoseTriangle, 0.00..0.16),
        RangeCheck(FeatureType.RightInnerEyeNoseBridgeTriangle, 0.00..0.16),
        RangeCheck(FeatureType.RightEyeNoseDepth, 0.00..1.20)
    )

    private val ACCESS_BOTH_EYE_RANGES = listOf(
        RangeCheck(FeatureType.EyeOpenAsymmetry, 0.00..1.90),
        RangeCheck(FeatureType.EyeWidthAsymmetry, 0.00..1.45),
        RangeCheck(FeatureType.EyeHeightAsymmetry, 0.00..1.60),
        RangeCheck(FeatureType.UpperEyelidArchAsymmetry, 0.00..0.22),
        RangeCheck(FeatureType.LowerEyelidArchAsymmetry, 0.00..0.22),
        RangeCheck(FeatureType.BrowEyeDistanceAsymmetry, 0.00..0.60),
        RangeCheck(FeatureType.NoseRootEyeLineDistance, 0.00..0.36),
        RangeCheck(FeatureType.NoseTipEyeLineDistance, 0.04..0.62),
        RangeCheck(FeatureType.NoseLateralOffset, 0.00..0.42),
        RangeCheck(FeatureType.PeriocularAreaAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.MidFaceTriangleAsymmetry, 0.00..0.22),
        RangeCheck(FeatureType.UpperMidfaceAreaAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.BrowOuterGapAsymmetry, 0.00..0.50),
        RangeCheck(FeatureType.BrowInnerGapAsymmetry, 0.00..0.50),
        RangeCheck(FeatureType.EyeCornerTiltAsymmetry, 0.00..1.00),
        RangeCheck(FeatureType.ForeheadToEyeLine, 0.08..0.78),
        RangeCheck(FeatureType.EyeLineBrowLineGap, 0.00..0.60),
        RangeCheck(FeatureType.OuterEyeNoseRootAsymmetry, 0.00..0.60),
        RangeCheck(FeatureType.EyeForeheadAsymmetry, 0.00..0.55),
        RangeCheck(FeatureType.OrbitalTriangleAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.InnerBrowNoseAreaAsymmetry, 0.00..0.12),
        RangeCheck(FeatureType.BrowSpanEyeSpanRatio, 0.25..1.60),
        RangeCheck(FeatureType.NoseRootToEyeSpanRatio, 0.00..0.75),
        RangeCheck(FeatureType.EyeBrowNoseTriangleAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.NoseBridgeEyeTriangle, 0.00..0.16),
        RangeCheck(FeatureType.NoseBridgeToEyeSpanRatio, 0.04..1.90),
        RangeCheck(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.NoseTipInnerEyeLineDistance, 0.04..0.62),
        RangeCheck(FeatureType.NoseBridgeInnerEyeSpanRatio, 0.04..1.90),
        RangeCheck(FeatureType.NoseTipDepthToEyeSpanRatio, 0.00..1.20),
        RangeCheck(FeatureType.NoseBridgeBrowLineOffset, 0.00..0.70),
        RangeCheck(FeatureType.NoseBridgeEyeLineOffset, 0.00..0.42),
        RangeCheck(FeatureType.EyeNoseDepthAsymmetry, 0.00..1.20)
    )

    private val ACCESS_IRIS_RANGES = listOf(
        RangeCheck(FeatureType.LeftIrisEyeOffset, 0.00..0.92),
        RangeCheck(FeatureType.RightIrisEyeOffset, 0.00..0.92),
        RangeCheck(FeatureType.IrisOffsetAsymmetry, 0.00..0.92),
        RangeCheck(FeatureType.IrisDistance, 0.10..0.86),
        RangeCheck(FeatureType.LeftIrisNoseRoot, 0.02..0.72),
        RangeCheck(FeatureType.RightIrisNoseRoot, 0.02..0.72),
        RangeCheck(FeatureType.IrisNoseRootAsymmetry, 0.00..0.56),
        RangeCheck(FeatureType.IrisSpanRatio, 0.35..1.55)
    )

    private val CLEAN_ENROLLMENT_RANGES = listOf(
        RangeCheck(FeatureType.EyeDistance, 0.25..0.78),
        RangeCheck(FeatureType.BrowDistance, 0.01..0.18),
        RangeCheck(FeatureType.NoseWidth, 0.08..0.40),
        RangeCheck(FeatureType.NoseToChin, 0.28..0.78),
        RangeCheck(FeatureType.MouthWidth, 0.18..0.72),
        RangeCheck(FeatureType.JawWidth, 1.20..4.50),
        RangeCheck(FeatureType.LeftEyeOpen, 0.02..0.60),
        RangeCheck(FeatureType.RightEyeOpen, 0.02..0.60),
        RangeCheck(FeatureType.NoseToMouth, 0.06..0.34),
        RangeCheck(FeatureType.FaceAspect, 0.75..1.85),
        RangeCheck(FeatureType.InnerEyeDistance, 0.08..0.48),
        RangeCheck(FeatureType.LeftEyeWidth, 0.06..0.40),
        RangeCheck(FeatureType.RightEyeWidth, 0.06..0.40),
        RangeCheck(FeatureType.LeftEyeHeight, 0.02..0.20),
        RangeCheck(FeatureType.RightEyeHeight, 0.02..0.20),
        RangeCheck(FeatureType.LeftUpperEyelidArch, 0.00..0.18),
        RangeCheck(FeatureType.RightUpperEyelidArch, 0.00..0.18),
        RangeCheck(FeatureType.UpperEyelidArchAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.LeftLowerEyelidArch, 0.00..0.18),
        RangeCheck(FeatureType.RightLowerEyelidArch, 0.00..0.18),
        RangeCheck(FeatureType.LowerEyelidArchAsymmetry, 0.00..0.14),
        RangeCheck(FeatureType.LeftBrowEyeDistance, 0.02..0.44),
        RangeCheck(FeatureType.RightBrowEyeDistance, 0.02..0.44),
        RangeCheck(FeatureType.BrowEyeDistanceAsymmetry, 0.0..0.36),
        RangeCheck(FeatureType.NoseRootEyeLineDistance, 0.0..0.24),
        RangeCheck(FeatureType.NoseTipEyeLineDistance, 0.06..0.52),
        RangeCheck(FeatureType.NoseLateralOffset, 0.0..0.28),
        RangeCheck(FeatureType.LeftPeriocularArea, 0.001..0.10),
        RangeCheck(FeatureType.RightPeriocularArea, 0.001..0.10),
        RangeCheck(FeatureType.PeriocularAreaAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.LeftMidFaceTriangle, 0.004..0.22),
        RangeCheck(FeatureType.RightMidFaceTriangle, 0.004..0.22),
        RangeCheck(FeatureType.MidFaceTriangleAsymmetry, 0.0..0.16),
        RangeCheck(FeatureType.LeftBrowArchHeight, 0.0..0.16),
        RangeCheck(FeatureType.RightBrowArchHeight, 0.0..0.16),
        RangeCheck(FeatureType.BrowArchAsymmetry, 0.0..0.16),
        RangeCheck(FeatureType.BrowLineTilt, 0.0..0.65),
        RangeCheck(FeatureType.LeftEyeCornerTilt, 0.0..0.80),
        RangeCheck(FeatureType.RightEyeCornerTilt, 0.0..0.80),
        RangeCheck(FeatureType.EyeCornerTiltAsymmetry, 0.0..0.80),
        RangeCheck(FeatureType.InnerEyeNoseTipTriangle, 0.001..0.14),
        RangeCheck(FeatureType.BrowNoseRootTriangle, 0.0..0.10),
        RangeCheck(FeatureType.NoseRootLateralOffset, 0.0..0.24),
        RangeCheck(FeatureType.LeftTempleBrowDistance, 0.03..0.68),
        RangeCheck(FeatureType.RightTempleBrowDistance, 0.03..0.68),
        RangeCheck(FeatureType.TempleBrowDistanceAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.ForeheadBrowTriangle, 0.0..0.16),
        RangeCheck(FeatureType.ForeheadEyeTriangle, 0.0..0.20),
        RangeCheck(FeatureType.LeftForeheadTempleArea, 0.0..0.18),
        RangeCheck(FeatureType.RightForeheadTempleArea, 0.0..0.18),
        RangeCheck(FeatureType.ForeheadTempleAreaAsymmetry, 0.0..0.12),
        RangeCheck(FeatureType.UpperFaceTaper, 0.18..1.05),
        RangeCheck(FeatureType.LeftBrowTempleSlope, 0.0..1.05),
        RangeCheck(FeatureType.RightBrowTempleSlope, 0.0..1.05),
        RangeCheck(FeatureType.BrowTempleSlopeAsymmetry, 0.0..0.70),
        RangeCheck(FeatureType.LeftUpperMidfaceArea, 0.002..0.14),
        RangeCheck(FeatureType.RightUpperMidfaceArea, 0.002..0.14),
        RangeCheck(FeatureType.UpperMidfaceAreaAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.LeftNoseCheekArea, 0.002..0.14),
        RangeCheck(FeatureType.RightNoseCheekArea, 0.002..0.14),
        RangeCheck(FeatureType.NoseCheekAreaAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.LeftTempleCheekSlope, 0.0..1.05),
        RangeCheck(FeatureType.RightTempleCheekSlope, 0.0..1.05),
        RangeCheck(FeatureType.TempleCheekSlopeAsymmetry, 0.0..0.70),
        RangeCheck(FeatureType.MidfaceWidthRatio, 0.35..1.35),
        RangeCheck(FeatureType.LeftNoseWing, 0.02..0.34),
        RangeCheck(FeatureType.RightNoseWing, 0.02..0.34),
        RangeCheck(FeatureType.NoseWingAsymmetry, 0.0..0.22),
        RangeCheck(FeatureType.NoseBaseTriangle, 0.0..0.08),
        RangeCheck(FeatureType.MouthHeight, 0.00..0.22),
        RangeCheck(FeatureType.MouthAspect, 0.00..1.00),
        RangeCheck(FeatureType.ChinMouthDistance, 0.08..0.62),
        RangeCheck(FeatureType.ChinJawOffset, 0.22..0.95),
        RangeCheck(FeatureType.EyeOpenAsymmetry, 0.0..1.60),
        RangeCheck(FeatureType.EyeHeightAsymmetry, 0.0..1.20),
        RangeCheck(FeatureType.BrowWidth, 0.03..0.32),
        RangeCheck(FeatureType.BrowAsymmetry, 0.0..0.22),
        RangeCheck(FeatureType.LeftBrowWidth, 0.03..0.34),
        RangeCheck(FeatureType.RightBrowWidth, 0.03..0.34),
        RangeCheck(FeatureType.NoseBridgeLength, 0.04..0.45),
        RangeCheck(FeatureType.NoseRootToBrowLine, 0.02..0.48),
        RangeCheck(FeatureType.NoseBridgeBrowLineOffset, 0.00..0.56),
        RangeCheck(FeatureType.NoseBridgeEyeLineOffset, 0.00..0.30),
        RangeCheck(FeatureType.EyeNoseLeft, 0.10..0.62),
        RangeCheck(FeatureType.EyeNoseRight, 0.10..0.62),
        RangeCheck(FeatureType.EyeNoseSymmetry, 0.0..0.38),
        RangeCheck(FeatureType.UpperFaceWidth, 0.45..1.25),
        RangeCheck(FeatureType.UpperFaceAspect, 0.12..1.10),
        RangeCheck(FeatureType.CheekboneWidth, 0.45..1.25),
        RangeCheck(FeatureType.LeftCheekNose, 0.18..0.80),
        RangeCheck(FeatureType.RightCheekNose, 0.18..0.80),
        RangeCheck(FeatureType.CheekNoseSymmetry, 0.0..0.45),
        RangeCheck(FeatureType.LeftBrowOuterEyeGap, 0.02..0.42),
        RangeCheck(FeatureType.RightBrowOuterEyeGap, 0.02..0.42),
        RangeCheck(FeatureType.BrowOuterGapAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.LeftBrowInnerEyeGap, 0.02..0.42),
        RangeCheck(FeatureType.RightBrowInnerEyeGap, 0.02..0.42),
        RangeCheck(FeatureType.BrowInnerGapAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.ForeheadToEyeLine, 0.12..0.70),
        RangeCheck(FeatureType.InterBrowDistance, 0.05..0.70),
        RangeCheck(FeatureType.LeftBrowNoseRoot, 0.03..0.48),
        RangeCheck(FeatureType.RightBrowNoseRoot, 0.03..0.48),
        RangeCheck(FeatureType.BrowNoseRootAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.LeftInnerEyeNoseRoot, 0.02..0.36),
        RangeCheck(FeatureType.RightInnerEyeNoseRoot, 0.02..0.36),
        RangeCheck(FeatureType.InnerEyeNoseRootAsymmetry, 0.0..0.30),
        RangeCheck(FeatureType.LeftTempleEye, 0.03..0.58),
        RangeCheck(FeatureType.RightTempleEye, 0.03..0.58),
        RangeCheck(FeatureType.TempleEyeAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.EyeWidthAsymmetry, 0.0..0.75),
        RangeCheck(FeatureType.LeftBrowSlope, 0.0..0.85),
        RangeCheck(FeatureType.RightBrowSlope, 0.0..0.85),
        RangeCheck(FeatureType.BrowSlopeAsymmetry, 0.0..0.70),
        RangeCheck(FeatureType.LeftIrisEyeOffset, 0.0..0.80),
        RangeCheck(FeatureType.RightIrisEyeOffset, 0.0..0.80),
        RangeCheck(FeatureType.IrisOffsetAsymmetry, 0.0..0.80),
        RangeCheck(FeatureType.IrisDistance, 0.15..0.78),
        RangeCheck(FeatureType.LeftIrisNoseRoot, 0.03..0.62),
        RangeCheck(FeatureType.RightIrisNoseRoot, 0.03..0.62),
        RangeCheck(FeatureType.IrisNoseRootAsymmetry, 0.0..0.45),
        RangeCheck(FeatureType.IrisSpanRatio, 0.45..1.35),
        RangeCheck(FeatureType.EyeLineBrowLineGap, 0.02..0.42),
        RangeCheck(FeatureType.LeftOuterEyeNoseRoot, 0.03..0.62),
        RangeCheck(FeatureType.RightOuterEyeNoseRoot, 0.03..0.62),
        RangeCheck(FeatureType.OuterEyeNoseRootAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.LeftEyeForeheadDistance, 0.12..0.90),
        RangeCheck(FeatureType.RightEyeForeheadDistance, 0.12..0.90),
        RangeCheck(FeatureType.EyeForeheadAsymmetry, 0.0..0.34),
        RangeCheck(FeatureType.NoseRootForeheadDistance, 0.08..0.75),
        RangeCheck(FeatureType.UpperFaceDiagonalRatio, 0.25..1.25),
        RangeCheck(FeatureType.UpperFacePerimeterRatio, 0.30..1.50),
        RangeCheck(FeatureType.LeftJawCheekDistance, 0.03..0.45),
        RangeCheck(FeatureType.RightJawCheekDistance, 0.03..0.45),
        RangeCheck(FeatureType.JawCheekAsymmetry, 0.0..0.28),
        RangeCheck(FeatureType.ChinLateralOffset, 0.0..0.24),
        RangeCheck(FeatureType.LeftOrbitalTriangle, 0.0..0.12),
        RangeCheck(FeatureType.RightOrbitalTriangle, 0.0..0.12),
        RangeCheck(FeatureType.OrbitalTriangleAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.LeftInnerBrowNoseArea, 0.0..0.10),
        RangeCheck(FeatureType.RightInnerBrowNoseArea, 0.0..0.10),
        RangeCheck(FeatureType.InnerBrowNoseAreaAsymmetry, 0.0..0.08),
        RangeCheck(FeatureType.LeftEyeBrowNoseTriangle, 0.0..0.12),
        RangeCheck(FeatureType.RightEyeBrowNoseTriangle, 0.0..0.12),
        RangeCheck(FeatureType.EyeBrowNoseTriangleAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.BrowSpanEyeSpanRatio, 0.40..1.40),
        RangeCheck(FeatureType.NoseRootToEyeSpanRatio, 0.0..0.50),
        RangeCheck(FeatureType.NoseBridgeEyeTriangle, 0.0..0.12),
        RangeCheck(FeatureType.NoseBridgeToEyeSpanRatio, 0.05..1.80),
        RangeCheck(FeatureType.LeftInnerEyeNoseBridgeTriangle, 0.0..0.12),
        RangeCheck(FeatureType.RightInnerEyeNoseBridgeTriangle, 0.0..0.12),
        RangeCheck(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry, 0.0..0.10),
        RangeCheck(FeatureType.NoseTipInnerEyeLineDistance, 0.06..0.52),
        RangeCheck(FeatureType.NoseBridgeInnerEyeSpanRatio, 0.05..1.80),
        RangeCheck(FeatureType.NoseTipDepthToEyeSpanRatio, 0.0..0.80),
        RangeCheck(FeatureType.NoseTipDepth, 0.0..0.80),
        RangeCheck(FeatureType.LeftEyeNoseDepth, 0.0..0.80),
        RangeCheck(FeatureType.RightEyeNoseDepth, 0.0..0.80),
        RangeCheck(FeatureType.EyeNoseDepthAsymmetry, 0.0..0.70),
        RangeCheck(FeatureType.LeftCheekNoseDepth, 0.0..0.80),
        RangeCheck(FeatureType.RightCheekNoseDepth, 0.0..0.80),
        RangeCheck(FeatureType.CheekNoseDepthAsymmetry, 0.0..0.70)
    )
}



