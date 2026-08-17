package dev.andface.galaxy.auth

import dev.andface.galaxy.occlusion.OcclusionHint

class OcclusionStateStabilizer(
    private val requiredConfirmingFrames: Int = DEFAULT_REQUIRED_CONFIRMING_FRAMES
) {
    private var stableSummary: String = CLEAN_SUMMARY
    private var pendingSummary: String? = null
    private var pendingCount: Int = 0

    fun stabilize(rawSummary: String, hint: OcclusionHint): String {
        if (!hint.isClean) {
            accept(rawSummary)
            return rawSummary
        }

        val currentStable = stableSummary

        if (rawSummary == currentStable) {
            pendingSummary = null
            pendingCount = 0
            return currentStable
        }

        if (pendingSummary == rawSummary) {
            pendingCount += 1
        } else {
            pendingSummary = rawSummary
            pendingCount = 1
        }

        return if (pendingCount >= requiredConfirmingFrames) {
            accept(rawSummary)
            rawSummary
        } else {
            currentStable
        }
    }

    fun reset() {
        stableSummary = CLEAN_SUMMARY
        pendingSummary = null
        pendingCount = 0
    }

    private fun accept(summary: String) {
        stableSummary = summary
        pendingSummary = null
        pendingCount = 0
    }

    private companion object {
        private const val DEFAULT_REQUIRED_CONFIRMING_FRAMES = 8
        private const val CLEAN_SUMMARY = "clean"
    }
}
