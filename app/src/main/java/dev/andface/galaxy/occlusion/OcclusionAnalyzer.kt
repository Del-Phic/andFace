package dev.andface.galaxy.occlusion

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureGroup
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.ObservableFeatureFrame
import dev.andface.galaxy.feature.RawFeatureFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OcclusionAnalyzer {
    fun analyze(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile?,
        hint: OcclusionHint
    ): ObservableFeatureFrame = analyzeInternal(rawFrame, profile, hint, allowAutomaticInference = true)

    /**
     * Applies only the operator-confirmed occlusion state. Landmark deviation alone is not
     * trusted to remove identity evidence because ordinary expression/pose changes can look
     * like a mask or glasses when enrollment variance is tight.
     */
    fun analyzeResolved(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile?,
        hint: OcclusionHint
    ): ObservableFeatureFrame = analyzeInternal(rawFrame, profile, hint, allowAutomaticInference = false)

    private fun analyzeInternal(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile?,
        hint: OcclusionHint,
        allowAutomaticInference: Boolean
    ): ObservableFeatureFrame {
        val inferredLowerFaceOcclusion = allowAutomaticInference && inferLowerFaceOcclusion(rawFrame, profile)
        val lowerFaceOccluded = hint.lowerFaceCovered || inferredLowerFaceOcclusion
        val midFaceOccluded = !hint.lowerFaceCovered && inferredLowerFaceOcclusion && inferMidFaceOcclusion(rawFrame, profile)
        val leftEyePatched = hint.leftEyePatch ||
            (allowAutomaticInference && inferEyePatch(rawFrame, profile, FeatureType.LeftEyeOpen))
        val rightEyePatched = hint.rightEyePatch ||
            (allowAutomaticInference && inferEyePatch(rawFrame, profile, FeatureType.RightEyeOpen))
        val leftEyeUnstable = allowAutomaticInference &&
            inferGlassesEyeInstability(rawFrame, profile, hint, FeatureType.LeftEyeOpen)
        val rightEyeUnstable = allowAutomaticInference &&
            inferGlassesEyeInstability(rawFrame, profile, hint, FeatureType.RightEyeOpen)
        val evidence = FeatureType.ordered.map { type ->
            buildEvidence(
                type = type,
                rawFrame = rawFrame,
                profile = profile,
                hint = hint,
                lowerFaceOccluded = lowerFaceOccluded,
                midFaceOccluded = midFaceOccluded,
                leftEyePatched = leftEyePatched,
                rightEyePatched = rightEyePatched,
                leftEyeUnstable = leftEyeUnstable,
                rightEyeUnstable = rightEyeUnstable
            )
        }

        val coverageNumerator = evidence.sumOf { item ->
            if (!item.observable) {
                0.0
            } else {
                item.visibility * item.type.ruleWeight
            }
        }
        val coverageDenominator = FeatureType.ordered.sumOf { it.ruleWeight }
        val summary = buildSummary(
            lowerFaceOccluded = lowerFaceOccluded,
            midFaceOccluded = midFaceOccluded,
            hint = hint,
            leftEyePatched = leftEyePatched,
            rightEyePatched = rightEyePatched,
            leftEyeUnstable = leftEyeUnstable,
            rightEyeUnstable = rightEyeUnstable
        )
        return ObservableFeatureFrame(
            evidence = evidence,
            coverage = (coverageNumerator / coverageDenominator).coerceIn(0.0, 1.0),
            observableCount = evidence.count { it.observable },
            occlusionSummary = summary
        )
    }

    private fun buildEvidence(
        type: FeatureType,
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile?,
        hint: OcclusionHint,
        lowerFaceOccluded: Boolean,
        midFaceOccluded: Boolean,
        leftEyePatched: Boolean,
        rightEyePatched: Boolean,
        leftEyeUnstable: Boolean,
        rightEyeUnstable: Boolean
    ): FeatureEvidence {
        var visibility = 1.0
        var observable = true
        var reason = "visible"
        val frameQualityConfidence = rawFrame.quality.accessScoringConfidence

        if (lowerFaceOccluded && (type.isLowerFace || type == FeatureType.FaceAspect)) {
            visibility = 0.0
            observable = false
            reason = "lower_face_occluded"
        } else if (lowerFaceOccluded && type.isUpperOrMidFace) {
            visibility = min(1.0, visibility * 1.08)
            reason = "upper_mid_boosted"
        }

        if (hint.lowerFaceCovered && type in MANUAL_MASK_COVERED_MID_TYPES) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "manual_mask_covered")
        }

        if (midFaceOccluded && type.isMidFace) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "mid_face_occluded")
        }

        if (type.isIrisFeature && !rawFrame.quality.irisLandmarksAvailable) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "iris_landmarks_unavailable")
        }

        if (isProfileIrisFeatureUnavailable(profile, type)) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "iris_unavailable_in_enrollment")
        }

        if (observable && frameQualityConfidence < 1.0) {
            visibility *= frameQualityConfidence
            reason = appendReason(reason, "frame_quality_reduced")
        }

        if (hint.glasses) {
            if (type.isEyeFeature) {
                visibility *= 0.55
                reason = appendReason(reason, "glasses_eye_reduced")
            } else if (type.isBrowOrNose) {
                visibility = min(1.0, visibility * 1.05)
                reason = appendReason(reason, "glasses_brow_nose_boosted")
            }
        }

        if (leftEyePatched && type.dependsOnLeftEye) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "left_eye_patch")
        }
        if (rightEyePatched && type.dependsOnRightEye) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "right_eye_patch")
        }

        if (leftEyeUnstable && type.isLeftEyeLocalFeature) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "glasses_left_eye_unstable")
        }
        if (rightEyeUnstable && type.isRightEyeLocalFeature) {
            visibility = 0.0
            observable = false
            reason = appendReason(reason, "glasses_right_eye_unstable")
        }

        val stabilityConfidence = profile?.featureReliability(type) ?: 1.0
        if (stabilityConfidence < 1.0) {
            visibility *= stabilityConfidence
            reason = appendReason(reason, "unstable_enrollment_feature")
        }

        if (visibility < MIN_VISIBILITY_FOR_OBSERVABLE) {
            observable = false
            reason = appendReason(reason, "low_visibility")
        }

        return FeatureEvidence(
            type = type,
            value = rawFrame.value(type),
            visibility = visibility.coerceIn(0.0, 1.0),
            observable = observable,
            reason = reason
        )
    }

    private fun inferLowerFaceOcclusion(rawFrame: RawFeatureFrame, profile: EnrollmentProfile?): Boolean {
        if (profile == null) return false

        val lowerMismatchCount = LOWER_FACE_TYPES.count { type ->
            zScore(rawFrame, profile, type) >= LOWER_OCCLUSION_Z
        }
        val lowerSoftMismatchCount = LOWER_FACE_TYPES.count { type ->
            zScore(rawFrame, profile, type) >= LOWER_SOFT_OCCLUSION_Z
        }
        val upperSupportCount = UPPER_MID_TYPES.count { type ->
            zScore(rawFrame, profile, type) <= UPPER_SUPPORT_Z
        }

        val strongLowerOcclusion = lowerMismatchCount >= MIN_LOWER_OCCLUSION_HARD_OUTLIERS &&
            upperSupportCount >= MIN_LOWER_OCCLUSION_UPPER_SUPPORT
        val distributedLowerOcclusion = lowerSoftMismatchCount >= MIN_LOWER_OCCLUSION_SOFT_OUTLIERS &&
            upperSupportCount >= MIN_SOFT_LOWER_OCCLUSION_UPPER_SUPPORT
        return strongLowerOcclusion || distributedLowerOcclusion
    }

    private fun inferMidFaceOcclusion(rawFrame: RawFeatureFrame, profile: EnrollmentProfile?): Boolean {
        if (profile == null) return false
        val midMismatchCount = MID_FACE_TYPES.count { type ->
            zScore(rawFrame, profile, type) >= MID_FACE_OCCLUSION_Z
        }
        val upperSupportCount = UPPER_IDENTITY_SUPPORT_TYPES.count { type ->
            zScore(rawFrame, profile, type) <= UPPER_SUPPORT_Z
        }
        return midMismatchCount >= 2 && upperSupportCount >= 4
    }

    private fun inferEyePatch(rawFrame: RawFeatureFrame, profile: EnrollmentProfile?, eyeType: FeatureType): Boolean {
        if (profile == null) return false
        val oppositeEyeType = oppositeEyeOpenType(eyeType)
        val affectedOutlierCount = eyeLocalOutlierCount(rawFrame, profile, eyeType, EYE_PATCH_Z)
        val oppositeOutlierCount = eyeLocalOutlierCount(rawFrame, profile, oppositeEyeType, EYE_PATCH_Z)
        val eyeOpenOutlier = zScore(rawFrame, profile, eyeType) >= EYE_PATCH_Z
        val oppositeEyeOpenOutlier = zScore(rawFrame, profile, oppositeEyeType) >= EYE_PATCH_Z
        val affectedLooksAbsolutelyCovered = looksAbsolutelyEyeCovered(rawFrame, eyeType, oppositeEyeType)
        val oppositeLooksAbsolutelyCovered = looksAbsolutelyEyeCovered(rawFrame, oppositeEyeType, eyeType)
        val identitySupportCount = PATCH_SUPPORT_TYPES.count { type ->
            zScore(rawFrame, profile, type) <= UPPER_SUPPORT_Z
        }
        val affectedLooksPatched = affectedLooksAbsolutelyCovered ||
            eyeOpenOutlier || affectedOutlierCount >= MIN_EYE_PATCH_LOCAL_OUTLIERS
        val oppositeLooksPatched = oppositeLooksAbsolutelyCovered ||
            oppositeEyeOpenOutlier || oppositeOutlierCount >= MIN_EYE_PATCH_LOCAL_OUTLIERS
        return affectedLooksPatched && !oppositeLooksPatched && identitySupportCount >= MIN_EYE_PATCH_SUPPORT_COUNT
    }

    private fun inferGlassesEyeInstability(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile?,
        hint: OcclusionHint,
        eyeType: FeatureType
    ): Boolean {
        if (profile == null) return false
        val affectedOutlierCount = eyeLocalOutlierCount(rawFrame, profile, eyeType, GLASSES_EYE_OUTLIER_Z)
        val oppositeOutlierCount = eyeLocalOutlierCount(rawFrame, profile, oppositeEyeOpenType(eyeType), GLASSES_EYE_OUTLIER_Z)
        val oppositeEyeType = oppositeEyeOpenType(eyeType)
        val eyeOpenOutlier = zScore(rawFrame, profile, eyeType) >= GLASSES_EYE_OUTLIER_Z
        val supportCount = PATCH_SUPPORT_TYPES.count { type ->
            zScore(rawFrame, profile, type) <= UPPER_SUPPORT_Z
        }
        val oppositeEyeOpenOutlier = zScore(rawFrame, profile, oppositeEyeType) >= GLASSES_EYE_OUTLIER_Z
        val bothEyesAbsolutelyCovered = rawFrame.value(eyeType) <= ABSOLUTE_EYE_COVERED_THRESHOLD &&
            rawFrame.value(oppositeEyeType) <= ABSOLUTE_EYE_COVERED_THRESHOLD
        val affectedEyeUnstable = bothEyesAbsolutelyCovered || eyeOpenOutlier || affectedOutlierCount >= MIN_GLASSES_LOCAL_OUTLIERS
        val bothEyesUnstable = bothEyesAbsolutelyCovered ||
            (affectedOutlierCount >= MIN_GLASSES_LOCAL_OUTLIERS &&
                oppositeOutlierCount >= MIN_GLASSES_LOCAL_OUTLIERS) || (eyeOpenOutlier && oppositeEyeOpenOutlier)
        return affectedEyeUnstable && supportCount >= MIN_EYE_PATCH_SUPPORT_COUNT && (hint.glasses || bothEyesUnstable)
    }

    private fun eyeLocalOutlierCount(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        eyeType: FeatureType,
        threshold: Double
    ): Int {
        return eyeLocalTypes(rawFrame, eyeType).count { type ->
            zScore(rawFrame, profile, type) >= threshold
        }
    }

    private fun eyeLocalTypes(rawFrame: RawFeatureFrame, eyeType: FeatureType): List<FeatureType> {
        val baseTypes = if (eyeType == FeatureType.LeftEyeOpen) {
            listOf(
                FeatureType.LeftEyeOpen,
                FeatureType.LeftEyeHeight,
                FeatureType.LeftUpperEyelidArch,
                FeatureType.LeftLowerEyelidArch,
                FeatureType.LeftEyeWidth,
                FeatureType.LeftBrowEyeDistance,
                FeatureType.LeftEyeCornerTilt,
                FeatureType.LeftPeriocularArea,
                FeatureType.LeftOrbitalTriangle,
                FeatureType.LeftInnerBrowNoseArea,
                FeatureType.LeftEyeBrowNoseTriangle,
                FeatureType.LeftInnerEyeNoseBridgeTriangle,
                FeatureType.InnerEyeNoseBridgeTriangleAsymmetry
            )
        } else {
            listOf(
                FeatureType.RightEyeOpen,
                FeatureType.RightEyeHeight,
                FeatureType.RightUpperEyelidArch,
                FeatureType.RightLowerEyelidArch,
                FeatureType.RightEyeWidth,
                FeatureType.RightBrowEyeDistance,
                FeatureType.RightEyeCornerTilt,
                FeatureType.RightPeriocularArea,
                FeatureType.RightOrbitalTriangle,
                FeatureType.RightInnerBrowNoseArea,
                FeatureType.RightEyeBrowNoseTriangle,
                FeatureType.RightInnerEyeNoseBridgeTriangle,
                FeatureType.InnerEyeNoseBridgeTriangleAsymmetry
            )
        }
        if (!rawFrame.quality.irisLandmarksAvailable) return baseTypes
        val irisTypes = if (eyeType == FeatureType.LeftEyeOpen) {
            listOf(FeatureType.LeftIrisEyeOffset, FeatureType.LeftIrisNoseRoot)
        } else {
            listOf(FeatureType.RightIrisEyeOffset, FeatureType.RightIrisNoseRoot)
        }
        return baseTypes + irisTypes
    }

    private fun oppositeEyeOpenType(eyeType: FeatureType): FeatureType {
        return if (eyeType == FeatureType.LeftEyeOpen) FeatureType.RightEyeOpen else FeatureType.LeftEyeOpen
    }

    private fun looksAbsolutelyEyeCovered(
        rawFrame: RawFeatureFrame,
        affectedEyeType: FeatureType,
        oppositeEyeType: FeatureType
    ): Boolean {
        val affectedEyeOpen = rawFrame.value(affectedEyeType)
        val oppositeEyeOpen = rawFrame.value(oppositeEyeType)
        return affectedEyeOpen <= ABSOLUTE_EYE_COVERED_THRESHOLD &&
            oppositeEyeOpen >= ABSOLUTE_OPPOSITE_EYE_OPEN_THRESHOLD
    }

    private fun zScore(rawFrame: RawFeatureFrame, profile: EnrollmentProfile, type: FeatureType): Double {
        val sigma = max(profile.sigma(type), type.minimumSigma)
        return abs(rawFrame.value(type) - profile.mean(type)) / sigma
    }

    private fun isProfileIrisFeatureUnavailable(profile: EnrollmentProfile?, type: FeatureType): Boolean {
        if (profile == null || !type.isIrisFeature) return false
        val sigma = max(profile.sigma(type), type.minimumSigma)
        return sigma >= type.minimumSigma * PROFILE_IRIS_UNAVAILABLE_SIGMA_MULTIPLIER
    }

    private fun buildSummary(
        lowerFaceOccluded: Boolean,
        midFaceOccluded: Boolean,
        hint: OcclusionHint,
        leftEyePatched: Boolean,
        rightEyePatched: Boolean,
        leftEyeUnstable: Boolean,
        rightEyeUnstable: Boolean
    ): String {
        val parts = mutableListOf<String>()
        val glassesLikeOcclusion = hint.glasses || leftEyeUnstable || rightEyeUnstable
        if (lowerFaceOccluded) parts += "lower"
        if (midFaceOccluded) parts += "mid"
        if (glassesLikeOcclusion) parts += "glasses"
        if (leftEyePatched) parts += "left_eye"
        if (rightEyePatched) parts += "right_eye"
        return if (parts.isEmpty()) "clean" else parts.joinToString("+")
    }

    private fun appendReason(current: String, next: String): String {
        return if (current == "visible") next else "$current,$next"
    }

    companion object {
        private const val MIN_VISIBILITY_FOR_OBSERVABLE = 0.15
        private const val LOWER_OCCLUSION_Z = 1.8
        private const val LOWER_SOFT_OCCLUSION_Z = 1.25
        private const val MID_FACE_OCCLUSION_Z = 2.6
        private const val UPPER_SUPPORT_Z = 2.7
        private const val EYE_PATCH_Z = 2.4
        private const val GLASSES_EYE_OUTLIER_Z = 2.8
        private const val ABSOLUTE_EYE_COVERED_THRESHOLD = 0.045
        private const val ABSOLUTE_OPPOSITE_EYE_OPEN_THRESHOLD = 0.070
        private const val MIN_EYE_PATCH_LOCAL_OUTLIERS = 2
        private const val MIN_GLASSES_LOCAL_OUTLIERS = 2
        private const val MIN_EYE_PATCH_SUPPORT_COUNT = 3
        private const val MIN_LOWER_OCCLUSION_HARD_OUTLIERS = 2
        private const val MIN_LOWER_OCCLUSION_SOFT_OUTLIERS = 4
        private const val MIN_LOWER_OCCLUSION_UPPER_SUPPORT = 3
        private const val MIN_SOFT_LOWER_OCCLUSION_UPPER_SUPPORT = 8
        private const val PROFILE_IRIS_UNAVAILABLE_SIGMA_MULTIPLIER = 40.0

        private val MANUAL_MASK_COVERED_MID_TYPES = setOf(
            FeatureType.NoseWidth,
            FeatureType.EyeNoseLeft,
            FeatureType.EyeNoseRight,
            FeatureType.EyeNoseSymmetry,
            FeatureType.CheekboneWidth,
            FeatureType.LeftCheekNose,
            FeatureType.RightCheekNose,
            FeatureType.CheekNoseSymmetry,
            FeatureType.LeftUnderEyeCheek,
            FeatureType.RightUnderEyeCheek,
            FeatureType.UnderEyeCheekAsymmetry,
            FeatureType.NoseTipEyeLineDistance,
            FeatureType.NoseLateralOffset,
            FeatureType.LeftMidFaceTriangle,
            FeatureType.RightMidFaceTriangle,
            FeatureType.MidFaceTriangleAsymmetry,
            FeatureType.LeftNoseWing,
            FeatureType.RightNoseWing,
            FeatureType.NoseWingAsymmetry,
            FeatureType.NoseBaseTriangle,
            FeatureType.LeftUpperMidfaceArea,
            FeatureType.RightUpperMidfaceArea,
            FeatureType.UpperMidfaceAreaAsymmetry,
            FeatureType.LeftNoseCheekArea,
            FeatureType.RightNoseCheekArea,
            FeatureType.NoseCheekAreaAsymmetry,
            FeatureType.MidfaceWidthRatio,
            FeatureType.InnerEyeNoseTipTriangle,
            FeatureType.NoseTipDepth,
            FeatureType.LeftEyeNoseDepth,
            FeatureType.RightEyeNoseDepth,
            FeatureType.EyeNoseDepthAsymmetry,
            FeatureType.LeftCheekNoseDepth,
            FeatureType.RightCheekNoseDepth,
            FeatureType.CheekNoseDepthAsymmetry,
            FeatureType.NoseTipInnerEyeLineDistance,
            FeatureType.NoseTipDepthToEyeSpanRatio
        )

        private val LOWER_FACE_TYPES = FeatureType.ordered
            .filter { type -> type.group == FeatureGroup.LOWER_FACE || type == FeatureType.FaceAspect }

        private val MID_FACE_TYPES = FeatureType.ordered
            .filter { type -> type.group == FeatureGroup.MID_FACE }

        private val UPPER_MID_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.EYE
            }

        private val UPPER_IDENTITY_SUPPORT_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.EYE ||
                    type.group == FeatureGroup.POSE
            }

        private val PATCH_SUPPORT_TYPES = UPPER_IDENTITY_SUPPORT_TYPES

    }
}




