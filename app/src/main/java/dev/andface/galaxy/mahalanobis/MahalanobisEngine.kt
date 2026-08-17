package dev.andface.galaxy.mahalanobis

import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.feature.FeatureEvidence
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class MahalanobisResult(
    val score: Double,
    val distance: Double,
    val usedDiagonalFallback: Boolean
)

class MahalanobisEngine {
    fun score(profile: EnrollmentProfile, observableEvidence: List<FeatureEvidence>): MahalanobisResult {
        val usableEvidence = observableEvidence.filter { evidence ->
            evidence.observable && evidence.visibility > 0.0
        }
        if (usableEvidence.isEmpty()) {
            return MahalanobisResult(score = 0.0, distance = Double.POSITIVE_INFINITY, usedDiagonalFallback = true)
        }

        val dimensions = usableEvidence.size
        val diff = DoubleArray(dimensions)
        val covariance = Array(dimensions) { DoubleArray(dimensions) }
        val diagonalVariances = DoubleArray(dimensions)
        val shrinkage = shrinkageFor(profile.sampleCount, dimensions)
        var effectiveDimensionSum = 0.0

        for (row in 0 until dimensions) {
            val rowEvidence = usableEvidence[row]
            val rowType = rowEvidence.type
            val confidence = rowEvidence.visibility.coerceIn(MIN_CONFIDENCE_WEIGHT, 1.0)
            effectiveDimensionSum += confidence
            diff[row] = rowEvidence.value - profile.mean(rowType)
            val floorVariance = rowType.minimumSigma * rowType.minimumSigma
            val profileVariance = max(profile.covariance[rowType.ordinal][rowType.ordinal], floorVariance)
            diagonalVariances[row] = profileVariance +
                regularizationFor(profileVariance) +
                measurementUncertaintyVariance(floorVariance, confidence)
        }

        for (row in 0 until dimensions) {
            val rowType = usableEvidence[row].type
            for (col in 0 until dimensions) {
                val colType = usableEvidence[col].type
                covariance[row][col] = if (row == col) {
                    diagonalVariances[row]
                } else {
                    val maxCovariance = sqrt(diagonalVariances[row] * diagonalVariances[col]) * MAX_ABS_CORRELATION
                    ((1.0 - shrinkage) * profile.covariance[rowType.ordinal][colType.ordinal])
                        .coerceIn(-maxCovariance, maxCovariance)
                }
            }
        }

        var usedFallback = false
        val distanceSquared = choleskyDistance(diff, covariance) ?: run {
            val inverse = invert(covariance)
            if (inverse != null) {
                quadraticForm(diff, inverse)
            } else {
                usedFallback = true
                diagonalDistance(diff, diagonalVariances)
            }
        }

        val boundedDistanceSquared = max(distanceSquared, 0.0)
        val effectiveDimensions = max(effectiveDimensionSum, 1.0)
        val normalizedDistance = boundedDistanceSquared / effectiveDimensions
        val exponentialScore = exp(-DISTANCE_TO_SCORE_SCALE * normalizedDistance).coerceIn(0.0, 1.0)
        val chiSquareScore = chiSquareSurvivalScore(boundedDistanceSquared, effectiveDimensions)
        val score = (EXPONENTIAL_SCORE_WEIGHT * exponentialScore + CHI_SQUARE_SCORE_WEIGHT * chiSquareScore)
            .coerceIn(0.0, 1.0)
        return MahalanobisResult(
            score = score,
            distance = normalizedDistance,
            usedDiagonalFallback = usedFallback
        )
    }

    private fun diagonalDistance(diff: DoubleArray, diagonalVariances: DoubleArray): Double {
        var sum = 0.0
        for (index in diff.indices) {
            sum += (diff[index] * diff[index]) / diagonalVariances[index]
        }
        return sum
    }

    private fun choleskyDistance(diff: DoubleArray, covariance: Array<DoubleArray>): Double? {
        val size = covariance.size
        val lower = Array(size) { DoubleArray(size) }
        for (row in 0 until size) {
            for (col in 0..row) {
                var sum = covariance[row][col]
                for (index in 0 until col) {
                    sum -= lower[row][index] * lower[col][index]
                }
                if (row == col) {
                    if (sum <= CHOLESKY_EPSILON || sum.isNaN() || sum.isInfinite()) return null
                    lower[row][col] = sqrt(sum)
                } else {
                    val pivot = lower[col][col]
                    if (pivot <= CHOLESKY_EPSILON) return null
                    lower[row][col] = sum / pivot
                }
            }
        }

        val solved = DoubleArray(size)
        for (row in 0 until size) {
            var value = diff[row]
            for (col in 0 until row) {
                value -= lower[row][col] * solved[col]
            }
            solved[row] = value / lower[row][row]
        }

        return solved.sumOf { value -> value * value }
    }

    private fun quadraticForm(diff: DoubleArray, inverse: Array<DoubleArray>): Double {
        var total = 0.0
        for (row in diff.indices) {
            var rowValue = 0.0
            for (col in diff.indices) {
                rowValue += inverse[row][col] * diff[col]
            }
            total += diff[row] * rowValue
        }
        return total
    }

    private fun invert(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val size = matrix.size
        val augmented = Array(size) { row ->
            DoubleArray(size * 2) { col ->
                when {
                    col < size -> matrix[row][col]
                    col - size == row -> 1.0
                    else -> 0.0
                }
            }
        }

        for (pivotIndex in 0 until size) {
            var bestRow = pivotIndex
            var bestAbs = kotlin.math.abs(augmented[pivotIndex][pivotIndex])
            for (row in pivotIndex + 1 until size) {
                val candidate = kotlin.math.abs(augmented[row][pivotIndex])
                if (candidate > bestAbs) {
                    bestAbs = candidate
                    bestRow = row
                }
            }

            if (bestAbs < PIVOT_EPSILON) return null

            if (bestRow != pivotIndex) {
                val temp = augmented[pivotIndex]
                augmented[pivotIndex] = augmented[bestRow]
                augmented[bestRow] = temp
            }

            val pivot = augmented[pivotIndex][pivotIndex]
            for (col in 0 until size * 2) {
                augmented[pivotIndex][col] /= pivot
            }

            for (row in 0 until size) {
                if (row == pivotIndex) continue
                val factor = augmented[row][pivotIndex]
                for (col in 0 until size * 2) {
                    augmented[row][col] -= factor * augmented[pivotIndex][col]
                }
            }
        }

        return Array(size) { row ->
            DoubleArray(size) { col -> augmented[row][col + size] }
        }
    }

    private fun shrinkageFor(sampleCount: Int, dimensions: Int): Double {
        val sampleRatio = dimensions.toDouble() / max(sampleCount + dimensions, 1).toDouble()
        return min(MAX_SHRINKAGE, BASE_SHRINKAGE + sampleRatio * SAMPLE_RATIO_SHRINKAGE).coerceAtLeast(MIN_SHRINKAGE)
    }

    private fun regularizationFor(variance: Double): Double {
        return max(BASE_COVARIANCE_REGULARIZATION, variance * RELATIVE_REGULARIZATION)
    }

    private fun measurementUncertaintyVariance(floorVariance: Double, confidence: Double): Double {
        val uncertainty = (1.0 - confidence).coerceIn(0.0, 1.0)
        return floorVariance * MEASUREMENT_UNCERTAINTY_SCALE * uncertainty / confidence
    }

    private fun chiSquareSurvivalScore(distanceSquared: Double, dimensions: Double): Double {
        if (distanceSquared <= 0.0) return 1.0
        val k = max(dimensions, 1.0)
        val transformed = (distanceSquared / k).pow(1.0 / 3.0)
        val mean = 1.0 - 2.0 / (9.0 * k)
        val standardDeviation = sqrt(2.0 / (9.0 * k))
        val z = (transformed - mean) / standardDeviation
        return (1.0 - normalCdf(z)).coerceIn(0.0, 1.0)
    }

    private fun normalCdf(value: Double): Double {
        return 0.5 * (1.0 + erf(value / sqrt(2.0)))
    }

    private fun erf(value: Double): Double {
        val sign = if (value < 0.0) -1.0 else 1.0
        val x = abs(value)
        val t = 1.0 / (1.0 + ERF_P * x)
        val y = 1.0 - (((((ERF_A5 * t + ERF_A4) * t) + ERF_A3) * t + ERF_A2) * t + ERF_A1) * t * exp(-x * x)
        return sign * y
    }

    companion object {
        private const val BASE_COVARIANCE_REGULARIZATION = 5e-5
        private const val RELATIVE_REGULARIZATION = 0.02
        private const val BASE_SHRINKAGE = 0.12
        private const val SAMPLE_RATIO_SHRINKAGE = 0.55
        private const val MIN_SHRINKAGE = 0.16
        private const val MAX_SHRINKAGE = 0.48
        private const val MAX_ABS_CORRELATION = 0.92
        private const val MEASUREMENT_UNCERTAINTY_SCALE = 2.5
        private const val DISTANCE_TO_SCORE_SCALE = 0.35
        private const val EXPONENTIAL_SCORE_WEIGHT = 0.60
        private const val CHI_SQUARE_SCORE_WEIGHT = 0.40
        private const val CHOLESKY_EPSILON = 1e-10
        private const val PIVOT_EPSILON = 1e-9
        private const val MIN_CONFIDENCE_WEIGHT = 0.35
        private const val ERF_P = 0.3275911
        private const val ERF_A1 = 0.254829592
        private const val ERF_A2 = -0.284496736
        private const val ERF_A3 = 1.421413741
        private const val ERF_A4 = -1.453152027
        private const val ERF_A5 = 1.061405429
    }
}
