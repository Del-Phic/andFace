package dev.andface.galaxy.auth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FaceAuthenticationAlgorithmContractTest {
    @Test
    fun authenticationCombinesFuzzyPrimaryAndMahalanobisAuxiliaryWithRequiredWeights() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("const val FUZZY_WEIGHT = 0.72"))
        assertTrue(source.contains("const val MAHALANOBIS_WEIGHT = 0.28"))
        assertTrue(source.contains("FUZZY_WEIGHT * fuzzy.score + MAHALANOBIS_WEIGHT * mahalanobis.score"))
    }

    @Test
    fun authenticationScoresOnlyObservableEvidence() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("val observableEvidence = observableFrame.observableEvidence"))
        assertTrue(source.contains("fuzzyInferenceEngine.score(profile, observableEvidence)"))
        assertTrue(source.contains("mahalanobisEngine.score(profile, observableEvidence)"))
        assertTrue(source.contains("hasIdentityCoverage(observableEvidence, observableFrame.occlusionSummary)"))
    }

    @Test
    fun authenticationRequiresObservedIdentityConsistencyBeyondFamMaximum() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("val identityConsistencyScore = identityConsistencyScore("))
        assertTrue(source.contains("fuzzy.activations,"))
        assertTrue(source.contains("normalizedActivation = (activation / type.ruleWeight)"))
        assertTrue(source.contains("best.identityConsistencyScore < best.requiredIdentityConsistencyScore"))
        assertTrue(source.contains("FailureReason.LOW_IDENTITY_SUPPORT"))
    }
    @Test
    fun currentFrameIdentityFailureCannotBeConvertedToSuccessByTemporalGrace() {
        val engineSource = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")
        val gateSource = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/TemporalDecisionGate.kt")

        assertTrue(!engineSource.contains("canUseTemporalGrace"))
        assertTrue(!engineSource.contains("TEMPORAL_GRACE_RECOVERABLE_FAILURES"))
        assertTrue(engineSource.contains("immediateReason == FailureReason.NONE && !temporal.passed"))
        assertTrue(gateSource.contains("if (failureReason != FailureReason.NONE)"))
        assertTrue(gateSource.contains("reset()"))
    }
    @Test
    fun projectDoesNotIntroduceTemplateMatchingLanguageInCoreAuthenticationCode() {
        val files = listOf(
            "app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt",
            "app/src/main/java/dev/andface/galaxy/fuzzy/FuzzyInferenceEngine.kt",
            "app/src/main/java/dev/andface/galaxy/mahalanobis/MahalanobisEngine.kt"
        )
        val forbiddenTerms = listOf("templateMatch", "template matching", "faceTemplate", "occludedTemplate")
        val found = files.flatMap { path ->
            val source = readProjectFile(path)
            forbiddenTerms.filter { term -> source.contains(term, ignoreCase = true) }.map { term -> "$path:$term" }
        }

        assertTrue("Core authentication code contains template-matching terms: ${found.joinToString()}", found.isEmpty())
    }

    @Test
    fun authenticationRequiresRegionalInlierBalanceForCommercialMaskSafety() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("hasRegionalIdentityInlierBalance"))
        assertTrue(source.contains("requiredRegionalIdentityGroups"))
        assertTrue(source.contains("regionalInlierScore"))
        assertTrue(source.contains("!best.regionalInlierBalancePassed -> FailureReason.LOW_GLOBAL_CONSISTENCY"))
    }
    @Test
    fun authenticationRequiresMicroRegionAgreementForOccludedCommercialSafety() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("hasMicroRegionAgreement"))
        assertTrue(source.contains("LEFT_MICRO_REGION_TYPES"))
        assertTrue(source.contains("RIGHT_MICRO_REGION_TYPES"))
        assertTrue(source.contains("CENTER_MICRO_REGION_TYPES"))
        assertTrue(source.contains("!best.microRegionAgreementPassed -> FailureReason.LOW_GLOBAL_CONSISTENCY"))
    }

    @Test
    fun identityInlierChecksAreWeightedByObservedFeatureConfidence() {
        val source = readProjectFile("app/src/main/java/dev/andface/galaxy/auth/AuthenticationEngine.kt")

        assertTrue(source.contains("private fun confidenceByType"))
        assertTrue(source.contains("private fun identityEvidenceWeight"))
        assertTrue(source.contains("evidence.visibility.coerceIn(0.0, 1.0)"))
        assertTrue(source.contains("regionalInlierScore(rawFrame, profile, regionTypes, confidenceByType)"))
        assertTrue(source.contains("val weight = identityEvidenceWeight(type, confidenceByType)"))
    }
    private fun readProjectFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        )
        val file = candidates.firstOrNull { it.isFile }
        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText()
    }
}

