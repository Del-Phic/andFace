package dev.andface.galaxy.auth

class TemporalDecisionGate {
    private var lastUserId: String? = null
    private var lastOcclusionSummary: String = "clean"
    private var stableFrames: Int = 0

    fun evaluate(
        userId: String?,
        occlusionSummary: String,
        failureReason: FailureReason
    ): TemporalDecision {
        val requiredFrames = requiredStableFrames(occlusionSummary)
        if (userId == null) {
            reset()
            return TemporalDecision(stableFrames = 0, requiredFrames = requiredFrames, passed = false)
        }

        if (failureReason != FailureReason.NONE) {
            reset()
            return TemporalDecision(stableFrames = 0, requiredFrames = requiredFrames, passed = false)
        }

        if (userId == lastUserId && occlusionSummary == lastOcclusionSummary) {
            stableFrames += 1
        } else {
            lastUserId = userId
            lastOcclusionSummary = occlusionSummary
            stableFrames = 1
        }
        return TemporalDecision(
            stableFrames = stableFrames,
            requiredFrames = requiredFrames,
            passed = stableFrames >= requiredFrames
        )
    }

    fun reset() {
        lastUserId = null
        lastOcclusionSummary = "clean"
        stableFrames = 0
    }

    private fun requiredStableFrames(occlusionSummary: String): Int {
        return if (occlusionSummary == "clean") CLEAN_REQUIRED_STABLE_FRAMES else OCCLUDED_REQUIRED_STABLE_FRAMES
    }

    companion object {
        private const val CLEAN_REQUIRED_STABLE_FRAMES = 5
        private const val OCCLUDED_REQUIRED_STABLE_FRAMES = 5
    }
}

data class TemporalDecision(
    val stableFrames: Int,
    val requiredFrames: Int,
    val passed: Boolean,
    val graceFrame: Boolean = false
)
