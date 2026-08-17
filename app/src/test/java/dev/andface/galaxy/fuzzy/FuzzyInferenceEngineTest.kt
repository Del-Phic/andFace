package dev.andface.galaxy.fuzzy

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyInferenceEngineTest {
    private val engine = FuzzyInferenceEngine()

    @Test
    fun centeredFeatureIsCappedByRuleWeightWithoutNormalization() {
        val profile = profileWithMeanAndSigma(
            type = FeatureType.EyeDistance,
            mean = 0.52,
            sigma = 0.05
        )
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    type = FeatureType.EyeDistance,
                    value = 0.52,
                    visibility = 1.0,
                    observable = true
                )
            )
        )

        assertEquals(FeatureType.EyeDistance.ruleWeight, result.score, 1e-12)
    }

    @Test
    fun visibilityScalesGaussianMembershipBeforeFamMaxMinComposition() {
        val profile = profileWithMeanAndSigma(
            type = FeatureType.EyeDistance,
            mean = 0.52,
            sigma = 0.05
        )
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    type = FeatureType.EyeDistance,
                    value = 0.52,
                    visibility = 0.40,
                    observable = true
                )
            )
        )

        assertEquals(0.40, result.score, 1e-12)
    }

    @Test
    fun famScoreKeepsMaximumActivationWhileFinalScoreUsesMultiFeatureGate() {
        val profile = baseProfile()
        profile.means[FeatureType.EyeDistance.ordinal] = 0.50
        profile.means[FeatureType.BrowDistance.ordinal] = 0.12
        profile.sigmas[FeatureType.EyeDistance.ordinal] = 0.05
        profile.sigmas[FeatureType.BrowDistance.ordinal] = 0.05

        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, value = 0.50, visibility = 0.50, observable = true),
                FeatureEvidence(FeatureType.BrowDistance, value = 0.12, visibility = 1.00, observable = true)
            )
        )

        assertEquals(FeatureType.BrowDistance.ruleWeight, result.famScore, 1e-12)
        assertTrue("final fuzzy score should be gated below single best activation", result.score < result.famScore)
        assertTrue("support should preserve broad enough evidence", result.supportScore > 0.70)
        assertTrue("final fuzzy score should still preserve strong multi-feature evidence", result.score > 0.70)
    }

    @Test
    fun supportGatePreventsOneStrongRuleFromDominatingManyWeakRules() {
        val profile = baseProfile()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.BrowDistance, value = 1.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.NoseWidth, value = 1.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.MouthWidth, value = 1.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.Yaw, value = 1.0, visibility = 1.0, observable = true)
            )
        )

        assertEquals(FeatureType.EyeDistance.ruleWeight, result.famScore, 1e-12)
        assertTrue("support should be low when only one feature supports identity: $result", result.supportScore < 0.30)
        assertTrue("final fuzzy score should be far below the raw FAM max: $result", result.score < 0.50)
    }

    @Test
    fun supportGatePreservesBroadCrossRegionAgreement() {
        val profile = baseProfile()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.BrowDistance, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.NoseWidth, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.EyeNoseLeft, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.MouthWidth, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.FaceAspect, value = 0.0, visibility = 1.0, observable = true)
            )
        )

        assertEquals(1.0, result.supportScore, 1e-12)
        assertTrue("regional aggregate should stay high when several visible regions agree: $result", result.regionalAggregateScore > 0.75)
        assertTrue("broad support should keep final fuzzy score high: $result", result.score > 0.85)
    }

    @Test
    fun emptyObservableEvidenceScoresZero() {
        val result = engine.score(baseProfile(), emptyList())

        assertEquals(0.0, result.score, 1e-12)
        assertEquals(emptyList<Pair<String, Double>>(), result.activations)
    }

    @Test
    fun evidenceMarkedNotObservableIsIgnoredInsideEngine() {
        val profile = baseProfile()
        profile.means[FeatureType.EyeDistance.ordinal] = 0.50
        profile.means[FeatureType.BrowDistance.ordinal] = 0.12
        profile.sigmas[FeatureType.EyeDistance.ordinal] = 0.05
        profile.sigmas[FeatureType.BrowDistance.ordinal] = 0.05

        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.BrowDistance, value = 0.12, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.EyeDistance, value = 0.50, visibility = 1.0, observable = false)
            )
        )

        assertEquals(FeatureType.BrowDistance.ruleWeight, result.score, 1e-12)
        assertEquals(listOf(FeatureType.BrowDistance.label), result.activations.map { it.first })
    }

    @Test
    fun zeroVisibilityEvidenceIsIgnoredInsideEngine() {
        val profile = profileWithMeanAndSigma(
            type = FeatureType.EyeDistance,
            mean = 0.52,
            sigma = 0.05
        )
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(
                    type = FeatureType.EyeDistance,
                    value = 0.52,
                    visibility = 0.0,
                    observable = true
                )
            )
        )

        assertEquals(0.0, result.score, 1e-12)
        assertEquals(emptyList<Pair<String, Double>>(), result.activations)
    }

    @Test
    fun regionalAggregatePenalizesSingleRegionDominance() {
        val profile = baseProfile()
        val result = engine.score(
            profile,
            listOf(
                FeatureEvidence(FeatureType.EyeDistance, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.BrowDistance, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.UpperFaceWidth, value = 0.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.NoseWidth, value = 1.0, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.MouthWidth, value = 1.0, visibility = 1.0, observable = true)
            )
        )

        val previousTopOnlyBlend = (0.65 * result.famScore + 0.35 * result.aggregateScore)
            .coerceAtMost(result.famScore)
        assertTrue(
            "regional score should be lower than top-feature aggregate when support is concentrated: $result",
            result.regionalAggregateScore < result.aggregateScore
        )
        assertTrue(
            "regional gate should lower the final fuzzy score compared with the previous top-only blend: $result",
            result.score < previousTopOnlyBlend
        )
        assertTrue("support should also penalize concentrated evidence: $result", result.supportScore < 0.60)
    }

    private fun profileWithMeanAndSigma(type: FeatureType, mean: Double, sigma: Double): EnrollmentProfile {
        return baseProfile().also { profile ->
            profile.means[type.ordinal] = mean
            profile.sigmas[type.ordinal] = sigma
        }
    }

    private fun baseProfile(): EnrollmentProfile {
        return EnrollmentProfile(
            userId = "USER_1",
            createdAtMs = 0L,
            modelSha256 = "test",
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = 45,
            means = DoubleArray(FeatureType.COUNT) { 0.0 },
            sigmas = DoubleArray(FeatureType.COUNT) { 0.05 },
            covariance = Array(FeatureType.COUNT) { row ->
                DoubleArray(FeatureType.COUNT) { col ->
                    if (row == col) 0.05 * 0.05 else 0.0
                }
            }
        )
    }
}