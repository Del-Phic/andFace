package dev.andface.galaxy.auth

import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.FeatureGroup
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object CleanEnrollmentValidator {
    fun validateSample(rawFrame: RawFeatureFrame, hint: OcclusionHint): FailureReason {
        if (!hint.isClean) return FailureReason.OCCLUDED_DURING_ENROLLMENT
        if (rawFrame.values.any { it.isNaN() || it.isInfinite() }) {
            return FailureReason.TOO_FEW_FEATURES
        }
        if (!isFrontal(rawFrame)) return FailureReason.LOW_COVERAGE
        if (!hasPlausibleCleanFaceGeometry(rawFrame)) {
            return FailureReason.OCCLUDED_DURING_ENROLLMENT
        }
        return FailureReason.NONE
    }

    fun validateBaseline(samples: List<RawFeatureFrame>, expectedCount: Int): FailureReason {
        if (samples.size < expectedCount) return FailureReason.TOO_FEW_FEATURES
        if (!hasEnrollmentLiveness(samples)) return FailureReason.LOW_LIVENESS
        if (hasPersistentEyeOcclusion(samples)) return FailureReason.OCCLUDED_DURING_ENROLLMENT

        val unstablePose = listOf(FeatureType.Yaw, FeatureType.Pitch, FeatureType.Roll).any { type ->
            range(samples, type) > MAX_ENROLLMENT_POSE_RANGE
        }
        if (unstablePose) return FailureReason.UNSTABLE_ENROLLMENT

        val unstableLowerFace = listOf(
            FeatureType.NoseToChin,
            FeatureType.MouthWidth,
            FeatureType.NoseToMouth
        ).any { type ->
            standardDeviation(samples, type) > type.minimumSigma * MAX_LOWER_FACE_SIGMA_MULTIPLIER
        }
        if (unstableLowerFace) return FailureReason.UNSTABLE_ENROLLMENT

        val coreIdentityTypes = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.LOWER_FACE ||
                    type.group == FeatureGroup.EYE ||
                    type == FeatureType.FaceAspect
            }
            .let { types ->
                if (samples.any { !it.quality.irisLandmarksAvailable }) {
                    types.filterNot { it.isIrisFeature }
                } else {
                    types
                }
            }

        val unstableCoreIdentity = coreIdentityTypes.any { type ->
            standardDeviation(samples, type) > type.minimumSigma * MAX_CORE_SIGMA_MULTIPLIER
        }
        if (unstableCoreIdentity) return FailureReason.UNSTABLE_ENROLLMENT

        val identityStabilityTypes = enrollmentIdentityStabilityTypes(samples)
        if (!hasStableIdentityRegionSupport(samples, identityStabilityTypes)) {
            return FailureReason.UNSTABLE_ENROLLMENT
        }

        return FailureReason.NONE
    }

    private fun isFrontal(rawFrame: RawFeatureFrame): Boolean {
        return abs(rawFrame.value(FeatureType.Yaw)) <= MAX_ENROLLMENT_POSE &&
            abs(rawFrame.value(FeatureType.Pitch)) <= MAX_ENROLLMENT_POSE &&
            abs(rawFrame.value(FeatureType.Roll)) <= MAX_ENROLLMENT_POSE
    }

    private fun hasPlausibleCleanFaceGeometry(rawFrame: RawFeatureFrame): Boolean {
        val nonIrisPlausible = rawFrame.value(FeatureType.EyeDistance) in 0.25..0.78 &&
            rawFrame.value(FeatureType.BrowDistance) in 0.01..0.18 &&
            rawFrame.value(FeatureType.NoseWidth) in 0.08..0.40 &&
            rawFrame.value(FeatureType.NoseToChin) in 0.28..0.78 &&
            rawFrame.value(FeatureType.MouthWidth) in 0.18..0.72 &&
            rawFrame.value(FeatureType.JawWidth) in 1.20..4.50 &&
            rawFrame.value(FeatureType.LeftEyeOpen) in 0.02..0.60 &&
            rawFrame.value(FeatureType.RightEyeOpen) in 0.02..0.60 &&
            rawFrame.value(FeatureType.NoseToMouth) in 0.06..0.34 &&
            rawFrame.value(FeatureType.FaceAspect) in 0.75..1.85 &&
            rawFrame.value(FeatureType.InnerEyeDistance) in 0.08..0.48 &&
            rawFrame.value(FeatureType.LeftEyeWidth) in 0.06..0.40 &&
            rawFrame.value(FeatureType.RightEyeWidth) in 0.06..0.40 &&
            rawFrame.value(FeatureType.LeftEyeHeight) in 0.02..0.20 &&
            rawFrame.value(FeatureType.RightEyeHeight) in 0.02..0.20 &&
            rawFrame.value(FeatureType.LeftUpperEyelidArch) in 0.0..0.18 &&
            rawFrame.value(FeatureType.RightUpperEyelidArch) in 0.0..0.18 &&
            rawFrame.value(FeatureType.UpperEyelidArchAsymmetry) in 0.0..0.14 &&
            rawFrame.value(FeatureType.LeftLowerEyelidArch) in 0.0..0.18 &&
            rawFrame.value(FeatureType.RightLowerEyelidArch) in 0.0..0.18 &&
            rawFrame.value(FeatureType.LowerEyelidArchAsymmetry) in 0.0..0.14 &&
            rawFrame.value(FeatureType.EyeOpenAsymmetry) in 0.0..1.60 &&
            rawFrame.value(FeatureType.EyeHeightAsymmetry) in 0.0..1.20 &&
            rawFrame.value(FeatureType.BrowWidth) in 0.03..0.32 &&
            rawFrame.value(FeatureType.BrowAsymmetry) in 0.0..0.22 &&
            rawFrame.value(FeatureType.LeftBrowWidth) in 0.03..0.34 &&
            rawFrame.value(FeatureType.RightBrowWidth) in 0.03..0.34 &&
            rawFrame.value(FeatureType.NoseBridgeLength) in 0.04..0.45 &&
            rawFrame.value(FeatureType.NoseRootToBrowLine) in 0.02..0.48 &&
            rawFrame.value(FeatureType.NoseBridgeBrowLineOffset) in 0.0..0.56 &&
            rawFrame.value(FeatureType.NoseBridgeEyeLineOffset) in 0.0..0.30 &&
            rawFrame.value(FeatureType.LeftBrowEyeDistance) in 0.02..0.44 &&
            rawFrame.value(FeatureType.RightBrowEyeDistance) in 0.02..0.44 &&
            rawFrame.value(FeatureType.BrowEyeDistanceAsymmetry) in 0.0..0.36 &&
            rawFrame.value(FeatureType.NoseRootEyeLineDistance) in 0.0..0.24 &&
            rawFrame.value(FeatureType.NoseTipEyeLineDistance) in 0.06..0.52 &&
            rawFrame.value(FeatureType.NoseLateralOffset) in 0.0..0.28 &&
            rawFrame.value(FeatureType.LeftPeriocularArea) in 0.001..0.10 &&
            rawFrame.value(FeatureType.RightPeriocularArea) in 0.001..0.10 &&
            rawFrame.value(FeatureType.PeriocularAreaAsymmetry) in 0.0..0.10 &&
            rawFrame.value(FeatureType.LeftMidFaceTriangle) in 0.004..0.22 &&
            rawFrame.value(FeatureType.RightMidFaceTriangle) in 0.004..0.22 &&
            rawFrame.value(FeatureType.MidFaceTriangleAsymmetry) in 0.0..0.16 &&
            rawFrame.value(FeatureType.LeftUpperMidfaceArea) in 0.002..0.14 &&
            rawFrame.value(FeatureType.RightUpperMidfaceArea) in 0.002..0.14 &&
            rawFrame.value(FeatureType.UpperMidfaceAreaAsymmetry) in 0.0..0.10 &&
            rawFrame.value(FeatureType.LeftNoseCheekArea) in 0.002..0.14 &&
            rawFrame.value(FeatureType.RightNoseCheekArea) in 0.002..0.14 &&
            rawFrame.value(FeatureType.NoseCheekAreaAsymmetry) in 0.0..0.10 &&
            rawFrame.value(FeatureType.LeftTempleCheekSlope) in 0.0..1.05 &&
            rawFrame.value(FeatureType.RightTempleCheekSlope) in 0.0..1.05 &&
            rawFrame.value(FeatureType.TempleCheekSlopeAsymmetry) in 0.0..0.70 &&
            rawFrame.value(FeatureType.MidfaceWidthRatio) in 0.35..1.35 &&
            rawFrame.value(FeatureType.LeftBrowArchHeight) in 0.0..0.16 &&
            rawFrame.value(FeatureType.RightBrowArchHeight) in 0.0..0.16 &&
            rawFrame.value(FeatureType.BrowArchAsymmetry) in 0.0..0.16 &&
            rawFrame.value(FeatureType.BrowLineTilt) in 0.0..0.65 &&
            rawFrame.value(FeatureType.LeftEyeCornerTilt) in 0.0..0.80 &&
            rawFrame.value(FeatureType.RightEyeCornerTilt) in 0.0..0.80 &&
            rawFrame.value(FeatureType.EyeCornerTiltAsymmetry) in 0.0..0.80 &&
            rawFrame.value(FeatureType.InnerEyeNoseTipTriangle) in 0.001..0.14 &&
            rawFrame.value(FeatureType.BrowNoseRootTriangle) in 0.0..0.10 &&
            rawFrame.value(FeatureType.NoseRootLateralOffset) in 0.0..0.24 &&
            rawFrame.value(FeatureType.LeftNoseWing) in 0.02..0.34 &&
            rawFrame.value(FeatureType.RightNoseWing) in 0.02..0.34 &&
            rawFrame.value(FeatureType.NoseWingAsymmetry) in 0.0..0.22 &&
            rawFrame.value(FeatureType.NoseBaseTriangle) in 0.0..0.08 &&
            rawFrame.value(FeatureType.MouthHeight) in 0.00..0.22 &&
            rawFrame.value(FeatureType.MouthAspect) in 0.00..1.00 &&
            rawFrame.value(FeatureType.ChinMouthDistance) in 0.08..0.62 &&
            rawFrame.value(FeatureType.ChinJawOffset) in 0.22..0.95 &&
            rawFrame.value(FeatureType.EyeNoseLeft) in 0.10..0.62 &&
            rawFrame.value(FeatureType.EyeNoseRight) in 0.10..0.62 &&
            rawFrame.value(FeatureType.EyeNoseSymmetry) in 0.0..0.38 &&
            rawFrame.value(FeatureType.UpperFaceWidth) in 0.45..1.25 &&
            rawFrame.value(FeatureType.UpperFaceAspect) in 0.12..1.10 &&
            rawFrame.value(FeatureType.CheekboneWidth) in 0.45..1.25 &&
            rawFrame.value(FeatureType.LeftCheekNose) in 0.18..0.80 &&
            rawFrame.value(FeatureType.RightCheekNose) in 0.18..0.80 &&
            rawFrame.value(FeatureType.CheekNoseSymmetry) in 0.0..0.45 &&
            rawFrame.value(FeatureType.LeftBrowOuterEyeGap) in 0.02..0.42 &&
            rawFrame.value(FeatureType.RightBrowOuterEyeGap) in 0.02..0.42 &&
            rawFrame.value(FeatureType.BrowOuterGapAsymmetry) in 0.0..0.34 &&
            rawFrame.value(FeatureType.LeftBrowInnerEyeGap) in 0.02..0.42 &&
            rawFrame.value(FeatureType.RightBrowInnerEyeGap) in 0.02..0.42 &&
            rawFrame.value(FeatureType.BrowInnerGapAsymmetry) in 0.0..0.34 &&
            rawFrame.value(FeatureType.ForeheadToEyeLine) in 0.12..0.70 &&
            rawFrame.value(FeatureType.InterBrowDistance) in 0.05..0.70 &&
            rawFrame.value(FeatureType.LeftBrowNoseRoot) in 0.03..0.48 &&
            rawFrame.value(FeatureType.RightBrowNoseRoot) in 0.03..0.48 &&
            rawFrame.value(FeatureType.BrowNoseRootAsymmetry) in 0.0..0.34 &&
            rawFrame.value(FeatureType.LeftInnerEyeNoseRoot) in 0.02..0.36 &&
            rawFrame.value(FeatureType.RightInnerEyeNoseRoot) in 0.02..0.36 &&
            rawFrame.value(FeatureType.InnerEyeNoseRootAsymmetry) in 0.0..0.30 &&
            rawFrame.value(FeatureType.LeftTempleEye) in 0.03..0.58 &&
            rawFrame.value(FeatureType.RightTempleEye) in 0.03..0.58 &&
            rawFrame.value(FeatureType.TempleEyeAsymmetry) in 0.0..0.34 &&
            rawFrame.value(FeatureType.EyeWidthAsymmetry) in 0.0..0.75 &&
            rawFrame.value(FeatureType.LeftBrowSlope) in 0.0..0.85 &&
            rawFrame.value(FeatureType.RightBrowSlope) in 0.0..0.85 &&
            rawFrame.value(FeatureType.BrowSlopeAsymmetry) in 0.0..0.70 &&
            rawFrame.value(FeatureType.NoseTipDepth) in 0.0..0.80 &&
            rawFrame.value(FeatureType.LeftEyeNoseDepth) in 0.0..0.80 &&
            rawFrame.value(FeatureType.RightEyeNoseDepth) in 0.0..0.80 &&
            rawFrame.value(FeatureType.EyeNoseDepthAsymmetry) in 0.0..0.70 &&
            rawFrame.value(FeatureType.LeftCheekNoseDepth) in 0.0..0.80 &&
            rawFrame.value(FeatureType.RightCheekNoseDepth) in 0.0..0.80 &&
            rawFrame.value(FeatureType.CheekNoseDepthAsymmetry) in 0.0..0.70 &&
            rawFrame.value(FeatureType.LeftForeheadTempleArea) in 0.0..0.18 &&
            rawFrame.value(FeatureType.RightForeheadTempleArea) in 0.0..0.18 &&
            rawFrame.value(FeatureType.ForeheadTempleAreaAsymmetry) in 0.0..0.12 &&
            rawFrame.value(FeatureType.LeftEyeBrowNoseTriangle) in 0.0..0.12 &&
            rawFrame.value(FeatureType.RightEyeBrowNoseTriangle) in 0.0..0.12 &&
            rawFrame.value(FeatureType.EyeBrowNoseTriangleAsymmetry) in 0.0..0.10 &&
            rawFrame.value(FeatureType.NoseBridgeEyeTriangle) in 0.0..0.12 &&
            rawFrame.value(FeatureType.NoseBridgeToEyeSpanRatio) in 0.05..1.80 &&
            rawFrame.value(FeatureType.LeftInnerEyeNoseBridgeTriangle) in 0.0..0.12 &&
            rawFrame.value(FeatureType.RightInnerEyeNoseBridgeTriangle) in 0.0..0.12 &&
            rawFrame.value(FeatureType.InnerEyeNoseBridgeTriangleAsymmetry) in 0.0..0.10 &&
            rawFrame.value(FeatureType.NoseTipInnerEyeLineDistance) in 0.06..0.52 &&
            rawFrame.value(FeatureType.NoseBridgeInnerEyeSpanRatio) in 0.05..1.80 &&
            rawFrame.value(FeatureType.NoseTipDepthToEyeSpanRatio) in 0.0..0.80

        val irisPlausible = !rawFrame.quality.irisLandmarksAvailable ||
            (rawFrame.value(FeatureType.LeftIrisEyeOffset) in 0.0..0.80 &&
                rawFrame.value(FeatureType.RightIrisEyeOffset) in 0.0..0.80 &&
                rawFrame.value(FeatureType.IrisOffsetAsymmetry) in 0.0..0.80 &&
                rawFrame.value(FeatureType.IrisDistance) in 0.15..0.78 &&
                rawFrame.value(FeatureType.LeftIrisNoseRoot) in 0.03..0.62 &&
                rawFrame.value(FeatureType.RightIrisNoseRoot) in 0.03..0.62 &&
                rawFrame.value(FeatureType.IrisNoseRootAsymmetry) in 0.0..0.45 &&
                rawFrame.value(FeatureType.IrisSpanRatio) in 0.45..1.35)

        return nonIrisPlausible && irisPlausible
    }

    private fun range(samples: List<RawFeatureFrame>, type: FeatureType): Double {
        val values = samples.map { it.value(type) }
        return values.maxOrNull().orZero() - values.minOrNull().orZero()
    }

    private fun standardDeviation(samples: List<RawFeatureFrame>, type: FeatureType): Double {
        val values = samples.map { it.value(type) }
        val mean = values.average()
        val variance = values.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / values.size.toDouble()
        return sqrt(variance)
    }

    private fun enrollmentIdentityStabilityTypes(samples: List<RawFeatureFrame>): List<FeatureType> {
        val irisAvailable = samples.all { it.quality.irisLandmarksAvailable }
        return FeatureType.ordered.filter { type ->
            type.group in CRITICAL_IDENTITY_GROUPS &&
                (irisAvailable || !type.isIrisFeature)
        }
    }

    private fun hasStableIdentityRegionSupport(
        samples: List<RawFeatureFrame>,
        types: List<FeatureType>
    ): Boolean {
        val requiredRegions = listOf(FeatureGroup.UPPER_FACE, FeatureGroup.MID_FACE, FeatureGroup.EYE)
        val regionsStable = requiredRegions.all { region ->
            stableWeightRatio(samples, types.filter { it.group == region }) >= MIN_REGION_STABLE_WEIGHT_RATIO
        }
        if (!regionsStable) return false

        val visibleWhenLowerFaceCovered = types.filter {
            it.group == FeatureGroup.UPPER_FACE ||
                it.group == FeatureGroup.MID_FACE ||
                it.group == FeatureGroup.EYE
        }
        if (stableWeightRatio(samples, visibleWhenLowerFaceCovered) < MIN_COMBINED_STABLE_WEIGHT_RATIO) {
            return false
        }

        val leftSideRatio = stableWeightRatio(samples, types.filter { it.dependsOnLeftEye && !it.dependsOnRightEye })
        val rightSideRatio = stableWeightRatio(samples, types.filter { it.dependsOnRightEye && !it.dependsOnLeftEye })
        return leftSideRatio >= MIN_SIDE_STABLE_WEIGHT_RATIO &&
            rightSideRatio >= MIN_SIDE_STABLE_WEIGHT_RATIO
    }

    private fun stableWeightRatio(samples: List<RawFeatureFrame>, types: List<FeatureType>): Double {
        val totalWeight = types.sumOf { it.ruleWeight }
        if (totalWeight <= EPSILON) return 0.0
        val stableWeight = types.sumOf { type ->
            if (normalizedEnrollmentSpread(samples, type) <= REGIONAL_STABLE_SIGMA_MULTIPLIER) {
                type.ruleWeight
            } else {
                0.0
            }
        }
        return stableWeight / totalWeight
    }

    private fun normalizedEnrollmentSpread(samples: List<RawFeatureFrame>, type: FeatureType): Double {
        return standardDeviation(samples, type) / max(type.minimumSigma, EPSILON)
    }

    private fun average(samples: List<RawFeatureFrame>, type: FeatureType): Double {
        return samples.map { it.value(type) }.average()
    }

    private fun hasPersistentEyeOcclusion(samples: List<RawFeatureFrame>): Boolean {
        val leftEyeMean = average(samples, FeatureType.LeftEyeOpen)
        val rightEyeMean = average(samples, FeatureType.RightEyeOpen)
        val eyeOpenImbalance = abs(leftEyeMean - rightEyeMean) / maxOf((leftEyeMean + rightEyeMean) * 0.5, EPSILON)
        val asymmetryMean = average(samples, FeatureType.EyeOpenAsymmetry)
        val upperArchAsymmetryMean = average(samples, FeatureType.UpperEyelidArchAsymmetry)
        val lowerArchAsymmetryMean = average(samples, FeatureType.LowerEyelidArchAsymmetry)
        val upperArchImbalance = relativeImbalance(
            average(samples, FeatureType.LeftUpperEyelidArch),
            average(samples, FeatureType.RightUpperEyelidArch)
        )
        val lowerArchImbalance = relativeImbalance(
            average(samples, FeatureType.LeftLowerEyelidArch),
            average(samples, FeatureType.RightLowerEyelidArch)
        )

        return leftEyeMean < MIN_CLEAN_EYE_OPEN_MEAN ||
            rightEyeMean < MIN_CLEAN_EYE_OPEN_MEAN ||
            eyeOpenImbalance > MAX_CLEAN_EYE_OPEN_IMBALANCE ||
            asymmetryMean > MAX_CLEAN_EYE_OPEN_ASYMMETRY_MEAN ||
            upperArchAsymmetryMean > MAX_CLEAN_EYELID_ARCH_ASYMMETRY_MEAN ||
            lowerArchAsymmetryMean > MAX_CLEAN_EYELID_ARCH_ASYMMETRY_MEAN ||
            upperArchImbalance > MAX_CLEAN_EYELID_ARCH_IMBALANCE ||
            lowerArchImbalance > MAX_CLEAN_EYELID_ARCH_IMBALANCE
    }

    private fun hasEnrollmentLiveness(samples: List<RawFeatureFrame>): Boolean {
        val eyeVariance = standardDeviation(samples, FeatureType.LeftEyeOpen).squared() +
            standardDeviation(samples, FeatureType.RightEyeOpen).squared()
        val poseVariance = standardDeviation(samples, FeatureType.Yaw).squared() +
            standardDeviation(samples, FeatureType.Pitch).squared()
        val score = eyeVariance / ENROLLMENT_EYE_VARIANCE_TARGET +
            poseVariance / ENROLLMENT_POSE_VARIANCE_TARGET
        return score >= MIN_ENROLLMENT_LIVENESS_SCORE
    }

    private fun relativeImbalance(first: Double, second: Double): Double {
        return abs(first - second) / maxOf((first + second) * 0.5, MIN_EYELID_ARCH_IMBALANCE_DENOMINATOR)
    }

    private fun Double?.orZero(): Double = this ?: 0.0
    private fun Double.squared(): Double = this * this

    private const val MAX_ENROLLMENT_POSE = 0.48
    private const val MAX_ENROLLMENT_POSE_RANGE = 0.36
    private const val MAX_LOWER_FACE_SIGMA_MULTIPLIER = 4.2
    private const val MAX_CORE_SIGMA_MULTIPLIER = 4.8
    private const val REGIONAL_STABLE_SIGMA_MULTIPLIER = 3.8
    private const val MIN_REGION_STABLE_WEIGHT_RATIO = 0.68
    private const val MIN_COMBINED_STABLE_WEIGHT_RATIO = 0.76
    private const val MIN_SIDE_STABLE_WEIGHT_RATIO = 0.58
    private const val ENROLLMENT_EYE_VARIANCE_TARGET = 0.0018
    private const val ENROLLMENT_POSE_VARIANCE_TARGET = 0.0040
    private const val MIN_ENROLLMENT_LIVENESS_SCORE = 0.03
    private const val MIN_CLEAN_EYE_OPEN_MEAN = 0.045
    private const val MAX_CLEAN_EYE_OPEN_IMBALANCE = 0.65
    private const val MAX_CLEAN_EYE_OPEN_ASYMMETRY_MEAN = 0.55
    private const val MAX_CLEAN_EYELID_ARCH_ASYMMETRY_MEAN = 0.16
    private const val MAX_CLEAN_EYELID_ARCH_IMBALANCE = 1.00
    private const val MIN_EYELID_ARCH_IMBALANCE_DENOMINATOR = 0.030
    private const val EPSILON = 1.0e-6
    private val CRITICAL_IDENTITY_GROUPS = setOf(
        FeatureGroup.UPPER_FACE,
        FeatureGroup.MID_FACE,
        FeatureGroup.EYE,
        FeatureGroup.GLOBAL
    )
}


