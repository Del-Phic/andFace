package dev.andface.galaxy.enrollment

import dev.andface.galaxy.feature.FeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentProfileTest {
    @Test
    fun featureReliabilityIsOneForStableCleanEnrollmentFeature() {
        val profile = profileWithSigma(FeatureType.EyeDistance.minimumSigma)

        assertEquals(1.0, profile.featureReliability(FeatureType.EyeDistance), 1e-12)
        assertEquals(
            FeatureType.EyeDistance.ruleWeight,
            profile.evidenceWeight(FeatureType.EyeDistance),
            1e-12
        )
    }

    @Test
    fun featureReliabilityFallsBackForNoisyEnrollmentFeature() {
        val profile = profileWithSigma(FeatureType.EyeDistance.minimumSigma * 12.0)

        assertEquals(0.45, profile.featureReliability(FeatureType.EyeDistance), 1e-12)
        assertTrue(profile.evidenceWeight(FeatureType.EyeDistance) < FeatureType.EyeDistance.ruleWeight)
    }

    private fun profileWithSigma(sigma: Double): EnrollmentProfile {
        val sigmas = DoubleArray(FeatureType.COUNT) { FeatureType.ordered[it].minimumSigma }
        sigmas[FeatureType.EyeDistance.ordinal] = sigma
        return EnrollmentProfile(
            userId = "USER_1",
            createdAtMs = 0L,
            modelSha256 = "test",
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = 30,
            means = DoubleArray(FeatureType.COUNT),
            sigmas = sigmas,
            covariance = Array(FeatureType.COUNT) { row ->
                DoubleArray(FeatureType.COUNT) { col ->
                    if (row == col) sigmas[row] * sigmas[row] else 0.0
                }
            }
        )
    }
}