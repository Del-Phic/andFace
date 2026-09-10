package dev.andface.galaxy.auth

import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

data class LivenessResult(
    val score: Double,
    val passed: Boolean,
    val frameCount: Int,
    val challengeLabel: String?,
    val challengePassed: Boolean,
    val passivePassed: Boolean
)

class LivenessTracker {
    private val frames = ArrayDeque<RawFeatureFrame>()
    private var challenge: LivenessChallenge? = null
    private var anchorYaw: Double = 0.0
    private var anchorPitch: Double = 0.0

    fun add(frame: RawFeatureFrame, hint: OcclusionHint = OcclusionHint()): LivenessResult {
        val previousTimestamp = frames.lastOrNull()?.timestampMs
        if (previousTimestamp != null &&
            (frame.timestampMs <= previousTimestamp || frame.timestampMs - previousTimestamp > MAX_FRAME_GAP_MS)
        ) reset()
        if (frames.isEmpty()) {
            startChallenge(frame)
        }
        frames.addLast(frame)
        while (frames.size > MAX_FRAMES ||
            frame.timestampMs - frames.first().timestampMs > MAX_WINDOW_DURATION_MS
        ) {
            frames.removeFirst()
        }
        return evaluate(hint)
    }

    fun reset() {
        frames.clear()
        challenge = null
        anchorYaw = 0.0
        anchorPitch = 0.0
    }

    fun evaluate(hint: OcclusionHint = OcclusionHint()): LivenessResult {
        if (frames.size < MIN_FRAMES) {
            return LivenessResult(
                score = 0.0,
                passed = false,
                frameCount = frames.size,
                challengeLabel = challenge?.label,
                challengePassed = false,
                passivePassed = false
            )
        }

        val activeEyeTypes = buildList {
            if (!hint.leftEyePatch) add(FeatureType.LeftEyeOpen)
            if (!hint.rightEyePatch) add(FeatureType.RightEyeOpen)
        }
        val eyeVariance = activeEyeTypes.sumOf { variance(it) }
        val poseVariance = variance(FeatureType.Yaw) + variance(FeatureType.Pitch)
        val blendshapeAvailable = frames.any { it.quality.blendshapesAvailable }
        val detailedBlendshapeAvailable = frames.any { frame ->
            frame.quality.blendshapeLeftEyeActivityScore > 0.0 ||
                frame.quality.blendshapeRightEyeActivityScore > 0.0 ||
                frame.quality.blendshapeBrowActivityScore > 0.0 ||
                frame.quality.blendshapeMouthActivityScore > 0.0
        }
        val aggregateBlendshapeVariance = if (blendshapeAvailable) {
            variance(frames.map { it.quality.blendshapeActivityScore })
        } else {
            0.0
        }
        val detailedBlendshapeVariance = if (detailedBlendshapeAvailable) {
            detailedVisibleBlendshapeVariance(hint)
        } else {
            0.0
        }
        val blendshapeVariance = if (detailedBlendshapeAvailable) {
            detailedBlendshapeVariance
        } else {
            aggregateBlendshapeVariance
        }
        val eyeTarget = EYE_VARIANCE_TARGET * max(activeEyeTypes.size.toDouble() / 2.0, MIN_EYE_TARGET_RATIO)
        val eyeScore = if (activeEyeTypes.isEmpty()) 0.0 else eyeVariance / eyeTarget
        val poseScore = poseVariance / POSE_VARIANCE_TARGET
        val blendshapeScore = blendshapeVariance / BLENDSHAPE_VARIANCE_TARGET
        val baseScore = if (activeEyeTypes.isEmpty()) poseScore else EYE_SCORE_WEIGHT * eyeScore + POSE_SCORE_WEIGHT * poseScore
        val score = min(1.0, baseScore + if (blendshapeAvailable) BLENDSHAPE_SCORE_WEIGHT * blendshapeScore else 0.0)
        val threshold = if (hint.isClean) MIN_LIVENESS_SCORE else MIN_OCCLUDED_LIVENESS_SCORE
        val challengePassed = hasCompletedChallenge()
        val passivePassed = hasStrongPassiveLiveness(
            score = score,
            eyeScore = eyeScore,
            poseScore = poseScore,
            blendshapeScore = blendshapeScore,
            blendshapeAvailable = blendshapeAvailable,
            activeEyeCount = activeEyeTypes.size,
            hint = hint
        )
        return LivenessResult(
            score = score,
            passed = score >= threshold && (challengePassed || passivePassed),
            frameCount = frames.size,
            challengeLabel = challenge?.label,
            challengePassed = challengePassed,
            passivePassed = passivePassed
        )
    }

    private fun hasStrongPassiveLiveness(
        score: Double,
        eyeScore: Double,
        poseScore: Double,
        blendshapeScore: Double,
        blendshapeAvailable: Boolean,
        activeEyeCount: Int,
        hint: OcclusionHint
    ): Boolean {
        if (frames.size < MIN_STRONG_PASSIVE_FRAMES) return false
        if (passiveTimeSpanMs() < MIN_PASSIVE_TIME_SPAN_MS) return false
        val requiredScore = if (hint.isClean) MIN_STRONG_PASSIVE_SCORE else MIN_OCCLUDED_STRONG_PASSIVE_SCORE
        if (score < requiredScore) return false

        val blendshapeCanSupport = blendshapeAvailable && blendshapeScore >= if (hint.isClean) {
            MIN_PASSIVE_BLENDSHAPE_SCORE
        } else {
            MIN_OCCLUDED_PASSIVE_BLENDSHAPE_SCORE
        }

        if (activeEyeCount == 0) {
            val poseOnlyRequired = if (hint.isClean) MIN_PASSIVE_POSE_ONLY_SCORE else MIN_OCCLUDED_PASSIVE_POSE_ONLY_SCORE
            return poseScore >= poseOnlyRequired ||
                (blendshapeCanSupport && poseScore >= poseOnlyRequired * MIN_POSE_WITH_BLENDSHAPE_RATIO)
        }

        return if (hint.isClean) {
            eyeScore >= MIN_PASSIVE_EYE_SCORE &&
                (poseScore >= MIN_PASSIVE_POSE_SCORE || blendshapeCanSupport)
        } else {
            eyeScore >= MIN_OCCLUDED_PASSIVE_EYE_SCORE &&
                (poseScore >= MIN_OCCLUDED_PASSIVE_POSE_SCORE || blendshapeCanSupport)
        }
    }

    private fun startChallenge(frame: RawFeatureFrame) {
        challenge = LivenessChallenge.entries[random.nextInt(LivenessChallenge.entries.size)]
        anchorYaw = frame.value(FeatureType.Yaw)
        anchorPitch = frame.value(FeatureType.Pitch)
    }

    private fun hasCompletedChallenge(): Boolean {
        val currentChallenge = challenge ?: return false
        val yawValues = frames.map { it.value(FeatureType.Yaw) }
        val pitchValues = frames.map { it.value(FeatureType.Pitch) }
        return when (currentChallenge) {
            LivenessChallenge.LOOK_LEFT -> yawValues.maxOrNull().orZero() - anchorYaw >= YAW_CHALLENGE_DELTA
            LivenessChallenge.LOOK_RIGHT -> anchorYaw - yawValues.minOrNull().orZero() >= YAW_CHALLENGE_DELTA
            LivenessChallenge.LOOK_UP -> anchorPitch - pitchValues.minOrNull().orZero() >= PITCH_CHALLENGE_DELTA
            LivenessChallenge.LOOK_DOWN -> pitchValues.maxOrNull().orZero() - anchorPitch >= PITCH_CHALLENGE_DELTA
        }
    }

    private fun passiveTimeSpanMs(): Long {
        val first = frames.firstOrNull()?.timestampMs ?: return 0L
        val last = frames.lastOrNull()?.timestampMs ?: return 0L
        return max(0L, last - first)
    }

    private fun detailedVisibleBlendshapeVariance(hint: OcclusionHint): Double {
        val leftEyeVariance = if (!hint.leftEyePatch) {
            variance(frames.map { it.quality.blendshapeLeftEyeActivityScore })
        } else {
            0.0
        }
        val rightEyeVariance = if (!hint.rightEyePatch) {
            variance(frames.map { it.quality.blendshapeRightEyeActivityScore })
        } else {
            0.0
        }
        val browVariance = variance(frames.map { it.quality.blendshapeBrowActivityScore })
        val mouthVariance = if (!hint.lowerFaceCovered) {
            variance(frames.map { it.quality.blendshapeMouthActivityScore })
        } else {
            0.0
        }
        return max(max(leftEyeVariance + rightEyeVariance, browVariance), mouthVariance)
    }

    private fun variance(type: FeatureType): Double {
        return variance(frames.map { it.value(type) })
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / values.size.toDouble()
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private enum class LivenessChallenge(val label: String) {
        LOOK_LEFT("\uC67C\uCABD \uBCF4\uAE30"),
        LOOK_RIGHT("\uC624\uB978\uCABD \uBCF4\uAE30"),
        LOOK_UP("\uC704 \uBCF4\uAE30"),
        LOOK_DOWN("\uC544\uB798 \uBCF4\uAE30")
    }

    companion object {
        private val random = SecureRandom()
        private const val MAX_FRAMES = 36
        private const val MAX_FRAME_GAP_MS = 1_500L
        private const val MAX_WINDOW_DURATION_MS = 4_000L
        private const val MIN_FRAMES = 8
        private const val MIN_STRONG_PASSIVE_FRAMES = 12
        private const val MIN_PASSIVE_TIME_SPAN_MS = 330L
        private const val YAW_CHALLENGE_DELTA = 0.030
        private const val PITCH_CHALLENGE_DELTA = 0.026
        private const val EYE_VARIANCE_TARGET = 0.0018
        private const val POSE_VARIANCE_TARGET = 0.0040
        private const val BLENDSHAPE_VARIANCE_TARGET = 0.0100
        private const val EYE_SCORE_WEIGHT = 0.55
        private const val POSE_SCORE_WEIGHT = 0.45
        private const val BLENDSHAPE_SCORE_WEIGHT = 0.12
        private const val MIN_EYE_TARGET_RATIO = 0.5
        private const val MIN_LIVENESS_SCORE = 0.12
        private const val MIN_OCCLUDED_LIVENESS_SCORE = 0.08
        private const val MIN_STRONG_PASSIVE_SCORE = 0.48
        private const val MIN_OCCLUDED_STRONG_PASSIVE_SCORE = 0.12
        private const val MIN_PASSIVE_EYE_SCORE = 0.20
        private const val MIN_PASSIVE_POSE_SCORE = 0.20
        private const val MIN_PASSIVE_POSE_ONLY_SCORE = 0.60
        private const val MIN_OCCLUDED_PASSIVE_EYE_SCORE = 0.10
        private const val MIN_OCCLUDED_PASSIVE_POSE_SCORE = 0.04
        private const val MIN_OCCLUDED_PASSIVE_POSE_ONLY_SCORE = 0.28
        private const val MIN_PASSIVE_BLENDSHAPE_SCORE = 0.35
        private const val MIN_OCCLUDED_PASSIVE_BLENDSHAPE_SCORE = 0.20
        private const val MIN_POSE_WITH_BLENDSHAPE_RATIO = 0.60
    }
}
