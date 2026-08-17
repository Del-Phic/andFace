package dev.andface.galaxy.fuzzy

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class FuzzyResult(
    val score: Double,
    val activations: List<Pair<String, Double>>,
    val famScore: Double = score,
    val aggregateScore: Double = score,
    val regionalAggregateScore: Double = aggregateScore,
    val supportScore: Double = score
)

class FuzzyInferenceEngine {
    fun score(profile: EnrollmentProfile, observableEvidence: List<FeatureEvidence>): FuzzyResult {
        val usableEvidence = observableEvidence.filter { evidence ->
            evidence.observable && evidence.visibility > 0.0
        }
        if (usableEvidence.isEmpty()) return FuzzyResult(score = 0.0, activations = emptyList())

        val activations = usableEvidence.map { evidence ->
            val center = profile.mean(evidence.type)
            val sigma = max(profile.sigma(evidence.type), evidence.type.minimumSigma)
            val membership = gaussianMembership(evidence.value, center, sigma)
            val visibleMembership = membership * evidence.visibility
            val activation = minOf(visibleMembership, evidence.type.ruleWeight)
            evidence.type.label to activation.coerceIn(0.0, 1.0)
        }
        val famScore = activations.maxOf { it.second }
        val aggregateScore = aggregateActivation(activations.map { it.second })
        val evidenceActivations = usableEvidence.zip(activations)
        val regionalAggregateScore = regionalAggregateActivation(evidenceActivations)
        val supportScore = supportActivation(evidenceActivations)
        val gatedScore = if (activations.size == 1) {
            famScore
        } else {
            (FAM_SCORE_WEIGHT * famScore +
                REGIONAL_AGGREGATE_SCORE_WEIGHT * regionalAggregateScore +
                SUPPORT_SCORE_WEIGHT * supportScore)
                .coerceAtMost(famScore)
        }

        return FuzzyResult(
            score = gatedScore.coerceIn(0.0, 1.0),
            activations = activations,
            famScore = famScore,
            aggregateScore = aggregateScore,
            regionalAggregateScore = regionalAggregateScore,
            supportScore = supportScore
        )
    }

    private fun aggregateActivation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedDescending()
        val topCount = min(MAX_AGGREGATED_RULES, max(MIN_AGGREGATED_RULES, sorted.size / 3 + 1))
            .coerceAtMost(sorted.size)
        return sorted.take(topCount).average().coerceIn(0.0, 1.0)
    }

    private fun regionalAggregateActivation(evidenceActivations: List<Pair<FeatureEvidence, Pair<String, Double>>>): Double {
        if (evidenceActivations.isEmpty()) return 0.0
        val regionScores = evidenceActivations
            .groupBy { (evidence, _) -> evidence.type.group }
            .values
            .map { regionItems -> aggregateActivation(regionItems.map { (_, activation) -> activation.second }) }
        return regionScores.average().coerceIn(0.0, 1.0)
    }

    private fun supportActivation(evidenceActivations: List<Pair<FeatureEvidence, Pair<String, Double>>>): Double {
        if (evidenceActivations.isEmpty()) return 0.0

        val normalizedActivations = evidenceActivations.map { (evidence, activation) ->
            if (evidence.type.ruleWeight <= 0.0) {
                0.0
            } else {
                (activation.second / evidence.type.ruleWeight).coerceIn(0.0, 1.0)
            }
        }
        val strongFeatureRatio = normalizedActivations
            .count { activation -> activation >= SUPPORT_ACTIVATION_THRESHOLD }
            .toDouble() / normalizedActivations.size.toDouble()
        val visibleGroups = evidenceActivations.map { (evidence, _) -> evidence.type.group }.toSet()
        val strongGroups = evidenceActivations
            .filterIndexed { index, _ -> normalizedActivations[index] >= SUPPORT_ACTIVATION_THRESHOLD }
            .map { (evidence, _) -> evidence.type.group }
            .toSet()
        val strongRegionRatio = if (visibleGroups.isEmpty()) {
            0.0
        } else {
            strongGroups.size.toDouble() / visibleGroups.size.toDouble()
        }
        val meanActivation = normalizedActivations.average()

        return (SUPPORT_FEATURE_BREADTH_WEIGHT * strongFeatureRatio +
            SUPPORT_REGION_BREADTH_WEIGHT * strongRegionRatio +
            SUPPORT_STRENGTH_WEIGHT * meanActivation)
            .coerceIn(0.0, 1.0)
    }

    private fun gaussianMembership(value: Double, center: Double, sigma: Double): Double {
        val exponent = -((value - center).pow(2.0)) / (2.0 * sigma.pow(2.0))
        return exp(exponent).coerceIn(0.0, 1.0)
    }

    companion object {
        private const val FAM_SCORE_WEIGHT = 0.35
        private const val REGIONAL_AGGREGATE_SCORE_WEIGHT = 0.45
        private const val SUPPORT_SCORE_WEIGHT = 0.20
        private const val SUPPORT_ACTIVATION_THRESHOLD = 0.55
        private const val SUPPORT_FEATURE_BREADTH_WEIGHT = 0.45
        private const val SUPPORT_REGION_BREADTH_WEIGHT = 0.35
        private const val SUPPORT_STRENGTH_WEIGHT = 0.20
        private const val MIN_AGGREGATED_RULES = 2
        private const val MAX_AGGREGATED_RULES = 6
    }
}