package dev.andface.galaxy.auth

import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import kotlin.math.abs

class FeatureFrameStabilizer {
    private val frames = ArrayDeque<RawFeatureFrame>()

    fun add(frame: RawFeatureFrame): RawFeatureFrame {
        if (frames.isNotEmpty() && shouldStartNewWindow(frames.last(), frame)) {
            frames.clear()
        }

        frames.addLast(frame)
        while (frames.size > WINDOW_SIZE) {
            frames.removeFirst()
        }

        val values = DoubleArray(FeatureType.COUNT) { index ->
            stableValue(index)
        }
        return RawFeatureFrame(
            values = values,
            timestampMs = frame.timestampMs,
            quality = frame.quality
        )
    }

    fun reset() {
        frames.clear()
    }

    private fun stableValue(index: Int): Double {
        val sorted = frames
            .map { frame ->
                WeightedValue(
                    value = frame.values[index],
                    weight = frame.quality.accessScoringConfidence.coerceIn(MIN_FRAME_WEIGHT, 1.0)
                )
            }
            .sortedBy { it.value }
        if (sorted.size < MIN_TRIMMED_WINDOW) return sorted.weightedMedian()

        val trimmed = sorted.subList(TRIM_COUNT, sorted.size - TRIM_COUNT)
        val trimmedMean = trimmed.weightedAverage()
        val median = sorted.weightedMedian()
        return ROBUST_MEAN_WEIGHT * trimmedMean + (1.0 - ROBUST_MEAN_WEIGHT) * median
    }

    private fun shouldStartNewWindow(previous: RawFeatureFrame, current: RawFeatureFrame): Boolean {
        if (current.timestampMs - previous.timestampMs > MAX_FRAME_GAP_MS) return true
        if (previous.quality.irisLandmarksAvailable != current.quality.irisLandmarksAvailable) return true

        val changedIdentityFeatures = FACE_CHANGE_THRESHOLDS.count { (type, threshold) ->
            abs(current.value(type) - previous.value(type)) >= threshold
        }
        return changedIdentityFeatures >= MIN_CHANGED_IDENTITY_FEATURES_FOR_RESET
    }

    private fun List<WeightedValue>.weightedAverage(): Double {
        val totalWeight = sumOf { it.weight }
        if (totalWeight <= 0.0) return map { it.value }.average()
        return sumOf { it.value * it.weight } / totalWeight
    }

    private fun List<WeightedValue>.weightedMedian(): Double {
        val totalWeight = sumOf { it.weight }
        if (totalWeight <= 0.0) return map { it.value }.average()
        val midpoint = totalWeight * 0.5
        var cumulativeWeight = 0.0
        for (item in this) {
            cumulativeWeight += item.weight
            if (cumulativeWeight >= midpoint) return item.value
        }
        return last().value
    }

    private data class WeightedValue(
        val value: Double,
        val weight: Double
    )

    private companion object {
        private const val MIN_FRAME_WEIGHT = 0.25
        private const val WINDOW_SIZE = 7
        private const val MIN_TRIMMED_WINDOW = 5
        private const val TRIM_COUNT = 1
        private const val ROBUST_MEAN_WEIGHT = 0.65
        private const val MAX_FRAME_GAP_MS = 750L
        private const val MIN_CHANGED_IDENTITY_FEATURES_FOR_RESET = 3

        private val FACE_CHANGE_THRESHOLDS = listOf(
            FeatureType.EyeDistance to 0.055,
            FeatureType.BrowDistance to 0.045,
            FeatureType.NoseWidth to 0.050,
            FeatureType.NoseToChin to 0.075,
            FeatureType.MouthWidth to 0.080,
            FeatureType.JawWidth to 0.320,
            FeatureType.NoseToMouth to 0.055,
            FeatureType.FaceAspect to 0.120,
            FeatureType.InnerEyeDistance to 0.040,
            FeatureType.LeftEyeWidth to 0.045,
            FeatureType.RightEyeWidth to 0.045,
            FeatureType.LeftEyeHeight to 0.040,
            FeatureType.RightEyeHeight to 0.040,
            FeatureType.BrowWidth to 0.045,
            FeatureType.LeftBrowWidth to 0.045,
            FeatureType.RightBrowWidth to 0.045,
            FeatureType.NoseBridgeLength to 0.050,
            FeatureType.NoseRootToBrowLine to 0.045,
            FeatureType.EyeNoseLeft to 0.055,
            FeatureType.EyeNoseRight to 0.055,
            FeatureType.UpperFaceWidth to 0.055,
            FeatureType.CheekboneWidth to 0.055,
            FeatureType.UpperFaceAspect to 0.075,
            FeatureType.LeftCheekNose to 0.060,
            FeatureType.RightCheekNose to 0.060,
            FeatureType.LeftBrowOuterEyeGap to 0.045,
            FeatureType.RightBrowOuterEyeGap to 0.045,
            FeatureType.LeftBrowInnerEyeGap to 0.045,
            FeatureType.RightBrowInnerEyeGap to 0.045,
            FeatureType.ForeheadToEyeLine to 0.055,
            FeatureType.LeftBrowEyeDistance to 0.045,
            FeatureType.RightBrowEyeDistance to 0.045,
            FeatureType.BrowEyeDistanceAsymmetry to 0.050,
            FeatureType.NoseRootEyeLineDistance to 0.050,
            FeatureType.NoseTipEyeLineDistance to 0.055,
            FeatureType.NoseLateralOffset to 0.050,
            FeatureType.LeftPeriocularArea to 0.035,
            FeatureType.RightPeriocularArea to 0.035,
            FeatureType.PeriocularAreaAsymmetry to 0.035,
            FeatureType.LeftMidFaceTriangle to 0.045,
            FeatureType.RightMidFaceTriangle to 0.045,
            FeatureType.MidFaceTriangleAsymmetry to 0.045,
            FeatureType.LeftUpperMidfaceArea to 0.035,
            FeatureType.RightUpperMidfaceArea to 0.035,
            FeatureType.UpperMidfaceAreaAsymmetry to 0.035,
            FeatureType.LeftNoseCheekArea to 0.035,
            FeatureType.RightNoseCheekArea to 0.035,
            FeatureType.NoseCheekAreaAsymmetry to 0.035,
            FeatureType.LeftTempleCheekSlope to 0.160,
            FeatureType.RightTempleCheekSlope to 0.160,
            FeatureType.TempleCheekSlopeAsymmetry to 0.180,
            FeatureType.MidfaceWidthRatio to 0.140,
            FeatureType.LeftBrowArchHeight to 0.045,
            FeatureType.RightBrowArchHeight to 0.045,
            FeatureType.BrowArchAsymmetry to 0.050,
            FeatureType.BrowLineTilt to 0.160,
            FeatureType.LeftEyeCornerTilt to 0.180,
            FeatureType.RightEyeCornerTilt to 0.180,
            FeatureType.EyeCornerTiltAsymmetry to 0.200,
            FeatureType.InnerEyeNoseTipTriangle to 0.035,
            FeatureType.BrowNoseRootTriangle to 0.035,
            FeatureType.NoseRootLateralOffset to 0.045,
            FeatureType.LeftTempleBrowDistance to 0.055,
            FeatureType.RightTempleBrowDistance to 0.055,
            FeatureType.TempleBrowDistanceAsymmetry to 0.060,
            FeatureType.ForeheadBrowTriangle to 0.035,
            FeatureType.ForeheadEyeTriangle to 0.040,
            FeatureType.UpperFaceTaper to 0.090,
            FeatureType.LeftBrowTempleSlope to 0.160,
            FeatureType.RightBrowTempleSlope to 0.160,
            FeatureType.BrowTempleSlopeAsymmetry to 0.180,
            FeatureType.LeftNoseWing to 0.045,
            FeatureType.RightNoseWing to 0.045,
            FeatureType.NoseWingAsymmetry to 0.060,
            FeatureType.NoseBaseTriangle to 0.035,
            FeatureType.MouthHeight to 0.050,
            FeatureType.MouthAspect to 0.220,
            FeatureType.ChinMouthDistance to 0.060,
            FeatureType.ChinJawOffset to 0.080,
            FeatureType.LeftIrisEyeOffset to 0.075,
            FeatureType.RightIrisEyeOffset to 0.075,
            FeatureType.IrisOffsetAsymmetry to 0.080,
            FeatureType.IrisDistance to 0.070,
            FeatureType.LeftIrisNoseRoot to 0.070,
            FeatureType.RightIrisNoseRoot to 0.070,
            FeatureType.IrisNoseRootAsymmetry to 0.080,
            FeatureType.IrisSpanRatio to 0.140,
            FeatureType.EyeLineBrowLineGap to 0.050,
            FeatureType.LeftOuterEyeNoseRoot to 0.055,
            FeatureType.RightOuterEyeNoseRoot to 0.055,
            FeatureType.OuterEyeNoseRootAsymmetry to 0.060,
            FeatureType.LeftEyeForeheadDistance to 0.065,
            FeatureType.RightEyeForeheadDistance to 0.065,
            FeatureType.EyeForeheadAsymmetry to 0.070,
            FeatureType.NoseRootForeheadDistance to 0.065,
            FeatureType.UpperFaceDiagonalRatio to 0.110,
            FeatureType.UpperFacePerimeterRatio to 0.130,
            FeatureType.LeftJawCheekDistance to 0.060,
            FeatureType.RightJawCheekDistance to 0.060,
            FeatureType.JawCheekAsymmetry to 0.070,
            FeatureType.ChinLateralOffset to 0.060,
            FeatureType.LeftOrbitalTriangle to 0.035,
            FeatureType.RightOrbitalTriangle to 0.035,
            FeatureType.OrbitalTriangleAsymmetry to 0.040,
            FeatureType.LeftInnerBrowNoseArea to 0.030,
            FeatureType.RightInnerBrowNoseArea to 0.030,
            FeatureType.InnerBrowNoseAreaAsymmetry to 0.035,
            FeatureType.BrowSpanEyeSpanRatio to 0.140,
            FeatureType.NoseRootToEyeSpanRatio to 0.120,
            FeatureType.NoseTipDepth to 0.120,
            FeatureType.LeftEyeNoseDepth to 0.120,
            FeatureType.RightEyeNoseDepth to 0.120,
            FeatureType.EyeNoseDepthAsymmetry to 0.100,
            FeatureType.LeftCheekNoseDepth to 0.130,
            FeatureType.RightCheekNoseDepth to 0.130,
            FeatureType.CheekNoseDepthAsymmetry to 0.110,
            FeatureType.LeftForeheadTempleArea to 0.040,
            FeatureType.RightForeheadTempleArea to 0.040,
            FeatureType.ForeheadTempleAreaAsymmetry to 0.045,
            FeatureType.LeftEyeBrowNoseTriangle to 0.035,
            FeatureType.RightEyeBrowNoseTriangle to 0.035,
            FeatureType.EyeBrowNoseTriangleAsymmetry to 0.040,
            FeatureType.NoseBridgeEyeTriangle to 0.035,
            FeatureType.NoseBridgeToEyeSpanRatio to 0.140,
            FeatureType.LeftInnerEyeNoseBridgeTriangle to 0.035,
            FeatureType.RightInnerEyeNoseBridgeTriangle to 0.035,
            FeatureType.InnerEyeNoseBridgeTriangleAsymmetry to 0.040,
            FeatureType.NoseTipInnerEyeLineDistance to 0.055,
            FeatureType.NoseBridgeInnerEyeSpanRatio to 0.140,
            FeatureType.NoseTipDepthToEyeSpanRatio to 0.130,
            FeatureType.LeftUpperEyelidArch to 0.040,
            FeatureType.RightUpperEyelidArch to 0.040,
            FeatureType.UpperEyelidArchAsymmetry to 0.045,
            FeatureType.LeftLowerEyelidArch to 0.040,
            FeatureType.RightLowerEyelidArch to 0.040,
            FeatureType.LowerEyelidArchAsymmetry to 0.045,
            FeatureType.NoseBridgeBrowLineOffset to 0.055,
            FeatureType.NoseBridgeEyeLineOffset to 0.055
        )
    }
}
