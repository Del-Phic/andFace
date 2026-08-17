package dev.andface.galaxy.auth

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationRegionalVisibilityGateTest {
    @Test
    fun regionalInlierBalanceRequiresEnoughVisibilityInsideEachRequiredRegion() {
        val engine = AuthenticationEngine()
        val method = AuthenticationEngine::class.java.getDeclaredMethod(
            "hasRegionalIdentityInlierBalance",
            RawFeatureFrame::class.java,
            EnrollmentProfile::class.java,
            List::class.java,
            String::class.java
        )
        method.isAccessible = true
        val frame = RawFeatureFrame(DoubleArray(FeatureType.COUNT), timestampMs = 0L)
        val profile = zeroProfile()

        val lowMidVisibility = method.invoke(
            engine,
            frame,
            profile,
            balancedCleanEvidence(midVisibility = 0.1),
            "clean"
        ) as Boolean
        val adequateVisibility = method.invoke(
            engine,
            frame,
            profile,
            balancedCleanEvidence(midVisibility = 1.0),
            "clean"
        ) as Boolean

        assertFalse("matching values must still fail when a required region has too little visibility", lowMidVisibility)
        assertTrue("matching values with enough per-region visibility should pass", adequateVisibility)
    }

    private fun balancedCleanEvidence(midVisibility: Double): List<FeatureEvidence> {
        return listOf(
            FeatureEvidence(FeatureType.EyeDistance, value = 0.0, visibility = 1.0, observable = true),
            FeatureEvidence(FeatureType.BrowDistance, value = 0.0, visibility = 1.0, observable = true),
            FeatureEvidence(FeatureType.UpperFaceWidth, value = 0.0, visibility = 1.0, observable = true),
            FeatureEvidence(FeatureType.NoseWidth, value = 0.0, visibility = midVisibility, observable = true),
            FeatureEvidence(FeatureType.NoseBridgeLength, value = 0.0, visibility = midVisibility, observable = true),
            FeatureEvidence(FeatureType.EyeNoseLeft, value = 0.0, visibility = midVisibility, observable = true),
            FeatureEvidence(FeatureType.NoseToChin, value = 0.0, visibility = 1.0, observable = true),
            FeatureEvidence(FeatureType.MouthWidth, value = 0.0, visibility = 1.0, observable = true)
        )
    }

    private fun zeroProfile(): EnrollmentProfile {
        val variance = 0.05 * 0.05
        return EnrollmentProfile(
            userId = "USER_1",
            createdAtMs = 0L,
            modelSha256 = "test",
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = 45,
            means = DoubleArray(FeatureType.COUNT),
            sigmas = DoubleArray(FeatureType.COUNT) { 0.05 },
            covariance = Array(FeatureType.COUNT) { row ->
                DoubleArray(FeatureType.COUNT) { col -> if (row == col) variance else 0.0 }
            }
        )
    }
}