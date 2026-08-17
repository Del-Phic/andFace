package dev.andface.galaxy.mahalanobis

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MahalanobisEngineTest {
    private val engine = MahalanobisEngine()

    @Test
    fun exactObservableSubsetMatchScoresOne() {
        val profile = profileWithIdentityCovariance()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, profile.mean(FeatureType.EyeDistance), 1.0, true),
                FeatureEvidence(FeatureType.NoseWidth, profile.mean(FeatureType.NoseWidth), 1.0, true)
            )
        )

        assertEquals(1.0, result.score, 1e-12)
        assertEquals(0.0, result.distance, 1e-12)
        assertFalse(result.usedDiagonalFallback)
    }

    @Test
    fun scoreUsesOnlyProvidedObservableFeatureSubset() {
        val profile = profileWithIdentityCovariance().also { profile ->
            profile.means[FeatureType.MouthWidth.ordinal] = 100.0
            profile.means[FeatureType.JawWidth.ordinal] = -100.0
        }
        val subset = listOf(
            FeatureEvidence(FeatureType.EyeDistance, profile.mean(FeatureType.EyeDistance), 1.0, true),
            FeatureEvidence(FeatureType.NoseWidth, profile.mean(FeatureType.NoseWidth), 1.0, true)
        )

        val result = engine.score(profile, subset)

        assertEquals(1.0, result.score, 1e-12)
        assertEquals(0.0, result.distance, 1e-12)
    }

    @Test
    fun emptyObservableSubsetFailsClosed() {
        val result = engine.score(profileWithIdentityCovariance(), emptyList())

        assertEquals(0.0, result.score, 1e-12)
        assertTrue(result.distance.isInfinite())
        assertTrue(result.usedDiagonalFallback)
    }

    @Test
    fun farObservableFeatureLowersConsistencyScore() {
        val profile = profileWithIdentityCovariance()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    FeatureType.EyeDistance,
                    value = profile.mean(FeatureType.EyeDistance) + 0.50,
                    visibility = 1.0,
                    observable = true
                )
            )
        )

        assertTrue("expected score below 1.0 but was ${result.score}", result.score < 1.0)
        assertTrue("expected positive distance but was ${result.distance}", result.distance > 0.0)
    }

    @Test
    fun evidenceMarkedNotObservableIsIgnoredInsideEngine() {
        val profile = profileWithIdentityCovariance()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, profile.mean(FeatureType.EyeDistance), 1.0, true),
                FeatureEvidence(FeatureType.MouthWidth, profile.mean(FeatureType.MouthWidth) + 100.0, 1.0, false)
            )
        )

        assertEquals(1.0, result.score, 1e-12)
        assertEquals(0.0, result.distance, 1e-12)
    }

    @Test
    fun lowVisibilityFeatureContributesLessToEffectiveDistance() {
        val profile = profileWithIdentityCovariance()
        val fullVisibility = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    FeatureType.NoseWidth,
                    value = profile.mean(FeatureType.NoseWidth) + 0.10,
                    visibility = 1.0,
                    observable = true
                )
            )
        )
        val lowVisibility = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    FeatureType.NoseWidth,
                    value = profile.mean(FeatureType.NoseWidth) + 0.10,
                    visibility = 0.35,
                    observable = true
                )
            )
        )

        assertTrue(
            "expected low visibility distance ${lowVisibility.distance} below full visibility ${fullVisibility.distance}",
            lowVisibility.distance < fullVisibility.distance
        )
        assertTrue(
            "expected low visibility score ${lowVisibility.score} above full visibility ${fullVisibility.score}",
            lowVisibility.score > fullVisibility.score
        )
    }
    private fun profileWithIdentityCovariance(): EnrollmentProfile {
        val means = DoubleArray(FeatureType.COUNT) { index -> 0.1 + index * 0.01 }
        val variance = 0.05 * 0.05
        return EnrollmentProfile(
            userId = "USER_1",
            createdAtMs = 0L,
            modelSha256 = "test",
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = 45,
            means = means,
            sigmas = DoubleArray(FeatureType.COUNT) { 0.05 },
            covariance = Array(FeatureType.COUNT) { row ->
                DoubleArray(FeatureType.COUNT) { col ->
                    if (row == col) variance else 0.0
                }
            }
        )
    }
}
