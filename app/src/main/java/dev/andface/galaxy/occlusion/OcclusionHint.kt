package dev.andface.galaxy.occlusion

data class OcclusionHint(
    val lowerFaceCovered: Boolean = false,
    val glasses: Boolean = false,
    val leftEyePatch: Boolean = false,
    val rightEyePatch: Boolean = false
) {
    val isClean: Boolean
        get() = !lowerFaceCovered && !glasses && !leftEyePatch && !rightEyePatch
}
