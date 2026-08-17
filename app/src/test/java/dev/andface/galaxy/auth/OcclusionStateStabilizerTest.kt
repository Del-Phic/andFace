package dev.andface.galaxy.auth

import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Test

class OcclusionStateStabilizerTest {
    @Test
    fun automaticSingleFrameOcclusionFlickerKeepsStableSummary() {
        val stabilizer = OcclusionStateStabilizer()

        assertEquals("clean", stabilizer.stabilize("clean", OcclusionHint()))
        repeat(7) {
            assertEquals("clean", stabilizer.stabilize("lower", OcclusionHint()))
        }
        assertEquals("lower", stabilizer.stabilize("lower", OcclusionHint()))
    }

    @Test
    fun explicitHintSwitchesImmediately() {
        val stabilizer = OcclusionStateStabilizer()

        assertEquals("clean", stabilizer.stabilize("clean", OcclusionHint()))
        assertEquals("lower", stabilizer.stabilize("lower", OcclusionHint(lowerFaceCovered = true)))
    }

    @Test
    fun automaticHighRiskOcclusionAlsoRequiresConfirmation() {
        val stabilizer = OcclusionStateStabilizer()

        assertEquals("clean", stabilizer.stabilize("clean", OcclusionHint()))
        repeat(7) {
            assertEquals("clean", stabilizer.stabilize("lower+left_eye", OcclusionHint()))
        }
        assertEquals("lower+left_eye", stabilizer.stabilize("lower+left_eye", OcclusionHint()))
    }

    @Test
    fun resetClearsPreviousStableSummary() {
        val stabilizer = OcclusionStateStabilizer()

        assertEquals("clean", stabilizer.stabilize("clean", OcclusionHint()))
        assertEquals("clean", stabilizer.stabilize("lower", OcclusionHint()))
        stabilizer.reset()

        assertEquals("clean", stabilizer.stabilize("lower", OcclusionHint()))
    }
}
