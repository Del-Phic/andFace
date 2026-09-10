package dev.andface.galaxy.auth

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.enrollment.EnrollmentSecurityPolicy
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureGroup
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.fuzzy.FuzzyInferenceEngine
import dev.andface.galaxy.mahalanobis.MahalanobisEngine
import dev.andface.galaxy.occlusion.OcclusionAnalyzer
import dev.andface.galaxy.occlusion.OcclusionHint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class AuthenticationEngine(
    private val occlusionAnalyzer: OcclusionAnalyzer = OcclusionAnalyzer(),
    private val fuzzyInferenceEngine: FuzzyInferenceEngine = FuzzyInferenceEngine(),
    private val mahalanobisEngine: MahalanobisEngine = MahalanobisEngine(),
    private val livenessTracker: LivenessTracker = LivenessTracker(),
    private val temporalDecisionGate: TemporalDecisionGate = TemporalDecisionGate(),
    private val featureFrameStabilizer: FeatureFrameStabilizer = FeatureFrameStabilizer(),
    private val occlusionStateStabilizer: OcclusionStateStabilizer = OcclusionStateStabilizer()
) {
    @Volatile
    var profiles: List<EnrollmentProfile> = emptyList()
        private set
    private var lastHint: OcclusionHint? = null

    @Synchronized
    fun setProfiles(profiles: List<EnrollmentProfile>) {
        this.profiles = profiles.sortedBy { it.userId }
        resetLiveSession()
    }

    @Synchronized
    fun resetLiveSession() {
        livenessTracker.reset()
        temporalDecisionGate.reset()
        featureFrameStabilizer.reset()
        occlusionStateStabilizer.reset()
        lastHint = null
    }

    @Synchronized
    fun authenticate(rawFrame: RawFeatureFrame, hint: OcclusionHint): AuthResult {
        if (profiles.isEmpty()) return AuthResult.failed(FailureReason.NO_ENROLLMENT).withFrameQuality(rawFrame)
        invalidFeatureVectorReason(rawFrame)?.let { reason ->
            resetLiveSession()
            return AuthResult.failed(reason).copy(registeredUserCount = profiles.size).withFrameQuality(rawFrame)
        }
        if (!rawFrame.quality.acceptableForEnrollmentCapture) {
            resetLiveSession()
            return AuthResult.failed(FailureReason.POOR_FACE_QUALITY).copy(registeredUserCount = profiles.size).withFrameQuality(rawFrame)
        }

        if (lastHint != null && lastHint != hint) {
            livenessTracker.reset()
            featureFrameStabilizer.reset()
            temporalDecisionGate.reset()
            occlusionStateStabilizer.reset()
        }
        lastHint = hint

        val scoringFrame = featureFrameStabilizer.add(rawFrame)
        val geometryFailure = validateCaptureGeometry(scoringFrame, hint)
        if (geometryFailure != FailureReason.NONE) {
            resetLiveSession()
            return AuthResult.failed(geometryFailure).copy(registeredUserCount = profiles.size).withFrameQuality(scoringFrame)
        }
        livenessTracker.add(rawFrame, hint)
        val resolvedHint = resolveOcclusionHint(scoringFrame, hint)
        val operatorConfirmedOcclusion = !hint.isClean
        val candidates = profiles.map { profile ->
            // Automatic occlusion is resolved once per frame and must be temporally stable
            // before it is allowed to remove identity evidence. Explicit UI hints take effect
            // immediately and remain authoritative.
            val hintFailure = FailureReason.NONE
            val observableFrame = occlusionAnalyzer.analyzeResolved(scoringFrame, profile, resolvedHint)
            val requiredObservableCount = minimumObservableCount(observableFrame.occlusionSummary)
            val requiredEffectiveObservableCount = minimumEffectiveObservableCount(observableFrame.occlusionSummary)
            val requiredCoverage = minimumCoverage(observableFrame.occlusionSummary)
            val requiredSupportCount = requiredSupportCount(observableFrame.occlusionSummary)
            val observableEvidence = observableFrame.observableEvidence
            val effectiveObservableCount = observableFrame.effectiveObservableCount
            if (hintFailure != FailureReason.NONE) {
                Candidate(
                    profile = profile,
                    fuzzyScore = 0.0,
                    mahalanobisScore = 0.0,
                    finalScore = 0.0,
                    coverage = observableFrame.coverage,
                    observableCount = observableFrame.observableCount,
                    occlusionSummary = observableFrame.occlusionSummary,
                    requiredObservableCount = requiredObservableCount,
                    requiredCoverage = requiredCoverage,
                    identityCoveragePassed = false,
                    identityConsistencyScore = 0.0,
                    requiredIdentityConsistencyScore = requiredIdentityConsistencyScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identityOutlierScore = 0.0,
                    requiredIdentityOutlierScore = requiredIdentityOutlierScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identitySupportCount = 0,
                    supportDistributionPassed = false,
                    regionalInlierBalancePassed = false,
                    localStructureAgreementPassed = false,
                    microRegionAgreementPassed = false,
                    requiredSupportCount = requiredSupportCount,
                    requiredMahalanobisScore = requiredMahalanobisScore(
                        profile,
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    rejectionReason = hintFailure,
                    valid = false
                )
            } else if (
                observableFrame.observableCount < requiredObservableCount ||
                effectiveObservableCount < requiredEffectiveObservableCount ||
                observableFrame.coverage < requiredCoverage
            ) {
                Candidate(
                    profile = profile,
                    fuzzyScore = 0.0,
                    mahalanobisScore = 0.0,
                    finalScore = 0.0,
                    coverage = observableFrame.coverage,
                    observableCount = observableFrame.observableCount,
                    occlusionSummary = observableFrame.occlusionSummary,
                    requiredObservableCount = requiredObservableCount,
                    requiredCoverage = requiredCoverage,
                    identityCoveragePassed = false,
                    identityConsistencyScore = 0.0,
                    requiredIdentityConsistencyScore = requiredIdentityConsistencyScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identityOutlierScore = 0.0,
                    requiredIdentityOutlierScore = requiredIdentityOutlierScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identitySupportCount = 0,
                    supportDistributionPassed = false,
                    regionalInlierBalancePassed = false,
                    localStructureAgreementPassed = false,
                    microRegionAgreementPassed = false,
                    requiredSupportCount = requiredSupportCount,
                    requiredMahalanobisScore = requiredMahalanobisScore(
                        profile,
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    rejectionReason = if (
                        observableFrame.observableCount < requiredObservableCount ||
                        effectiveObservableCount < requiredEffectiveObservableCount
                    ) {
                        FailureReason.TOO_FEW_FEATURES
                    } else {
                        FailureReason.LOW_COVERAGE
                    },
                    valid = false
                )
            } else {
                val fuzzy = fuzzyInferenceEngine.score(profile, observableEvidence)
                val mahalanobis = mahalanobisEngine.score(profile, observableEvidence)
                val finalScore = (FUZZY_WEIGHT * fuzzy.score + MAHALANOBIS_WEIGHT * mahalanobis.score)
                    .coerceIn(0.0, 1.0)
                val identityConsistencyScore = identityConsistencyScore(
                    fuzzy.activations,
                    observableFrame.occlusionSummary
                )
                val identityOutlierScore = identityOutlierScore(
                    scoringFrame,
                    profile,
                    observableEvidence,
                    observableFrame.occlusionSummary
                )
                val microRegion = hasMicroRegionAgreement(
                    scoringFrame,
                    profile,
                    observableEvidence,
                    observableFrame.occlusionSummary,
                    operatorConfirmedOcclusion
                )
                Candidate(
                    profile = profile,
                    fuzzyScore = fuzzy.score,
                    mahalanobisScore = mahalanobis.score,
                    finalScore = finalScore,
                    coverage = observableFrame.coverage,
                    observableCount = observableFrame.observableCount,
                    occlusionSummary = observableFrame.occlusionSummary,
                    requiredObservableCount = requiredObservableCount,
                    requiredCoverage = requiredCoverage,
                    identityCoveragePassed = hasIdentityCoverage(observableEvidence, observableFrame.occlusionSummary),
                    identityConsistencyScore = identityConsistencyScore,
                    requiredIdentityConsistencyScore = requiredIdentityConsistencyScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identityOutlierScore = identityOutlierScore,
                    requiredIdentityOutlierScore = requiredIdentityOutlierScore(
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    identitySupportCount = fuzzy.activations.count { it.second >= FUZZY_SUPPORT_THRESHOLD },
                    supportDistributionPassed = hasSupportDistribution(fuzzy.activations, observableFrame.occlusionSummary),
                    regionalInlierBalancePassed = hasRegionalIdentityInlierBalance(
                        scoringFrame,
                        profile,
                        observableEvidence,
                        observableFrame.occlusionSummary
                    ),
                    localStructureAgreementPassed = hasLocalStructureAgreement(
                        scoringFrame,
                        profile,
                        observableEvidence,
                        observableFrame.occlusionSummary
                    ),
                    microRegionAgreementPassed = microRegion.passed,
                    microRegionDiagnostics = microRegion.diagnostics,
                    requiredSupportCount = requiredSupportCount,
                    requiredMahalanobisScore = requiredMahalanobisScore(
                        profile,
                        observableFrame.occlusionSummary,
                        operatorConfirmedOcclusion
                    ),
                    rejectionReason = FailureReason.NONE,
                    valid = true
                )
            }
        }

        val validCandidates = candidates.filter { it.valid }
        if (validCandidates.isEmpty()) {
            temporalDecisionGate.reset()
            occlusionStateStabilizer.reset()
            val strongestRejected = candidates.maxWithOrNull(
                compareBy<Candidate> { it.observableCount }.thenBy { it.coverage }
            )
            val strongestOcclusionSummary = strongestRejected?.occlusionSummary ?: "clean"
            val liveness = livenessTracker.evaluate(effectiveLivenessHint(hint, strongestOcclusionSummary))
            val reason = if (strongestRejected?.occlusionSummary?.let(::isExcessiveOcclusion) == true) {
                FailureReason.EXCESSIVE_OCCLUSION
            } else if ((strongestRejected?.observableCount ?: 0) < (strongestRejected?.requiredObservableCount ?: MIN_OBSERVABLE_FEATURES)) {
                FailureReason.TOO_FEW_FEATURES
            } else {
                strongestRejected?.rejectionReason?.takeIf { it != FailureReason.NONE } ?: FailureReason.LOW_COVERAGE
            }
            return buildFailure(
                    reason = reason,
                    registeredUserCount = profiles.size,
                    matchedUserId = strongestRejected?.profile?.userId,
                    observableCount = strongestRejected?.observableCount ?: 0,
                    coverage = strongestRejected?.coverage ?: 0.0,
                    liveness = liveness,
                    occlusionSummary = strongestOcclusionSummary
                ).withFrameQuality(scoringFrame)
        }

        val sortedCandidates = validCandidates.sortedByDescending { it.finalScore }
        val best = sortedCandidates.first()
        val second = sortedCandidates.drop(1).firstOrNull()
        val resultOcclusionSummary = best.occlusionSummary
        val secondBestScore = second?.finalScore ?: 0.0
        val margin = best.finalScore - secondBestScore
        val requiredMargin = requiredMargin(best.occlusionSummary)
        val requiredFinalScore = requiredFinalScore(
            best.profile,
            best.occlusionSummary,
            operatorConfirmedOcclusion
        )
        val liveness = livenessTracker.evaluate(effectiveLivenessHint(hint, resultOcclusionSummary))

        val immediateReason = when {
            !liveness.passed -> FailureReason.LOW_LIVENESS
            isExcessiveOcclusion(best.occlusionSummary) -> FailureReason.EXCESSIVE_OCCLUSION
            !best.identityCoveragePassed -> FailureReason.LOW_IDENTITY_COVERAGE
            best.identityOutlierScore < best.requiredIdentityOutlierScore -> FailureReason.LOW_GLOBAL_CONSISTENCY
            !best.regionalInlierBalancePassed -> FailureReason.LOW_GLOBAL_CONSISTENCY
            !best.localStructureAgreementPassed -> FailureReason.LOW_GLOBAL_CONSISTENCY
            !best.microRegionAgreementPassed -> FailureReason.LOW_GLOBAL_CONSISTENCY
            best.identityConsistencyScore < best.requiredIdentityConsistencyScore -> FailureReason.LOW_IDENTITY_SUPPORT
            best.identitySupportCount < best.requiredSupportCount -> FailureReason.LOW_IDENTITY_SUPPORT
            !best.supportDistributionPassed -> FailureReason.LOW_IDENTITY_SUPPORT
            best.mahalanobisScore < best.requiredMahalanobisScore -> FailureReason.LOW_GLOBAL_CONSISTENCY
            second != null && margin < requiredMargin -> FailureReason.LOW_MARGIN
            best.finalScore < requiredFinalScore -> FailureReason.LOW_SCORE
            else -> FailureReason.NONE
        }
        // Automatic occlusion inference may alternate between clean/lower/glasses on
        // ordinary blinks and expressions. It is not allowed to relax thresholds, and
        // it must not reset an otherwise continuous same-identity decision sequence.
        val temporalOcclusionSummary = if (operatorConfirmedOcclusion) {
            resultOcclusionSummary
        } else {
            "clean"
        }
        val temporal = temporalDecisionGate.evaluate(
            userId = best.profile.userId,
            occlusionSummary = temporalOcclusionSummary,
            failureReason = immediateReason
        )
        val reason = if (immediateReason == FailureReason.NONE && !temporal.passed) {
            FailureReason.UNSTABLE_DECISION
        } else {
            immediateReason
        }

        return AuthResult(
                decision = if (reason == FailureReason.NONE) AuthDecision.SUCCESS else AuthDecision.FAILED,
                failureReason = reason,
                matchedUserId = best.profile.userId,
                secondBestUserId = second?.profile?.userId,
                registeredUserCount = profiles.size,
                fuzzyScore = best.fuzzyScore,
                mahalanobisScore = best.mahalanobisScore,
                finalScore = best.finalScore,
                coverage = best.coverage,
                margin = margin,
                livenessScore = liveness.score,
                livenessPassed = liveness.passed,
                livenessFrameCount = liveness.frameCount,
                livenessChallenge = liveness.challengeLabel,
                livenessChallengePassed = liveness.challengePassed,
                livenessPassivePassed = liveness.passivePassed,
                observableCount = best.observableCount,
                identityConsistencyScore = best.identityConsistencyScore,
                requiredIdentityConsistencyScore = best.requiredIdentityConsistencyScore,
                identityOutlierScore = best.identityOutlierScore,
                requiredIdentityOutlierScore = best.requiredIdentityOutlierScore,
                identitySupportCount = best.identitySupportCount,
                requiredSupportCount = best.requiredSupportCount,
                stableFrameCount = temporal.stableFrames,
                requiredStableFrames = temporal.requiredFrames,
                occlusionSummary = resultOcclusionSummary,
                faceQualityScore = scoringFrame.quality.accessScoringConfidence,
                meshSymmetryScore = scoringFrame.quality.meshSymmetryScore,
                landmarkTopologyScore = scoringFrame.quality.landmarkTopologyScore,
                requiredMahalanobisScore = best.requiredMahalanobisScore,
                requiredFinalScore = requiredFinalScore,
                regionalInlierBalancePassed = best.regionalInlierBalancePassed,
                localStructureAgreementPassed = best.localStructureAgreementPassed,
                microRegionAgreementPassed = best.microRegionAgreementPassed,
                microRegionDiagnostics = best.microRegionDiagnostics
            )
    }

    @Synchronized
    fun checkEnrollmentSample(rawFrame: RawFeatureFrame, hint: OcclusionHint): FailureReason {
        invalidFeatureVectorReason(rawFrame)?.let { return it }
        if (!rawFrame.quality.acceptableForEnrollmentCapture) return FailureReason.POOR_FACE_QUALITY
        if (!hint.isClean) return FailureReason.OCCLUDED_DURING_ENROLLMENT

        val geometryFailure = validateCaptureGeometry(rawFrame, hint)
        if (geometryFailure != FailureReason.NONE) return geometryFailure

        return FailureReason.NONE
    }

    @Synchronized
    fun enrollmentSampleDiagnostic(rawFrame: RawFeatureFrame, hint: OcclusionHint): String {
        invalidFeatureVectorReason(rawFrame)?.let { return "vector=$it" }
        val geometryFailure = FeatureGeometryQualityPolicy.validateForCleanEnrollment(rawFrame)
        val cleanFailure = CleanEnrollmentValidator.validateSample(rawFrame, hint)
        val observableFrame = occlusionAnalyzer.analyze(rawFrame, profile = null, hint = hint)
        val requiredFeatureCount = cleanEnrollmentFeatureCount(rawFrame)
        val requiredEffectiveFeatureCount = cleanEnrollmentEffectiveFeatureCount(rawFrame)
        val requiredCoverage = cleanEnrollmentCoverage(rawFrame)
        val missing = observableFrame.evidence
            .asSequence()
            .filterNot { it.observable }
            .take(3)
            .joinToString(",") { item -> "${item.type.name}:${item.reason}" }
            .ifBlank { "none" }

        return String.format(
            Locale.US,
            "q=%s geom=%s clean=%s obs=%d/%d eff=%.1f/%.1f cov=%.2f/%.2f occ=%s miss=%s",
            rawFrame.quality.acceptableForEnrollmentCapture,
            geometryFailure,
            cleanFailure,
            observableFrame.observableCount,
            requiredFeatureCount,
            observableFrame.effectiveObservableCount,
            requiredEffectiveFeatureCount,
            observableFrame.coverage,
            requiredCoverage,
            observableFrame.occlusionSummary.ifBlank { "clean" },
            missing
        )
    }

    @Synchronized
    fun checkEnrollmentBaseline(samples: List<RawFeatureFrame>): FailureReason {
        samples.forEach { frame ->
            invalidFeatureVectorReason(frame)?.let { return it }
        }
        return CleanEnrollmentValidator.validateBaseline(
            samples,
            EnrollmentSecurityPolicy.REQUIRED_CLEAN_SAMPLE_COUNT
        )
    }

    @Synchronized
    fun checkEnrollmentSeparation(
        candidate: EnrollmentProfile,
        existingProfiles: List<EnrollmentProfile> = profiles
    ): FailureReason {
        val otherProfiles = existingProfiles.filter { it.userId != candidate.userId }
        if (otherProfiles.isEmpty()) return FailureReason.NONE

        val ambiguousUnderSupportedAccessMode = otherProfiles.any { existing ->
            ENROLLMENT_SEPARATION_SCENARIOS.any { scenario ->
                profileSimilarity(candidate, existing, scenario.types) >= scenario.maxSimilarity
            }
        }

        return if (ambiguousUnderSupportedAccessMode) {
            FailureReason.LOW_MARGIN
        } else {
            FailureReason.NONE
        }
    }

    private fun invalidFeatureVectorReason(rawFrame: RawFeatureFrame): FailureReason? {
        if (rawFrame.values.size < FeatureType.COUNT) return FailureReason.TOO_FEW_FEATURES
        if (rawFrame.values.size != FeatureType.COUNT) return FailureReason.POOR_FACE_QUALITY
        return if (rawFrame.values.any { !it.isFinite() }) FailureReason.POOR_FACE_QUALITY else null
    }

    private fun validateCaptureGeometry(rawFrame: RawFeatureFrame, hint: OcclusionHint): FailureReason {
        val frontal = listOf(FeatureType.Yaw, FeatureType.Pitch, FeatureType.Roll).all { type ->
            kotlin.math.abs(rawFrame.value(type)) <= MAX_ENROLLMENT_CAPTURE_POSE
        }
        if (!frontal) return FailureReason.LOW_COVERAGE

        val alwaysVisibleCorePlausible =
            rawFrame.value(FeatureType.UpperFaceWidth) in 0.22..1.60 &&
                (hint.lowerFaceCovered ||
                    (rawFrame.value(FeatureType.NoseWidth) in 0.03..0.60 &&
                        rawFrame.value(FeatureType.CheekboneWidth) in 0.22..1.60))
        if (!alwaysVisibleCorePlausible) return FailureReason.POOR_FACE_QUALITY

        val eyeCorePlausible = hint.leftEyePatch || hint.rightEyePatch ||
            (rawFrame.value(FeatureType.EyeDistance) in 0.18..1.05 &&
                rawFrame.value(FeatureType.InnerEyeDistance) in 0.02..0.70)
        if (!eyeCorePlausible) return FailureReason.POOR_FACE_QUALITY

        val lowerFaceCorePlausible = hint.lowerFaceCovered ||
            (rawFrame.value(FeatureType.FaceAspect) in 0.55..2.30 &&
                rawFrame.value(FeatureType.JawWidth) in 0.70..5.50)
        if (!lowerFaceCorePlausible) return FailureReason.POOR_FACE_QUALITY

        return FailureReason.NONE
    }

    private fun cleanEnrollmentFeatureCount(rawFrame: RawFeatureFrame): Int {
        return if (rawFrame.quality.irisLandmarksAvailable) {
            FeatureCountForCleanEnrollment
        } else {
            FeatureCountForCleanEnrollmentWithoutOptionalIris
        }
    }

    private fun cleanEnrollmentCoverage(rawFrame: RawFeatureFrame): Double {
        return if (rawFrame.quality.irisLandmarksAvailable) {
            CLEAN_ENROLLMENT_COVERAGE
        } else {
            CLEAN_ENROLLMENT_COVERAGE_WITHOUT_OPTIONAL_IRIS
        }
    }

    private fun cleanEnrollmentEffectiveFeatureCount(rawFrame: RawFeatureFrame): Double {
        return if (rawFrame.quality.irisLandmarksAvailable) {
            MIN_CLEAN_ENROLLMENT_EFFECTIVE_FEATURES
        } else {
            MIN_CLEAN_ENROLLMENT_EFFECTIVE_FEATURES_WITHOUT_OPTIONAL_IRIS
        }
    }


    private fun profileSimilarity(
        first: EnrollmentProfile,
        second: EnrollmentProfile,
        types: Set<FeatureType>
    ): Double {
        var weightedSimilarity = 0.0
        var totalWeight = 0.0

        for (type in types) {
            val sigma = maxOf(first.sigma(type), second.sigma(type), type.minimumSigma)
            val zScore = abs(first.mean(type) - second.mean(type)) / sigma
            val featureSimilarity = exp(-0.5 * zScore * zScore)
            weightedSimilarity += featureSimilarity * type.ruleWeight
            totalWeight += type.ruleWeight
        }

        return if (totalWeight > 0.0) {
            weightedSimilarity / totalWeight
        } else {
            0.0
        }
    }


    private fun AuthResult.withFrameQuality(rawFrame: RawFeatureFrame): AuthResult {
        return copy(
            faceQualityScore = rawFrame.quality.accessScoringConfidence,
            meshSymmetryScore = rawFrame.quality.meshSymmetryScore,
            landmarkTopologyScore = rawFrame.quality.landmarkTopologyScore
        )
    }

    private fun buildFailure(
        reason: FailureReason,
        registeredUserCount: Int = profiles.size,
        matchedUserId: String? = null,
        observableCount: Int,
        coverage: Double,
        liveness: LivenessResult,
        occlusionSummary: String
    ): AuthResult {
        return AuthResult(
            decision = AuthDecision.FAILED,
            failureReason = reason,
            matchedUserId = matchedUserId,
            secondBestUserId = null,
            registeredUserCount = registeredUserCount,
            fuzzyScore = 0.0,
            mahalanobisScore = 0.0,
            finalScore = 0.0,
            coverage = coverage,
            margin = 0.0,
            livenessScore = liveness.score,
            livenessPassed = liveness.passed,
            livenessFrameCount = liveness.frameCount,
            livenessChallenge = liveness.challengeLabel,
            livenessChallengePassed = liveness.challengePassed,
            livenessPassivePassed = liveness.passivePassed,
            observableCount = observableCount,
            identityConsistencyScore = 0.0,
            requiredIdentityConsistencyScore = 0.0,
            identitySupportCount = 0,
            requiredSupportCount = 0,
            stableFrameCount = 0,
            requiredStableFrames = 0,
            occlusionSummary = occlusionSummary
        )
    }

    private data class EnrollmentSeparationScenario(
        val name: String,
        val types: Set<FeatureType>,
        val maxSimilarity: Double
    )
    private data class Candidate(
        val profile: EnrollmentProfile,
        val fuzzyScore: Double,
        val mahalanobisScore: Double,
        val finalScore: Double,
        val coverage: Double,
        val observableCount: Int,
        val occlusionSummary: String,
        val requiredObservableCount: Int,
        val requiredCoverage: Double,
        val identityCoveragePassed: Boolean,
        val identityConsistencyScore: Double,
        val requiredIdentityConsistencyScore: Double,
        val identityOutlierScore: Double,
        val requiredIdentityOutlierScore: Double,
        val identitySupportCount: Int,
        val supportDistributionPassed: Boolean,
        val regionalInlierBalancePassed: Boolean,
        val localStructureAgreementPassed: Boolean,
        val microRegionAgreementPassed: Boolean,
        val microRegionDiagnostics: String = "",
        val requiredSupportCount: Int,
        val requiredMahalanobisScore: Double,
        val rejectionReason: FailureReason,
        val valid: Boolean
    )

    private fun minimumObservableCount(occlusionSummary: String): Int {
        return if (occlusionSummary == "clean") {
            MIN_OBSERVABLE_FEATURES
        } else {
            MIN_OCCLUDED_OBSERVABLE_FEATURES
        }
    }

    private fun minimumEffectiveObservableCount(occlusionSummary: String): Double {
        return if (occlusionSummary == "clean") {
            MIN_EFFECTIVE_OBSERVABLE_FEATURES
        } else {
            MIN_OCCLUDED_EFFECTIVE_OBSERVABLE_FEATURES
        }
    }

    private fun minimumCoverage(occlusionSummary: String): Double {
        // With masked contour/pose excluded and glasses confidence applied,
        // the maximum available coverage is about 0.499. Counts and regional
        // identity checks still require the remaining evidence to agree.
        if (occlusionSummary.contains("lower") && occlusionSummary.contains("glasses")) return 0.49
        return if (occlusionSummary == "clean") {
            MIN_COVERAGE
        } else {
            MIN_OCCLUDED_COVERAGE
        }
    }

    private fun requiredFinalScore(
        profile: EnrollmentProfile,
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean
    ): Double {
        val effectiveSummary = if (operatorConfirmedOcclusion) occlusionSummary else "clean"
        val cohortThreshold = when {
            effectiveSummary == "clean" -> MIN_FINAL_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_REDUCED_IDENTITY_FINAL_SCORE
            else -> MIN_OCCLUDED_FINAL_SCORE
        }
        val enrolledGenuineThreshold = when {
            effectiveSummary == "clean" -> profile.calibratedCleanFinalScoreFloor
            isReducedIdentityOcclusion(effectiveSummary) ->
                profile.calibratedCleanFinalScoreFloor - REDUCED_IDENTITY_FINAL_SCORE_RELAXATION
            else -> profile.calibratedCleanFinalScoreFloor - OCCLUDED_FINAL_SCORE_RELAXATION
        }
        return maxOf(cohortThreshold, enrolledGenuineThreshold)
    }

    private fun requiredMargin(occlusionSummary: String): Double {
        return when {
            occlusionSummary == "clean" -> MIN_MARGIN
            isReducedIdentityOcclusion(occlusionSummary) -> MIN_REDUCED_IDENTITY_MARGIN
            else -> MIN_OCCLUDED_MARGIN
        }
    }

    private fun isReducedIdentityOcclusion(occlusionSummary: String): Boolean {
        return occlusionSummary.contains("mid") ||
            (occlusionSummary.contains("lower") && occlusionSummary.contains("_eye"))
    }

    private fun isGlassesOcclusion(occlusionSummary: String): Boolean {
        return occlusionSummary.contains("glasses")
    }

    private fun isExcessiveOcclusion(occlusionSummary: String): Boolean {
        val lowerFaceCovered = occlusionSummary.contains("lower")
        val midFaceCovered = occlusionSummary.contains("mid")
        val leftEyeCovered = occlusionSummary.contains("left_eye")
        val rightEyeCovered = occlusionSummary.contains("right_eye")
        val anyEyeCovered = leftEyeCovered || rightEyeCovered
        val bothEyesCovered = leftEyeCovered && rightEyeCovered

        return bothEyesCovered || ((lowerFaceCovered || midFaceCovered) && anyEyeCovered)
    }

    private fun effectiveLivenessHint(hint: OcclusionHint, occlusionSummary: String): OcclusionHint {
        return OcclusionHint(
            lowerFaceCovered = hint.lowerFaceCovered || occlusionSummary.contains("lower") || occlusionSummary.contains("mid"),
            glasses = hint.glasses || occlusionSummary.contains("glasses"),
            leftEyePatch = hint.leftEyePatch || occlusionSummary.contains("left_eye"),
            rightEyePatch = hint.rightEyePatch || occlusionSummary.contains("right_eye")
        )
    }

    private fun validateOcclusionHint(
        hint: OcclusionHint,
        inferredFrameWithoutHint: dev.andface.galaxy.feature.ObservableFeatureFrame
    ): FailureReason {
        if (hint.isClean) return FailureReason.NONE

        val inferredSummary = inferredFrameWithoutHint.occlusionSummary
        val inferredGlassesInstability = inferredFrameWithoutHint.evidence.any { evidence ->
            evidence.reason.contains("glasses_")
        }

        // MediaPipe can estimate plausible lower-face landmarks through a mask.
        // When the operator marks the lower face as covered, do not trust those
        // estimated lower-face values to reject the hint; exclude them and let
        // upper/mid-face support, margin, Fuzzy, and Mahalanobis gates decide.
        val lowerHintMatches = true
        val glassesHintMatches = !hint.glasses || inferredGlassesInstability
        // A covered eye can still receive plausible-looking MediaPipe landmarks.
        // Treat an explicit patch hint as an instruction to exclude that eye and
        // require the remaining visible regions to carry the identity decision.
        val leftPatchHintMatches = true
        val rightPatchHintMatches = true

        return if (lowerHintMatches && glassesHintMatches && leftPatchHintMatches && rightPatchHintMatches) {
            FailureReason.NONE
        } else {
            FailureReason.OCCLUSION_HINT_MISMATCH
        }
    }


    private fun profileZScore(rawFrame: RawFeatureFrame, profile: EnrollmentProfile, type: FeatureType): Double {
        val sigma = maxOf(profile.sigma(type), type.minimumSigma)
        return abs(rawFrame.value(type) - profile.mean(type)) / sigma
    }

    private fun identityConsistencyScore(
        activations: List<Pair<String, Double>>,
        occlusionSummary: String
    ): Double {
        val activationByType = activations
            .mapNotNull { (label, activation) -> FEATURE_BY_LABEL[label]?.let { type -> type to activation } }
            .toMap()
        val consistencyTypes = identityConsistencyTypes(occlusionSummary)
        var weightedScore = 0.0
        var totalWeight = 0.0

        for (type in consistencyTypes) {
            val activation = activationByType[type] ?: continue
            val normalizedActivation = (activation / type.ruleWeight).coerceIn(0.0, 1.0)
            weightedScore += normalizedActivation * type.ruleWeight
            totalWeight += type.ruleWeight
        }

        return if (totalWeight > 0.0) {
            (weightedScore / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    private fun identityConsistencyTypes(occlusionSummary: String): Set<FeatureType> {
        var types = when {
            occlusionSummary == "clean" -> CORE_IDENTITY_TYPES
            isGlassesOcclusion(occlusionSummary) -> GLASSES_VISIBLE_SEPARATION_TYPES
            else -> CORE_IDENTITY_TYPES
        }

        if (occlusionSummary.contains("lower")) {
            types = types - LOWER_IDENTITY_TYPES
        }
        if (occlusionSummary.contains("mid")) {
            types = types.filterNot { it.isMidFace }.toSet()
        }
        if (occlusionSummary.contains("left_eye")) {
            types = types.filterNot { it.dependsOnLeftEye }.toSet()
        }
        if (occlusionSummary.contains("right_eye")) {
            types = types.filterNot { it.dependsOnRightEye }.toSet()
        }
        return types
    }

    private fun requiredIdentityConsistencyScore(
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean = true
    ): Double {
        val effectiveSummary = if (operatorConfirmedOcclusion) occlusionSummary else "clean"
        val cohortThreshold = when {
            effectiveSummary == "clean" -> MIN_CLEAN_IDENTITY_CONSISTENCY_SCORE
            isGlassesOcclusion(effectiveSummary) -> MIN_GLASSES_IDENTITY_CONSISTENCY_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_REDUCED_IDENTITY_CONSISTENCY_SCORE
            else -> MIN_OCCLUDED_IDENTITY_CONSISTENCY_SCORE
        }
        val absoluteThreshold = when {
            effectiveSummary == "clean" -> MIN_ABSOLUTE_CLEAN_IDENTITY_CONSISTENCY_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_ABSOLUTE_REDUCED_IDENTITY_CONSISTENCY_SCORE
            else -> MIN_ABSOLUTE_OCCLUDED_IDENTITY_CONSISTENCY_SCORE
        }
        return maxOf(cohortThreshold, absoluteThreshold)
    }

    private fun identityOutlierScore(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        observableEvidence: List<FeatureEvidence>,
        occlusionSummary: String
    ): Double {
        val observableTypes = observableEvidence.map { it.type }.toSet()
        val identityTypes = identityConsistencyTypes(occlusionSummary)
            .filter { it in observableTypes }
        val confidenceByType = confidenceByType(observableEvidence)
        var weightedInlierScore = 0.0
        var totalWeight = 0.0

        for (type in identityTypes) {
            val sigma = maxOf(profile.sigma(type), type.minimumSigma)
            val zScore = abs(rawFrame.value(type) - profile.mean(type)) / sigma
            val featureScore = when {
                zScore <= IDENTITY_OUTLIER_SOFT_Z -> 1.0
                zScore >= IDENTITY_OUTLIER_HARD_Z -> 0.0
                else -> 1.0 - ((zScore - IDENTITY_OUTLIER_SOFT_Z) /
                    (IDENTITY_OUTLIER_HARD_Z - IDENTITY_OUTLIER_SOFT_Z))
            }
            val weight = identityEvidenceWeight(type, confidenceByType)
            weightedInlierScore += featureScore.coerceIn(0.0, 1.0) * weight
            totalWeight += weight
        }

        return if (totalWeight > 0.0) {
            (weightedInlierScore / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    private fun requiredIdentityOutlierScore(
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean = true
    ): Double {
        val effectiveSummary = if (operatorConfirmedOcclusion) occlusionSummary else "clean"
        val cohortThreshold = when {
            effectiveSummary == "clean" -> MIN_CLEAN_IDENTITY_OUTLIER_SCORE
            isGlassesOcclusion(effectiveSummary) -> MIN_GLASSES_IDENTITY_OUTLIER_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_REDUCED_IDENTITY_OUTLIER_SCORE
            else -> MIN_OCCLUDED_IDENTITY_OUTLIER_SCORE
        }
        val absoluteThreshold = when {
            effectiveSummary == "clean" -> MIN_ABSOLUTE_IDENTITY_OUTLIER_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_ABSOLUTE_REDUCED_IDENTITY_OUTLIER_SCORE
            else -> MIN_ABSOLUTE_OCCLUDED_IDENTITY_OUTLIER_SCORE
        }
        return maxOf(cohortThreshold, absoluteThreshold)
    }

    private fun hasRegionalIdentityInlierBalance(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        observableEvidence: List<FeatureEvidence>,
        occlusionSummary: String
    ): Boolean {
        val observableTypes = observableEvidence.map { it.type }.toSet()
        val identityTypes = identityConsistencyTypes(occlusionSummary)
            .filter { it in observableTypes }
        val requiredGroups = requiredRegionalIdentityGroups(occlusionSummary)
        if (requiredGroups.isEmpty()) return true
        val confidenceByType = confidenceByType(observableEvidence)

        return requiredGroups.all { group ->
            val regionTypes = identityTypes.filter { it.group == group }
            val regionalEffectiveVisibility = regionTypes.sumOf { type ->
                confidenceByType[type]?.coerceIn(0.0, 1.0) ?: 0.0
            }
            regionTypes.size >= MIN_REGIONAL_INLIER_FEATURE_COUNT &&
                regionalEffectiveVisibility >= minimumRegionalEffectiveVisibility(group, occlusionSummary) &&
                regionalInlierScore(rawFrame, profile, regionTypes, confidenceByType) >= requiredRegionalInlierScore(occlusionSummary)
        }
    }

    private fun requiredRegionalIdentityGroups(occlusionSummary: String): Set<FeatureGroup> {
        if (isExcessiveOcclusion(occlusionSummary)) return emptySet()
        val groups = mutableSetOf<FeatureGroup>()
        groups += FeatureGroup.UPPER_FACE
        if (!occlusionSummary.contains("mid")) groups += FeatureGroup.MID_FACE
        if (!isGlassesOcclusion(occlusionSummary) && !occlusionSummary.contains("lower")) {
            groups += FeatureGroup.LOWER_FACE
        }
        return groups
    }

    private fun minimumRegionalEffectiveVisibility(group: FeatureGroup, occlusionSummary: String): Double {
        val clean = occlusionSummary == "clean"
        return when (group) {
            FeatureGroup.UPPER_FACE -> if (clean) MIN_CLEAN_UPPER_FACE_EFFECTIVE_VISIBILITY else MIN_OCCLUDED_UPPER_FACE_EFFECTIVE_VISIBILITY
            FeatureGroup.MID_FACE -> if (clean) MIN_CLEAN_MID_FACE_EFFECTIVE_VISIBILITY else MIN_OCCLUDED_MID_FACE_EFFECTIVE_VISIBILITY
            FeatureGroup.LOWER_FACE -> MIN_CLEAN_LOWER_FACE_EFFECTIVE_VISIBILITY
            else -> 0.0
        }
    }

    private fun regionalInlierScore(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        types: List<FeatureType>,
        confidenceByType: Map<FeatureType, Double> = emptyMap()
    ): Double {
        var weightedScore = 0.0
        var totalWeight = 0.0

        for (type in types) {
            val sigma = maxOf(profile.sigma(type), type.minimumSigma)
            val zScore = abs(rawFrame.value(type) - profile.mean(type)) / sigma
            val featureScore = when {
                zScore <= REGIONAL_INLIER_SOFT_Z -> 1.0
                zScore >= REGIONAL_INLIER_HARD_Z -> 0.0
                else -> 1.0 - ((zScore - REGIONAL_INLIER_SOFT_Z) /
                    (REGIONAL_INLIER_HARD_Z - REGIONAL_INLIER_SOFT_Z))
            }
            val weight = identityEvidenceWeight(type, confidenceByType)
            weightedScore += featureScore.coerceIn(0.0, 1.0) * weight
            totalWeight += weight
        }

        return if (totalWeight > 0.0) {
            (weightedScore / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    private fun confidenceByType(observableEvidence: List<FeatureEvidence>): Map<FeatureType, Double> {
        return observableEvidence.associate { evidence ->
            evidence.type to evidence.visibility.coerceIn(0.0, 1.0)
        }
    }

    private fun identityEvidenceWeight(
        type: FeatureType,
        confidenceByType: Map<FeatureType, Double>
    ): Double {
        val confidence = confidenceByType[type]?.coerceIn(0.0, 1.0) ?: 1.0
        return type.ruleWeight * confidence
    }
    private fun requiredRegionalInlierScore(occlusionSummary: String): Double {
        return when {
            occlusionSummary == "clean" -> MIN_CLEAN_REGIONAL_INLIER_SCORE
            isReducedIdentityOcclusion(occlusionSummary) -> MIN_REDUCED_REGIONAL_INLIER_SCORE
            else -> MIN_OCCLUDED_REGIONAL_INLIER_SCORE
        }
    }

    private fun hasLocalStructureAgreement(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        observableEvidence: List<FeatureEvidence>,
        occlusionSummary: String
    ): Boolean {
        if (!requiresNoseStructureAgreement(occlusionSummary)) return true

        val observableTypes = observableEvidence.map { it.type }.toSet()
        val confidenceByType = confidenceByType(observableEvidence)
        val noseTypes = NOSE_STRUCTURE_TYPES.filter { it in observableTypes }
        if (noseTypes.size < MIN_NOSE_STRUCTURE_FEATURE_COUNT) return false

        // A shared scale change moves both sides together. Opposite residuals
        // indicate different visible structure and must not be diluted by the
        // many unchanged features or by excluding the covered nose tip.
        val pairedStructureAgrees = VISIBLE_NOSE_ROOT_PAIRS.all { (left, right) ->
            if (left !in observableTypes || right !in observableTypes) return@all true
            val residual = (rawFrame.value(left) - rawFrame.value(right)) -
                (profile.mean(left) - profile.mean(right))
            val leftSigma = maxOf(profile.sigma(left), left.minimumSigma)
            val rightSigma = maxOf(profile.sigma(right), right.minimumSigma)
            val contrastSigma = sqrt(leftSigma * leftSigma + rightSigma * rightSigma)
            abs(residual) <= MICRO_REGION_CLUSTER_OUTLIER_Z * contrastSigma
        }
        if (!pairedStructureAgrees) return false

        val strongInlierCount = noseTypes.count { type ->
            profileZScore(rawFrame, profile, type) <= NOSE_STRUCTURE_STRONG_Z
        }
        return strongInlierCount >= MIN_NOSE_STRUCTURE_STRONG_INLIERS &&
            regionalInlierScore(rawFrame, profile, noseTypes, confidenceByType) >= MIN_NOSE_STRUCTURE_INLIER_SCORE
    }

    private fun requiresNoseStructureAgreement(occlusionSummary: String): Boolean {
        return occlusionSummary.contains("lower") &&
            !isExcessiveOcclusion(occlusionSummary)
    }

    private fun hasMicroRegionAgreement(
        rawFrame: RawFeatureFrame,
        profile: EnrollmentProfile,
        observableEvidence: List<FeatureEvidence>,
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean = false
    ): MicroRegionDecision {
        if (isExcessiveOcclusion(occlusionSummary)) return MicroRegionDecision(true, "excessive")

        val observableTypes = observableEvidence.map { it.type }.toSet()
        val confidenceByType = confidenceByType(observableEvidence)
        val identityTypes = identityConsistencyTypes(occlusionSummary)
            .filter { it in observableTypes }
            .toSet()
        val requiredRegions = requiredMicroRegionTypes(occlusionSummary)
        if (requiredRegions.isEmpty()) return MicroRegionDecision(true, "none")

        val evaluations = requiredRegions.map { regionTypes ->
            val stableRegionTypes = if (operatorConfirmedOcclusion && occlusionSummary != "clean") {
                regionTypes - OPERATOR_OCCLUSION_VOLATILE_MICRO_TYPES
            } else {
                regionTypes
            }
            val availableTypes = stableRegionTypes.filter { it in identityTypes }
            val clusteredOutlierTypes = availableTypes.filter { type ->
                profileZScore(rawFrame, profile, type) >= MICRO_REGION_CLUSTER_OUTLIER_Z
            }
            val clusteredOutlierCount = clusteredOutlierTypes.size
            val inlierScore = regionalInlierScore(rawFrame, profile, availableTypes, confidenceByType)
            val passed = availableTypes.size >= MIN_MICRO_REGION_FEATURE_COUNT &&
                clusteredOutlierCount <= maxMicroRegionClusterOutliers(
                    occlusionSummary,
                    operatorConfirmedOcclusion
                ) &&
                inlierScore >=
                requiredMicroRegionInlierScore(occlusionSummary, operatorConfirmedOcclusion)
            val label = when (regionTypes) {
                LEFT_MICRO_REGION_TYPES -> "L"
                RIGHT_MICRO_REGION_TYPES -> "R"
                CENTER_MICRO_REGION_TYPES -> "C"
                LOWER_MICRO_REGION_TYPES -> "D"
                else -> "?"
            }
            MicroRegionEvaluation(
                label,
                availableTypes.size,
                clusteredOutlierCount,
                inlierScore,
                passed,
                clusteredOutlierTypes.joinToString(",") { it.label }
            )
        }
        return MicroRegionDecision(
            passed = evaluations.all { it.passed },
            diagnostics = evaluations.joinToString(";") { evaluation ->
                    "%s:%d/%d/%.2f/%s[%s]".format(
                    evaluation.label,
                    evaluation.availableCount,
                    evaluation.outlierCount,
                    evaluation.inlierScore,
                    evaluation.passed,
                    evaluation.outlierLabels
                )
            }
        )
    }

    private fun requiredMicroRegionTypes(occlusionSummary: String): List<Set<FeatureType>> {
        val leftEyeBlocked = occlusionSummary.contains("left_eye")
        val rightEyeBlocked = occlusionSummary.contains("right_eye")
        val regions = mutableListOf<Set<FeatureType>>()

        if (!isGlassesOcclusion(occlusionSummary)) {
            if (!leftEyeBlocked) regions += LEFT_MICRO_REGION_TYPES
            if (!rightEyeBlocked) regions += RIGHT_MICRO_REGION_TYPES
        }
        if (!occlusionSummary.contains("mid")) regions += CENTER_MICRO_REGION_TYPES
        if (occlusionSummary == "clean") regions += LOWER_MICRO_REGION_TYPES

        return regions
    }

    private fun requiredMicroRegionInlierScore(
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean = false
    ): Double {
        if (operatorConfirmedOcclusion && occlusionSummary != "clean") {
            return if (isReducedIdentityOcclusion(occlusionSummary)) {
                MIN_OPERATOR_REDUCED_MICRO_REGION_INLIER_SCORE
            } else {
                MIN_OPERATOR_OCCLUDED_MICRO_REGION_INLIER_SCORE
            }
        }
        return when {
            occlusionSummary == "clean" -> MIN_CLEAN_MICRO_REGION_INLIER_SCORE
            isReducedIdentityOcclusion(occlusionSummary) -> MIN_REDUCED_MICRO_REGION_INLIER_SCORE
            else -> MIN_OCCLUDED_MICRO_REGION_INLIER_SCORE
        }
    }

    private fun maxMicroRegionClusterOutliers(
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean = false
    ): Int {
        if (operatorConfirmedOcclusion && occlusionSummary != "clean") {
            return MAX_OPERATOR_OCCLUDED_MICRO_REGION_CLUSTER_OUTLIERS
        }
        return if (occlusionSummary == "clean") {
            MAX_CLEAN_MICRO_REGION_CLUSTER_OUTLIERS
        } else {
            MAX_OCCLUDED_MICRO_REGION_CLUSTER_OUTLIERS
        }
    }
    private fun requiredSupportCount(occlusionSummary: String): Int {
        val cohortThreshold = when {
            occlusionSummary == "clean" -> MIN_CLEAN_FUZZY_SUPPORT_COUNT
            isReducedIdentityOcclusion(occlusionSummary) -> MIN_REDUCED_IDENTITY_FUZZY_SUPPORT_COUNT
            else -> MIN_OCCLUDED_FUZZY_SUPPORT_COUNT
        }
        val absoluteThreshold = when {
            occlusionSummary == "clean" -> MIN_ABSOLUTE_CLEAN_FUZZY_SUPPORT_COUNT
            isReducedIdentityOcclusion(occlusionSummary) -> MIN_ABSOLUTE_REDUCED_FUZZY_SUPPORT_COUNT
            else -> MIN_ABSOLUTE_OCCLUDED_FUZZY_SUPPORT_COUNT
        }
        return maxOf(cohortThreshold, absoluteThreshold)
    }

    private data class MicroRegionDecision(
        val passed: Boolean,
        val diagnostics: String
    )

    private data class MicroRegionEvaluation(
        val label: String,
        val availableCount: Int,
        val outlierCount: Int,
        val inlierScore: Double,
        val passed: Boolean,
        val outlierLabels: String
    )

    private fun resolveOcclusionHint(rawFrame: RawFeatureFrame, requestedHint: OcclusionHint): OcclusionHint {
        if (!requestedHint.isClean) {
            occlusionStateStabilizer.stabilize(summaryForHint(requestedHint), requestedHint)
            return requestedHint
        }

        val referenceProfile = profiles.maxByOrNull { profile ->
            val cleanEvidence = occlusionAnalyzer
                .analyzeResolved(rawFrame, profile, OcclusionHint())
                .observableEvidence
            fuzzyInferenceEngine.score(profile, cleanEvidence).score
        } ?: return requestedHint
        val proposedSummary = occlusionAnalyzer
            .analyze(rawFrame, referenceProfile, OcclusionHint())
            .occlusionSummary
        val stableSummary = occlusionStateStabilizer.stabilize(proposedSummary, requestedHint)
        return hintForSummary(stableSummary)
    }

    private fun summaryForHint(hint: OcclusionHint): String {
        val parts = mutableListOf<String>()
        if (hint.lowerFaceCovered) parts += "lower"
        if (hint.glasses) parts += "glasses"
        if (hint.leftEyePatch) parts += "left_eye"
        if (hint.rightEyePatch) parts += "right_eye"
        return if (parts.isEmpty()) "clean" else parts.joinToString("+")
    }

    private fun hintForSummary(summary: String): OcclusionHint {
        return OcclusionHint(
            lowerFaceCovered = summary.contains("lower") || summary.contains("mid"),
            glasses = summary.contains("glasses"),
            leftEyePatch = summary.contains("left_eye"),
            rightEyePatch = summary.contains("right_eye")
        )
    }

    private fun requiredMahalanobisScore(
        profile: EnrollmentProfile,
        occlusionSummary: String,
        operatorConfirmedOcclusion: Boolean
    ): Double {
        val effectiveSummary = if (operatorConfirmedOcclusion) occlusionSummary else "clean"
        val calibratedCleanFloor = minOf(
            profile.calibratedCleanMahalanobisFloor,
            EnrollmentProfile.DEFAULT_CLEAN_MAHALANOBIS_FLOOR
        )
        val cohortThreshold = when {
            effectiveSummary == "clean" -> MIN_CLEAN_MAHALANOBIS_SCORE
            isReducedIdentityOcclusion(effectiveSummary) -> MIN_REDUCED_IDENTITY_MAHALANOBIS_SCORE
            else -> MIN_OCCLUDED_MAHALANOBIS_SCORE
        }
        val enrolledGenuineThreshold = when {
            effectiveSummary == "clean" -> calibratedCleanFloor
            isReducedIdentityOcclusion(effectiveSummary) ->
                calibratedCleanFloor - REDUCED_IDENTITY_MAHALANOBIS_RELAXATION
            else -> calibratedCleanFloor - OCCLUDED_MAHALANOBIS_RELAXATION
        }
        return maxOf(cohortThreshold, enrolledGenuineThreshold)
    }
    private fun hasIdentityCoverage(
        observableEvidence: List<FeatureEvidence>,
        occlusionSummary: String
    ): Boolean {
        val observableTypes = observableEvidence.map { it.type }.toSet()
        val coreCount = CORE_IDENTITY_TYPES.count { it in observableTypes }
        val upperMidCount = UPPER_MID_IDENTITY_TYPES.count { it in observableTypes }
        val lowerCount = LOWER_IDENTITY_TYPES.count { it in observableTypes }
        val eyeCount = EYE_IDENTITY_TYPES.count { it in observableTypes }
        val poseCount = POSE_TYPES.count { it in observableTypes }

        return when {
            occlusionSummary == "clean" ->
                coreCount >= MIN_CLEAN_CORE_IDENTITY_COUNT &&
                    upperMidCount >= MIN_CLEAN_UPPER_MID_COUNT &&
                    lowerCount >= MIN_CLEAN_LOWER_COUNT
            isBothEyeOcclusion(occlusionSummary) ->
                coreCount >= MIN_BOTH_EYE_OCCLUDED_CORE_IDENTITY_COUNT &&
                    upperMidCount >= MIN_OCCLUDED_UPPER_MID_COUNT &&
                    lowerCount >= MIN_BOTH_EYE_OCCLUDED_LOWER_COUNT &&
                    poseCount >= MIN_BOTH_EYE_OCCLUDED_POSE_COUNT
            isGlassesOcclusion(occlusionSummary) ->
                coreCount >= MIN_GLASSES_CORE_IDENTITY_COUNT &&
                    upperMidCount >= MIN_GLASSES_UPPER_MID_COUNT &&
                    (lowerCount >= MIN_GLASSES_LOWER_COUNT ||
                        poseCount >= MIN_GLASSES_POSE_COUNT ||
                        (occlusionSummary.contains("lower") && eyeCount >= MIN_OCCLUDED_EYE_COUNT))
            isReducedIdentityOcclusion(occlusionSummary) ->
                coreCount >= MIN_REDUCED_CORE_IDENTITY_COUNT &&
                    upperMidCount >= MIN_REDUCED_UPPER_MID_COUNT &&
                    eyeCount >= MIN_REDUCED_EYE_COUNT
            else ->
                coreCount >= MIN_OCCLUDED_CORE_IDENTITY_COUNT &&
                    upperMidCount >= MIN_OCCLUDED_UPPER_MID_COUNT &&
                    eyeCount >= MIN_OCCLUDED_EYE_COUNT
        }
    }

    private fun isBothEyeOcclusion(occlusionSummary: String): Boolean {
        return occlusionSummary.contains("left_eye") && occlusionSummary.contains("right_eye")
    }

    private fun hasSupportDistribution(
        activations: List<Pair<String, Double>>,
        occlusionSummary: String
    ): Boolean {
        val supportedTypes = activations
            .filter { it.second >= FUZZY_SUPPORT_THRESHOLD }
            .mapNotNull { FEATURE_BY_LABEL[it.first] }
            .toSet()
        val upperMidCount = UPPER_MID_IDENTITY_TYPES.count { it in supportedTypes }
        val upperFaceCount = supportedTypes.count { type ->
            type.group == FeatureGroup.UPPER_FACE && type in UPPER_MID_IDENTITY_TYPES
        }
        val midFaceCount = supportedTypes.count { type ->
            type.group == FeatureGroup.MID_FACE && type in UPPER_MID_IDENTITY_TYPES
        }
        val lowerCount = LOWER_IDENTITY_TYPES.count { it in supportedTypes }
        val eyeCount = EYE_IDENTITY_TYPES.count { it in supportedTypes }
        val poseCount = POSE_TYPES.count { it in supportedTypes }
        val midFaceRequired = !occlusionSummary.contains("mid")
        if (occlusionSummary.contains("lower") && occlusionSummary.contains("mid")) {
            return false
        }

        return when {
            occlusionSummary == "clean" ->
                upperMidCount >= MIN_CLEAN_SUPPORTED_UPPER_MID_COUNT &&
                    upperFaceCount >= MIN_CLEAN_SUPPORTED_UPPER_FACE_COUNT &&
                    midFaceCount >= MIN_CLEAN_SUPPORTED_MID_FACE_COUNT &&
                    lowerCount >= MIN_CLEAN_SUPPORTED_LOWER_COUNT
            isGlassesOcclusion(occlusionSummary) ->
                upperMidCount >= MIN_GLASSES_SUPPORTED_UPPER_MID_COUNT &&
                    upperFaceCount >= MIN_GLASSES_SUPPORTED_UPPER_FACE_COUNT &&
                    (!midFaceRequired || midFaceCount >= MIN_GLASSES_SUPPORTED_MID_FACE_COUNT) &&
                    (lowerCount >= MIN_GLASSES_SUPPORTED_LOWER_COUNT ||
                        poseCount >= MIN_GLASSES_SUPPORTED_POSE_COUNT ||
                        (occlusionSummary.contains("lower") && eyeCount >= 2))
            isReducedIdentityOcclusion(occlusionSummary) ->
                upperMidCount >= MIN_REDUCED_SUPPORTED_UPPER_MID_COUNT &&
                    upperFaceCount >= MIN_REDUCED_SUPPORTED_UPPER_FACE_COUNT &&
                    (!midFaceRequired || midFaceCount >= MIN_REDUCED_SUPPORTED_MID_FACE_COUNT) &&
                    (eyeCount >= MIN_REDUCED_SUPPORTED_EYE_COUNT || poseCount >= MIN_REDUCED_SUPPORTED_POSE_COUNT)
            else ->
                upperMidCount >= MIN_OCCLUDED_SUPPORTED_UPPER_MID_COUNT &&
                    upperFaceCount >= MIN_OCCLUDED_SUPPORTED_UPPER_FACE_COUNT &&
                    (!midFaceRequired || midFaceCount >= MIN_OCCLUDED_SUPPORTED_MID_FACE_COUNT) &&
                    (eyeCount >= MIN_OCCLUDED_SUPPORTED_EYE_COUNT || poseCount >= MIN_OCCLUDED_SUPPORTED_POSE_COUNT)
        }
    }

    companion object {
        const val FUZZY_WEIGHT = 0.72
        const val MAHALANOBIS_WEIGHT = 0.28


        private const val MIN_OBSERVABLE_FEATURES = 24
        private const val MIN_OCCLUDED_OBSERVABLE_FEATURES = 20
        private const val MIN_EFFECTIVE_OBSERVABLE_FEATURES = 72.0
        private const val MIN_OCCLUDED_EFFECTIVE_OBSERVABLE_FEATURES = 52.0
        private const val MIN_COVERAGE = 0.60
        private const val MIN_OCCLUDED_COVERAGE = 0.50
        private const val MIN_FINAL_SCORE = 0.78
        private const val MIN_OCCLUDED_FINAL_SCORE = 0.62
        private const val MIN_REDUCED_IDENTITY_FINAL_SCORE = 0.60
        // Occluded features are already excluded from both models. The prior
        // 0.18/0.22 reductions plus operator caps bypassed enrollment calibration
        // and accepted near impostors. Only a small fuzzy-ceiling allowance remains.
        private const val OCCLUDED_FINAL_SCORE_RELAXATION = 0.02
        private const val REDUCED_IDENTITY_FINAL_SCORE_RELAXATION = 0.02
        private const val MIN_MARGIN = 0.10
        private const val MIN_OCCLUDED_MARGIN = 0.05
        private const val MIN_REDUCED_IDENTITY_MARGIN = 0.04
        private const val FUZZY_SUPPORT_THRESHOLD = 0.25
        private const val MIN_CLEAN_FUZZY_SUPPORT_COUNT = 16
        private const val MIN_OCCLUDED_FUZZY_SUPPORT_COUNT = 12
        private const val MIN_REDUCED_IDENTITY_FUZZY_SUPPORT_COUNT = 10
        private const val MIN_ABSOLUTE_CLEAN_FUZZY_SUPPORT_COUNT = 100
        private const val MIN_ABSOLUTE_OCCLUDED_FUZZY_SUPPORT_COUNT = 80
        private const val MIN_ABSOLUTE_REDUCED_FUZZY_SUPPORT_COUNT = 60
        private const val MIN_CLEAN_MAHALANOBIS_SCORE = 0.50
        private const val MIN_OCCLUDED_MAHALANOBIS_SCORE = 0.28
        private const val MIN_REDUCED_IDENTITY_MAHALANOBIS_SCORE = 0.24
        private const val OCCLUDED_MAHALANOBIS_RELAXATION = 0.18
        private const val REDUCED_IDENTITY_MAHALANOBIS_RELAXATION = 0.22
        private const val MIN_CLEAN_IDENTITY_CONSISTENCY_SCORE = 0.66
        private const val MIN_OCCLUDED_IDENTITY_CONSISTENCY_SCORE = 0.58
        private const val MIN_GLASSES_IDENTITY_CONSISTENCY_SCORE = 0.58
        private const val MIN_REDUCED_IDENTITY_CONSISTENCY_SCORE = 0.54
        private const val MIN_ABSOLUTE_CLEAN_IDENTITY_CONSISTENCY_SCORE = 0.88
        private const val MIN_ABSOLUTE_OCCLUDED_IDENTITY_CONSISTENCY_SCORE = 0.74
        private const val MIN_ABSOLUTE_REDUCED_IDENTITY_CONSISTENCY_SCORE = 0.70
        private const val IDENTITY_OUTLIER_SOFT_Z = 2.2
        private const val IDENTITY_OUTLIER_HARD_Z = 4.4
        private const val MIN_CLEAN_IDENTITY_OUTLIER_SCORE = 0.70
        private const val MIN_OCCLUDED_IDENTITY_OUTLIER_SCORE = 0.62
        private const val MIN_GLASSES_IDENTITY_OUTLIER_SCORE = 0.62
        private const val MIN_REDUCED_IDENTITY_OUTLIER_SCORE = 0.58
        private const val MIN_ABSOLUTE_IDENTITY_OUTLIER_SCORE = 0.96
        private const val MIN_ABSOLUTE_OCCLUDED_IDENTITY_OUTLIER_SCORE = 0.90
        private const val MIN_ABSOLUTE_REDUCED_IDENTITY_OUTLIER_SCORE = 0.88
        private const val REGIONAL_INLIER_SOFT_Z = 2.0
        private const val REGIONAL_INLIER_HARD_Z = 4.0
        private const val MIN_CLEAN_REGIONAL_INLIER_SCORE = 0.56
        private const val MIN_OCCLUDED_REGIONAL_INLIER_SCORE = 0.50
        private const val MIN_REDUCED_REGIONAL_INLIER_SCORE = 0.46
        private const val MIN_REGIONAL_INLIER_FEATURE_COUNT = 2
        private const val MIN_CLEAN_UPPER_FACE_EFFECTIVE_VISIBILITY = 3.0
        private const val MIN_CLEAN_MID_FACE_EFFECTIVE_VISIBILITY = 3.0
        private const val MIN_CLEAN_LOWER_FACE_EFFECTIVE_VISIBILITY = 1.5
        private const val MIN_OCCLUDED_UPPER_FACE_EFFECTIVE_VISIBILITY = 2.5
        private const val MIN_OCCLUDED_MID_FACE_EFFECTIVE_VISIBILITY = 2.5
        private const val MIN_MICRO_REGION_FEATURE_COUNT = 3
        private const val MIN_CLEAN_MICRO_REGION_INLIER_SCORE = 0.48
        private const val MIN_OCCLUDED_MICRO_REGION_INLIER_SCORE = 0.42
        private const val MIN_REDUCED_MICRO_REGION_INLIER_SCORE = 0.38
        private const val MIN_OPERATOR_OCCLUDED_MICRO_REGION_INLIER_SCORE = 0.42
        private const val MIN_OPERATOR_REDUCED_MICRO_REGION_INLIER_SCORE = 0.38
        private const val MICRO_REGION_CLUSTER_OUTLIER_Z = 3.2
        private const val MAX_CLEAN_MICRO_REGION_CLUSTER_OUTLIERS = 3
        private const val MAX_OCCLUDED_MICRO_REGION_CLUSTER_OUTLIERS = 2
        private const val MAX_OPERATOR_OCCLUDED_MICRO_REGION_CLUSTER_OUTLIERS = 2
        private const val NOSE_STRUCTURE_STRONG_Z = 2.5
        private const val MIN_NOSE_STRUCTURE_FEATURE_COUNT = 7
        private const val MIN_NOSE_STRUCTURE_STRONG_INLIERS = 10
        private const val MIN_NOSE_STRUCTURE_INLIER_SCORE = 0.54

        private const val MIN_CLEAN_CORE_IDENTITY_COUNT = 30
        private const val MIN_CLEAN_UPPER_MID_COUNT = 20
        private const val MIN_CLEAN_LOWER_COUNT = 3
        private const val MIN_OCCLUDED_CORE_IDENTITY_COUNT = 22
        private const val MIN_OCCLUDED_UPPER_MID_COUNT = 18
        private const val MIN_OCCLUDED_EYE_COUNT = 2
        private const val MIN_REDUCED_CORE_IDENTITY_COUNT = 16
        private const val MIN_REDUCED_UPPER_MID_COUNT = 12
        private const val MIN_REDUCED_EYE_COUNT = 1
        private const val MIN_GLASSES_CORE_IDENTITY_COUNT = 22
        private const val MIN_GLASSES_UPPER_MID_COUNT = 18
        private const val MIN_GLASSES_LOWER_COUNT = 1
        private const val MIN_GLASSES_POSE_COUNT = 1
        private const val MIN_BOTH_EYE_OCCLUDED_CORE_IDENTITY_COUNT = 12
        private const val MIN_BOTH_EYE_OCCLUDED_LOWER_COUNT = 3
        private const val MIN_BOTH_EYE_OCCLUDED_POSE_COUNT = 2
        private const val MIN_CLEAN_SUPPORTED_UPPER_MID_COUNT = 8
        private const val MIN_CLEAN_SUPPORTED_UPPER_FACE_COUNT = 3
        private const val MIN_CLEAN_SUPPORTED_MID_FACE_COUNT = 3
        private const val MIN_CLEAN_SUPPORTED_LOWER_COUNT = 1
        private const val MIN_OCCLUDED_SUPPORTED_UPPER_MID_COUNT = 8
        private const val MIN_OCCLUDED_SUPPORTED_UPPER_FACE_COUNT = 3
        private const val MIN_OCCLUDED_SUPPORTED_MID_FACE_COUNT = 3
        private const val MIN_OCCLUDED_SUPPORTED_EYE_COUNT = 1
        private const val MIN_OCCLUDED_SUPPORTED_POSE_COUNT = 1
        private const val MIN_GLASSES_SUPPORTED_UPPER_MID_COUNT = 8
        private const val MIN_GLASSES_SUPPORTED_UPPER_FACE_COUNT = 3
        private const val MIN_GLASSES_SUPPORTED_MID_FACE_COUNT = 3
        private const val MIN_GLASSES_SUPPORTED_LOWER_COUNT = 1
        private const val MIN_GLASSES_SUPPORTED_POSE_COUNT = 1
        private const val MIN_REDUCED_SUPPORTED_UPPER_MID_COUNT = 5
        private const val MIN_REDUCED_SUPPORTED_UPPER_FACE_COUNT = 3
        private const val MIN_REDUCED_SUPPORTED_MID_FACE_COUNT = 2
        private const val MIN_REDUCED_SUPPORTED_EYE_COUNT = 1
        private const val MIN_REDUCED_SUPPORTED_POSE_COUNT = 1
        private const val MAX_ENROLLMENT_PROFILE_SIMILARITY = 0.88
        private const val MAX_MASK_ENROLLMENT_PROFILE_SIMILARITY = 0.86
        private const val MAX_MASK_CRITICAL_PROFILE_SIMILARITY = 0.87
        private const val MAX_NOSE_STRUCTURE_PROFILE_SIMILARITY = 0.86
        private const val MAX_GLASSES_ENROLLMENT_PROFILE_SIMILARITY = 0.88
        private const val MAX_PATCH_ENROLLMENT_PROFILE_SIMILARITY = 0.88

        private val CORE_IDENTITY_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.LOWER_FACE ||
                    type.group == FeatureGroup.EYE ||
                    type == FeatureType.FaceAspect
            }
            .toSet()

        private val UPPER_MID_IDENTITY_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.EYE
            }
            .toSet()

        private val LOWER_IDENTITY_TYPES = FeatureType.ordered
            .filter { type -> type.group == FeatureGroup.LOWER_FACE || type == FeatureType.FaceAspect }
            .toSet()

        private val EYE_IDENTITY_TYPES = FeatureType.ordered
            .filter { type -> type.group == FeatureGroup.EYE }
            .toSet()

        private val POSE_TYPES = setOf(
            FeatureType.Yaw,
            FeatureType.Pitch,
            FeatureType.Roll
        )

        private val LEFT_MICRO_REGION_TYPES = FeatureType.ordered
            .filter { type -> type.isLeftEyeLocalFeature }
            .toSet()

        private val RIGHT_MICRO_REGION_TYPES = FeatureType.ordered
            .filter { type -> type.isRightEyeLocalFeature }
            .toSet()

        private val CENTER_MICRO_REGION_TYPES = FeatureType.ordered
            .filter { type ->
                (type.group == FeatureGroup.UPPER_FACE || type.group == FeatureGroup.MID_FACE) &&
                    !type.dependsOnLeftEye &&
                    !type.dependsOnRightEye
            }
            .toSet()

        private val LOWER_MICRO_REGION_TYPES = LOWER_IDENTITY_TYPES

        // These measurements move with blinking, iris tracking, and small camera-angle
        // changes when a mask or patch is explicitly declared. They remain available to
        // fuzzy/global scoring, but are not counted as hard micro-cluster mismatches.
        private val OPERATOR_OCCLUSION_VOLATILE_MICRO_TYPES = FeatureType.ordered
            .filter { type ->
                type.isIrisFeature || type in setOf(
                    FeatureType.EyeOpenAsymmetry,
                    FeatureType.EyeWidthAsymmetry,
                    FeatureType.EyeHeightAsymmetry,
                    FeatureType.BrowEyeDistanceAsymmetry,
                    FeatureType.LeftEyeCornerTilt,
                    FeatureType.RightEyeCornerTilt,
                    FeatureType.LeftBrowSlope,
                    FeatureType.RightBrowSlope,
                    FeatureType.BrowSlopeAsymmetry,
                    FeatureType.BrowLineTilt,
                    FeatureType.LeftBrowTempleSlope,
                    FeatureType.RightBrowTempleSlope,
                    FeatureType.BrowTempleSlopeAsymmetry
                )
            }
            .toSet()

        private val NOSE_STRUCTURE_TYPES = FeatureType.ordered
            .filter { type -> type.group == FeatureGroup.MID_FACE }
            .toList()

        private val VISIBLE_NOSE_ROOT_PAIRS = listOf(
            FeatureType.LeftBrowNoseRoot to FeatureType.RightBrowNoseRoot,
            FeatureType.LeftInnerEyeNoseRoot to FeatureType.RightInnerEyeNoseRoot,
            FeatureType.LeftOuterEyeNoseRoot to FeatureType.RightOuterEyeNoseRoot
        )

        private val MASK_VISIBLE_SEPARATION_TYPES = UPPER_MID_IDENTITY_TYPES
        private val MASK_CRITICAL_SEPARATION_TYPES = UPPER_MID_IDENTITY_TYPES
        private val NOSE_STRUCTURE_SEPARATION_TYPES = NOSE_STRUCTURE_TYPES.toSet()
        private val GLASSES_VISIBLE_SEPARATION_TYPES = UPPER_MID_IDENTITY_TYPES
        private val LEFT_PATCH_VISIBLE_SEPARATION_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.EYE
            }
            .filterNot { type -> type.dependsOnLeftEye && !type.dependsOnRightEye }
            .toSet()
        private val RIGHT_PATCH_VISIBLE_SEPARATION_TYPES = FeatureType.ordered
            .filter { type ->
                type.group == FeatureGroup.UPPER_FACE ||
                    type.group == FeatureGroup.MID_FACE ||
                    type.group == FeatureGroup.EYE
            }
            .filterNot { type -> type.dependsOnRightEye && !type.dependsOnLeftEye }
            .toSet()

        private val ENROLLMENT_SEPARATION_SCENARIOS = listOf(
            EnrollmentSeparationScenario("clean", CORE_IDENTITY_TYPES, MAX_ENROLLMENT_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("mask", MASK_VISIBLE_SEPARATION_TYPES, MAX_MASK_ENROLLMENT_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("mask_critical", MASK_CRITICAL_SEPARATION_TYPES, MAX_MASK_CRITICAL_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("mask_nose_structure", NOSE_STRUCTURE_SEPARATION_TYPES, MAX_NOSE_STRUCTURE_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("glasses", GLASSES_VISIBLE_SEPARATION_TYPES, MAX_GLASSES_ENROLLMENT_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("left_patch", LEFT_PATCH_VISIBLE_SEPARATION_TYPES, MAX_PATCH_ENROLLMENT_PROFILE_SIMILARITY),
            EnrollmentSeparationScenario("right_patch", RIGHT_PATCH_VISIBLE_SEPARATION_TYPES, MAX_PATCH_ENROLLMENT_PROFILE_SIMILARITY)
        )
        private val FEATURE_BY_LABEL = FeatureType.ordered.associateBy { it.label }

        private val OPTIONAL_IRIS_TYPES = FeatureType.ordered.filter { it.isIrisFeature }
        private val FeatureCountForCleanEnrollment = FeatureType.COUNT
        private val FeatureCountForCleanEnrollmentWithoutOptionalIris =
            FeatureType.COUNT - OPTIONAL_IRIS_TYPES.size
        private const val CLEAN_ENROLLMENT_COVERAGE = 0.58
        private const val MAX_ENROLLMENT_CAPTURE_POSE = 0.60
        private const val MIN_CLEAN_ENROLLMENT_EFFECTIVE_FEATURES = 105.0
        private val MIN_CLEAN_ENROLLMENT_EFFECTIVE_FEATURES_WITHOUT_OPTIONAL_IRIS =
            MIN_CLEAN_ENROLLMENT_EFFECTIVE_FEATURES *
                (FeatureCountForCleanEnrollmentWithoutOptionalIris.toDouble() / FeatureCountForCleanEnrollment.toDouble())
        private val CLEAN_ENROLLMENT_COVERAGE_WITHOUT_OPTIONAL_IRIS =
            CLEAN_ENROLLMENT_COVERAGE *
                (FeatureType.ordered.filterNot { it.isIrisFeature }.sumOf { it.ruleWeight } /
                    FeatureType.ordered.sumOf { it.ruleWeight })
    }
}












