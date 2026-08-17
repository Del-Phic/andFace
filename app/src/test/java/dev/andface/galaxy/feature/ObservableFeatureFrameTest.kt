package dev.andface.galaxy.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableFeatureFrameTest {
    @Test
    fun effectiveObservableCountSumsOnlyObservableVisibility() {
        val frame = ObservableFeatureFrame(
            evidence = listOf(
                FeatureEvidence(FeatureType.EyeDistance, value = 0.4, visibility = 1.0, observable = true),
                FeatureEvidence(FeatureType.BrowDistance, value = 0.1, visibility = 0.5, observable = true),
                FeatureEvidence(FeatureType.MouthWidth, value = 0.2, visibility = 1.0, observable = false),
                FeatureEvidence(FeatureType.NoseWidth, value = 0.3, visibility = -0.2, observable = true),
                FeatureEvidence(FeatureType.FaceAspect, value = 1.2, visibility = 1.4, observable = true)
            ),
            coverage = 0.0,
            observableCount = 4,
            occlusionSummary = "clean"
        )

        assertEquals(4, frame.observableEvidence.size)
        assertEquals(2.5, frame.effectiveObservableCount, 1e-12)
    }
}