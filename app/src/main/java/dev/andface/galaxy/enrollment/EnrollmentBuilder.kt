package dev.andface.galaxy.enrollment

import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.feature.FeatureEvidence
import dev.andface.galaxy.feature.FeatureGroup
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.fuzzy.FuzzyInferenceEngine
import dev.andface.galaxy.mahalanobis.MahalanobisEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EnrollmentBuilder {
    fun build(userId: String, samples: List<RawFeatureFrame>): EnrollmentProfile {
        require(samples.isNotEmpty()) { "At least one clean-face sample is required." }

        val calibration = calibrateCleanAcceptance(userId, samples)
        return buildCore(
            userId = userId,
            samples = samples,
            calibratedCleanMahalanobisFloor = calibration.mahalanobisFloor,
            calibratedCleanFinalScoreFloor = calibration.finalScoreFloor
        )
    }

    private fun buildCore(
        userId: String,
        samples: List<RawFeatureFrame>,
        calibratedCleanMahalanobisFloor: Double = EnrollmentProfile.DEFAULT_CLEAN_MAHALANOBIS_FLOOR,
        calibratedCleanFinalScoreFloor: Double = EnrollmentProfile.DEFAULT_CLEAN_FINAL_SCORE_FLOOR
    ): EnrollmentProfile {

        val consensusSamples = consensusFilteredSamples(samples)
        val means = DoubleArray(FeatureType.COUNT)
        val trimCount = trimCountFor(consensusSamples.size)
        for (index in means.indices) {
            means[index] = trimmedMean(consensusSamples, index, trimCount)
        }

        val covariance = Array(FeatureType.COUNT) { DoubleArray(FeatureType.COUNT) }
        val bounds = Array(FeatureType.COUNT) { index ->
            winsorBounds(consensusSamples, index, trimCount)
        }
        if (consensusSamples.size > 1) {
            for (sample in consensusSamples) {
                for (row in 0 until FeatureType.COUNT) {
                    val rowDelta = sample.values[row].coerceIn(bounds[row].first, bounds[row].second) - means[row]
                    for (col in 0 until FeatureType.COUNT) {
                        val colDelta = sample.values[col].coerceIn(bounds[col].first, bounds[col].second) - means[col]
                        covariance[row][col] += rowDelta * colDelta
                    }
                }
            }
            val divisor = consensusSamples.size - 1.0
            for (row in 0 until FeatureType.COUNT) {
                for (col in 0 until FeatureType.COUNT) {
                    covariance[row][col] /= divisor
                }
            }
        }

        val sigmas = DoubleArray(FeatureType.COUNT)
        for (type in FeatureType.ordered) {
            val variance = max(covariance[type.ordinal][type.ordinal], type.minimumSigma * type.minimumSigma)
            sigmas[type.ordinal] = sqrt(variance)
            covariance[type.ordinal][type.ordinal] = variance
        }
        if (consensusSamples.any { !it.quality.irisLandmarksAvailable }) {
            markOptionalIrisUnavailable(sigmas, covariance)
        }

        return EnrollmentProfile(
            userId = userId,
            createdAtMs = System.currentTimeMillis(),
            modelSha256 = BuildConfig.FACE_LANDMARKER_MODEL_SHA256,
            policyVersion = EnrollmentProfile.CURRENT_POLICY_VERSION,
            sampleCount = samples.size,
            means = means,
            sigmas = sigmas,
            covariance = covariance,
            calibratedCleanMahalanobisFloor = calibratedCleanMahalanobisFloor,
            calibratedCleanFinalScoreFloor = calibratedCleanFinalScoreFloor
        )
    }

    private fun calibrateCleanAcceptance(userId: String, samples: List<RawFeatureFrame>): CleanCalibration {
        if (samples.size < MIN_CALIBRATION_SAMPLE_COUNT) {
            return CleanCalibration(
                EnrollmentProfile.DEFAULT_CLEAN_MAHALANOBIS_FLOOR,
                EnrollmentProfile.DEFAULT_CLEAN_FINAL_SCORE_FLOOR
            )
        }

        val validationSamples = samples.filterIndexed { index, _ ->
            index % CALIBRATION_VALIDATION_INTERVAL == CALIBRATION_VALIDATION_INTERVAL - 1
        }
        val trainingSamples = samples.filterIndexed { index, _ ->
            index % CALIBRATION_VALIDATION_INTERVAL != CALIBRATION_VALIDATION_INTERVAL - 1
        }
        if (validationSamples.size < MIN_CALIBRATION_VALIDATION_COUNT ||
            trainingSamples.size < MIN_CALIBRATION_TRAINING_COUNT
        ) {
            return CleanCalibration(
                EnrollmentProfile.DEFAULT_CLEAN_MAHALANOBIS_FLOOR,
                EnrollmentProfile.DEFAULT_CLEAN_FINAL_SCORE_FLOOR
            )
        }

        val provisionalProfile = buildCore(userId, trainingSamples)
        val fuzzyEngine = FuzzyInferenceEngine()
        val mahalanobisEngine = MahalanobisEngine()
        val scores = validationSamples.map { sample ->
            val evidence = cleanEvidence(sample, provisionalProfile)
            val fuzzy = fuzzyEngine.score(provisionalProfile, evidence).score
            val mahalanobis = mahalanobisEngine.score(provisionalProfile, evidence).score
            CalibrationScore(
                mahalanobis = mahalanobis,
                finalScore = (CALIBRATION_FUZZY_WEIGHT * fuzzy + CALIBRATION_MAHALANOBIS_WEIGHT * mahalanobis)
                    .coerceIn(0.0, 1.0)
            )
        }
        val mahalanobisReference = lowerPercentile(scores.map { it.mahalanobis })
        val finalScoreReference = lowerPercentile(scores.map { it.finalScore })
        return CleanCalibration(
            mahalanobisFloor = calibratedFloor(
                reference = mahalanobisReference,
                excellentReference = EXCELLENT_MAHALANOBIS_REFERENCE,
                defaultFloor = EnrollmentProfile.DEFAULT_CLEAN_MAHALANOBIS_FLOOR,
                minimumFloor = MIN_CALIBRATED_CLEAN_MAHALANOBIS_FLOOR
            ),
            finalScoreFloor = calibratedFloor(
                reference = finalScoreReference,
                excellentReference = EXCELLENT_FINAL_SCORE_REFERENCE,
                defaultFloor = EnrollmentProfile.DEFAULT_CLEAN_FINAL_SCORE_FLOOR,
                minimumFloor = MIN_CALIBRATED_CLEAN_FINAL_SCORE_FLOOR
            )
        )
    }

    private fun cleanEvidence(
        sample: RawFeatureFrame,
        profile: EnrollmentProfile
    ): List<FeatureEvidence> {
        return FeatureType.ordered.map { type ->
            val irisAvailable = !type.isIrisFeature || (
                sample.quality.irisLandmarksAvailable &&
                    profile.sigma(type) < type.minimumSigma * UNRELIABLE_OPTIONAL_IRIS_SIGMA_MULTIPLIER
                )
            val visibility = if (irisAvailable) {
                profile.featureReliability(type) * sample.quality.accessScoringConfidence
            } else {
                0.0
            }
            FeatureEvidence(
                type = type,
                value = sample.value(type),
                visibility = visibility.coerceIn(0.0, 1.0),
                observable = irisAvailable && visibility >= MIN_CALIBRATION_VISIBILITY,
                reason = if (irisAvailable) "calibration" else "iris_unavailable"
            )
        }
    }

    private fun lowerPercentile(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * CALIBRATION_LOWER_PERCENTILE).toInt()
            .coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun calibratedFloor(
        reference: Double,
        excellentReference: Double,
        defaultFloor: Double,
        minimumFloor: Double
    ): Double {
        if (reference >= excellentReference) return defaultFloor
        return (reference - CALIBRATION_GENERALIZATION_MARGIN)
            .coerceIn(minimumFloor, defaultFloor)
    }

    private fun markOptionalIrisUnavailable(
        sigmas: DoubleArray,
        covariance: Array<DoubleArray>
    ) {
        for (type in OPTIONAL_IRIS_TYPES) {
            val index = type.ordinal
            val sigma = type.minimumSigma * UNRELIABLE_OPTIONAL_IRIS_SIGMA_MULTIPLIER
            sigmas[index] = sigma
            for (other in 0 until FeatureType.COUNT) {
                covariance[index][other] = 0.0
                covariance[other][index] = 0.0
            }
            covariance[index][index] = sigma * sigma
        }
    }

    private fun consensusFilteredSamples(samples: List<RawFeatureFrame>): List<RawFeatureFrame> {
        if (samples.size < MIN_FRAME_CONSENSUS_SAMPLE_COUNT) return samples

        val medians = DoubleArray(FeatureType.COUNT) { index ->
            samples.map { it.values[index] }.median()
        }
        val robustScales = DoubleArray(FeatureType.COUNT) { index ->
            val median = medians[index]
            val mad = samples.map { abs(it.values[index] - median) }.median()
            max(FeatureType.ordered[index].minimumSigma, mad * MAD_TO_SIGMA)
        }
        val scoredSamples = samples.mapIndexed { index, sample ->
            FrameConsensusScore(index, consensusOutlierScore(sample, medians, robustScales))
        }
        val maxDropCount = min(MAX_FRAME_CONSENSUS_DROP_COUNT, samples.size / FRAME_CONSENSUS_DROP_DIVISOR)
        val droppedIndices = scoredSamples
            .filter { it.score >= FRAME_CONSENSUS_DROP_SCORE }
            .sortedByDescending { it.score }
            .take(maxDropCount)
            .map { it.index }
            .toSet()
        if (droppedIndices.isEmpty()) return samples

        val retained = samples.filterIndexed { index, _ -> index !in droppedIndices }
        return if (retained.size >= MIN_FRAME_CONSENSUS_RETAINED_COUNT) retained else samples
    }

    private fun consensusOutlierScore(
        sample: RawFeatureFrame,
        medians: DoubleArray,
        robustScales: DoubleArray
    ): Double {
        val globalScore = weightedOutlierRatio(FRAME_CONSENSUS_TYPES, sample, medians, robustScales)
        val clusterScore = FRAME_CONSENSUS_CLUSTER_TYPES.maxOfOrNull { types ->
            weightedOutlierRatio(types, sample, medians, robustScales)
        } ?: 0.0
        return max(globalScore, clusterScore)
    }

    private fun weightedOutlierRatio(
        types: List<FeatureType>,
        sample: RawFeatureFrame,
        medians: DoubleArray,
        robustScales: DoubleArray
    ): Double {
        val totalWeight = types.sumOf { it.ruleWeight }
        if (totalWeight <= 0.0) return 0.0
        val outlierWeight = types.sumOf { type ->
            val zScore = abs(sample.value(type) - medians[type.ordinal]) / robustScales[type.ordinal]
            if (zScore >= FRAME_CONSENSUS_OUTLIER_Z) type.ruleWeight else 0.0
        }
        return (outlierWeight / totalWeight).coerceIn(0.0, 1.0)
    }
    private fun trimCountFor(sampleCount: Int): Int {
        return if (sampleCount >= MIN_ROBUST_SAMPLE_COUNT) {
            min(MAX_TRIM_COUNT, sampleCount / ROBUST_TRIM_DIVISOR)
        } else {
            0
        }
    }

    private fun trimmedMean(samples: List<RawFeatureFrame>, index: Int, trimCount: Int): Double {
        val sorted = samples.map { it.values[index] }.sorted()
        val from = trimCount.coerceAtMost(sorted.lastIndex)
        val toExclusive = (sorted.size - trimCount).coerceAtLeast(from + 1)
        return sorted.subList(from, toExclusive).average()
    }

    private fun winsorBounds(samples: List<RawFeatureFrame>, index: Int, trimCount: Int): Pair<Double, Double> {
        val sorted = samples.map { it.values[index] }.sorted()
        val lowIndex = trimCount.coerceIn(0, sorted.lastIndex)
        val highIndex = (sorted.lastIndex - trimCount).coerceIn(lowIndex, sorted.lastIndex)
        return sorted[lowIndex] to sorted[highIndex]
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) * 0.5
        }
    }

    private data class FrameConsensusScore(
        val index: Int,
        val score: Double
    )

    private data class CalibrationScore(
        val mahalanobis: Double,
        val finalScore: Double
    )

    private data class CleanCalibration(
        val mahalanobisFloor: Double,
        val finalScoreFloor: Double
    )

    private const val MIN_ROBUST_SAMPLE_COUNT = 20
    private const val ROBUST_TRIM_DIVISOR = 10
    private const val MAX_TRIM_COUNT = 4
    private const val MIN_FRAME_CONSENSUS_SAMPLE_COUNT = 30
    private const val MIN_FRAME_CONSENSUS_RETAINED_COUNT = 24
    private const val FRAME_CONSENSUS_OUTLIER_Z = 4.0
    private const val FRAME_CONSENSUS_DROP_SCORE = 0.18
    private const val FRAME_CONSENSUS_DROP_DIVISOR = 6
    private const val MAX_FRAME_CONSENSUS_DROP_COUNT = 8
    private const val MAD_TO_SIGMA = 1.4826
    private const val UNRELIABLE_OPTIONAL_IRIS_SIGMA_MULTIPLIER = 40.0
    private const val MIN_CALIBRATION_SAMPLE_COUNT = 32
    private const val CALIBRATION_VALIDATION_INTERVAL = 4
    private const val MIN_CALIBRATION_VALIDATION_COUNT = 8
    private const val MIN_CALIBRATION_TRAINING_COUNT = 24
    private const val CALIBRATION_LOWER_PERCENTILE = 0.20
    private const val CALIBRATION_GENERALIZATION_MARGIN = 0.045
    private const val EXCELLENT_MAHALANOBIS_REFERENCE = 0.985
    private const val EXCELLENT_FINAL_SCORE_REFERENCE = 0.93
    private const val MIN_CALIBRATED_CLEAN_MAHALANOBIS_FLOOR = 0.84
    private const val MIN_CALIBRATED_CLEAN_FINAL_SCORE_FLOOR = 0.78
    private const val MIN_CALIBRATION_VISIBILITY = 0.15
    private const val CALIBRATION_FUZZY_WEIGHT = 0.70
    private const val CALIBRATION_MAHALANOBIS_WEIGHT = 0.30

    private val OPTIONAL_IRIS_TYPES = FeatureType.ordered.filter { it.isIrisFeature }

    private val NEWEST_CONTOUR_CONSENSUS_TYPES = listOf(
        FeatureType.EyeLineBrowLineGap,
        FeatureType.LeftOuterEyeNoseRoot,
        FeatureType.RightOuterEyeNoseRoot,
        FeatureType.OuterEyeNoseRootAsymmetry,
        FeatureType.LeftEyeForeheadDistance,
        FeatureType.RightEyeForeheadDistance,
        FeatureType.EyeForeheadAsymmetry,
        FeatureType.NoseRootForeheadDistance,
        FeatureType.UpperFaceDiagonalRatio,
        FeatureType.UpperFacePerimeterRatio,
        FeatureType.LeftJawCheekDistance,
        FeatureType.RightJawCheekDistance,
        FeatureType.JawCheekAsymmetry,
        FeatureType.ChinLateralOffset,
        FeatureType.LeftOrbitalTriangle,
        FeatureType.RightOrbitalTriangle,
        FeatureType.OrbitalTriangleAsymmetry,
        FeatureType.LeftInnerBrowNoseArea,
        FeatureType.RightInnerBrowNoseArea,
        FeatureType.InnerBrowNoseAreaAsymmetry,
        FeatureType.BrowSpanEyeSpanRatio,
        FeatureType.NoseRootToEyeSpanRatio,
        FeatureType.LeftForeheadTempleArea,
        FeatureType.RightForeheadTempleArea,
        FeatureType.ForeheadTempleAreaAsymmetry,
        FeatureType.LeftEyeBrowNoseTriangle,
        FeatureType.RightEyeBrowNoseTriangle,
        FeatureType.EyeBrowNoseTriangleAsymmetry,
        FeatureType.NoseBridgeEyeTriangle,
        FeatureType.NoseBridgeToEyeSpanRatio,
        FeatureType.LeftInnerEyeNoseBridgeTriangle,
        FeatureType.RightInnerEyeNoseBridgeTriangle,
        FeatureType.InnerEyeNoseBridgeTriangleAsymmetry,
        FeatureType.NoseTipInnerEyeLineDistance,
        FeatureType.NoseBridgeInnerEyeSpanRatio,
        FeatureType.NoseTipDepthToEyeSpanRatio,
        FeatureType.LeftUpperEyelidArch,
        FeatureType.RightUpperEyelidArch,
        FeatureType.UpperEyelidArchAsymmetry,
        FeatureType.LeftLowerEyelidArch,
        FeatureType.RightLowerEyelidArch,
        FeatureType.LowerEyelidArchAsymmetry,
        FeatureType.NoseBridgeBrowLineOffset,
        FeatureType.NoseBridgeEyeLineOffset
    )
    private val FRAME_CONSENSUS_CLUSTER_TYPES = listOf(
        NEWEST_CONTOUR_CONSENSUS_TYPES
    )

    private val FRAME_CONSENSUS_TYPES = FeatureType.ordered
        .filter { type ->
            type.group == FeatureGroup.UPPER_FACE ||
                type.group == FeatureGroup.MID_FACE ||
                type.group == FeatureGroup.EYE ||
                type.group == FeatureGroup.LOWER_FACE ||
                type == FeatureType.FaceAspect
        }

}
