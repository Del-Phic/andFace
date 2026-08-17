package dev.andface.galaxy.enrollment

import dev.andface.galaxy.feature.FeatureType
import kotlin.math.max

data class EnrollmentProfile(
    val userId: String,
    val createdAtMs: Long,
    val modelSha256: String,
    val policyVersion: Int,
    val sampleCount: Int,
    val means: DoubleArray,
    val sigmas: DoubleArray,
    val covariance: Array<DoubleArray>,
    val calibratedCleanMahalanobisFloor: Double = DEFAULT_CLEAN_MAHALANOBIS_FLOOR,
    val calibratedCleanFinalScoreFloor: Double = DEFAULT_CLEAN_FINAL_SCORE_FLOOR
) {
    fun mean(type: FeatureType): Double = means[type.ordinal]

    fun sigma(type: FeatureType): Double = sigmas[type.ordinal]

    fun featureReliability(type: FeatureType): Double {
        val sigma = max(sigma(type), type.minimumSigma)
        val fullConfidenceSigma = type.minimumSigma * STABLE_FEATURE_SIGMA_MULTIPLIER
        if (sigma <= fullConfidenceSigma) return 1.0

        val floorSigma = type.minimumSigma * LOW_CONFIDENCE_SIGMA_MULTIPLIER
        val ratio = ((sigma - fullConfidenceSigma) / max(floorSigma - fullConfidenceSigma, 1.0e-6))
            .coerceIn(0.0, 1.0)
        return (1.0 - ratio * (1.0 - MIN_FEATURE_RELIABILITY))
            .coerceIn(MIN_FEATURE_RELIABILITY, 1.0)
    }

    fun evidenceWeight(type: FeatureType): Double = type.ruleWeight * featureReliability(type)

    companion object {
        const val CURRENT_POLICY_VERSION = 16
        // Enrollment frames are strongly correlated because they are captured in one
        // short session. A 0.94 runtime floor overfits that session and rejects the
        // same person after a small camera/pose change. The fuzzy model remains the
        // primary decision model and the independent identity gates remain active.
        const val DEFAULT_CLEAN_MAHALANOBIS_FLOOR = 0.88
        const val DEFAULT_CLEAN_FINAL_SCORE_FLOOR = 0.86
        private const val STABLE_FEATURE_SIGMA_MULTIPLIER = 5.0
        private const val LOW_CONFIDENCE_SIGMA_MULTIPLIER = 12.0
        private const val MIN_FEATURE_RELIABILITY = 0.45
    }
}
