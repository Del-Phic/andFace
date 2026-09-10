package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector
import dev.andface.galaxy.enrollment.EnrollmentBuilder
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpostorAcceptanceBoundaryTest {
    @Test
    fun singleEnrollmentKeepsGenuineScenariosAndRejectsNearImpostors() {
        val profile = EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE))
        println(
            "profile calibration mahalanobis=${profile.calibratedCleanMahalanobisFloor} " +
                "final=${profile.calibratedCleanFinalScoreFloor}"
        )

        val genuineScenarios = listOf(
            "clean" to (BASE_FACE to OcclusionHint()),
            "mask" to (BASE_FACE.copyOf().apply {
                this[FeatureType.NoseToChin.ordinal] += 0.10
                this[FeatureType.MouthWidth.ordinal] -= 0.13
                this[FeatureType.JawWidth.ordinal] += 0.36
                this[FeatureType.NoseToMouth.ordinal] += 0.08
                this[FeatureType.FaceAspect.ordinal] -= 0.16
            } to OcclusionHint(lowerFaceCovered = true)),
            "glasses" to (BASE_FACE.copyOf().apply {
                this[FeatureType.LeftEyeOpen.ordinal] *= 0.40
                this[FeatureType.RightEyeOpen.ordinal] *= 0.42
                this[FeatureType.BrowDistance.ordinal] += 0.006
            } to OcclusionHint(glasses = true)),
            "leftPatch" to (BASE_FACE.copyOf().apply {
                this[FeatureType.LeftEyeOpen.ordinal] = 0.02
            } to OcclusionHint(leftEyePatch = true))
        )
        for ((name, scenario) in genuineScenarios) {
            val engine = AuthenticationEngine().apply { setProfiles(listOf(profile)) }
            val results = liveFrames(scenario.first).map { frame -> engine.authenticate(frame, scenario.second) }
            val final = results.last()
            assertEquals("genuine $name should authenticate", AuthDecision.SUCCESS, final.decision)
            println(
                "genuine name=$name successes=${results.count { it.decision == AuthDecision.SUCCESS }}/${results.size} " +
                    "decision=${final.decision} reason=${final.failureReason} score=${final.finalScore} " +
                    "fuzzy=${final.fuzzyScore} mahalanobis=${final.mahalanobisScore} " +
                    "consistency=${final.identityConsistencyScore}/${final.requiredIdentityConsistencyScore} " +
                    "outlier=${final.identityOutlierScore}/${final.requiredIdentityOutlierScore} " +
                    "support=${final.identitySupportCount}/${final.requiredSupportCount} " +
                    "margin=${final.margin} stable=${final.stableFrameCount}/${final.requiredStableFrames} " +
                    "occlusion=${final.occlusionSummary}"
            )
        }

        for (impostorShare in listOf(0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60)) {
            val probe = blend(BASE_FACE, IMPOSTOR_FACE, impostorShare)
            val engine = AuthenticationEngine().apply { setProfiles(listOf(profile)) }
            val results = liveFrames(probe).map { frame -> engine.authenticate(frame, OcclusionHint()) }
            val successfulFrames = results.count { it.decision == AuthDecision.SUCCESS }
            val final = results.last()
            if (impostorShare <= 0.25) {
                assertTrue(
                    "near impostor share=$impostorShare must never authenticate: " +
                        "successes=$successfulFrames, reason=${final.failureReason}, score=${final.finalScore}, " +
                        "occlusion=${final.occlusionSummary}",
                    successfulFrames == 0
                )
            }
            println(
                "boundary share=$impostorShare successes=$successfulFrames/${results.size} " +
                    "decision=${final.decision} reason=${final.failureReason} score=${final.finalScore} " +
                    "fuzzy=${final.fuzzyScore} mahalanobis=${final.mahalanobisScore} " +
                    "consistency=${final.identityConsistencyScore}/${final.requiredIdentityConsistencyScore} " +
                    "outlier=${final.identityOutlierScore}/${final.requiredIdentityOutlierScore} " +
                    "support=${final.identitySupportCount}/${final.requiredSupportCount} " +
                    "margin=${final.margin} stable=${final.stableFrameCount}/${final.requiredStableFrames}"
            )
        }
    }

    @Test
    fun multipleEnrollmentsNeverLowerAbsoluteIdentityRequirements() {
        val profiles = listOf(
            EnrollmentBuilder.build("USER_1", enrollmentSamples(BASE_FACE)),
            EnrollmentBuilder.build("USER_2", enrollmentSamples(IMPOSTOR_FACE)),
            EnrollmentBuilder.build("USER_3", enrollmentSamples(OTHER_FACE))
        )

        for (impostorShare in listOf(0.15, 0.20, 0.25)) {
            val engine = AuthenticationEngine().apply { setProfiles(profiles) }
            val nearImpostor = blend(BASE_FACE, IMPOSTOR_FACE, impostorShare)
            val results = liveFrames(nearImpostor).map { frame -> engine.authenticate(frame, OcclusionHint()) }

            assertTrue(
                "profile count must not weaken open-set rejection for share=$impostorShare: " +
                    results.joinToString { "${it.decision}/${it.failureReason}/${it.finalScore}" },
                results.none { it.decision == AuthDecision.SUCCESS }
            )
        }
    }

    @Test
    fun cleanMaskGlassesAndCombinedModeKeepGenuineUsersAndRejectImpostors() {
        val enrolled = listOf(BASE_FACE, IMPOSTOR_FACE, OTHER_FACE)
        val profiles = enrolled.mapIndexed { index, base ->
            EnrollmentBuilder.build("USER_${index + 1}", enrollmentSamples(base))
        }
        val hints = listOf(OcclusionHint(), OcclusionHint(lowerFaceCovered = true),
            OcclusionHint(glasses = true), OcclusionHint(lowerFaceCovered = true, glasses = true))
        var genuinePasses = 0
        var impostorRejects = 0
        for (hint in hints) {
            for ((index, base) in enrolled.withIndex()) {
                val engine = AuthenticationEngine().apply { setProfiles(profiles) }
                val result = liveFrames(withOcclusion(base, hint)).map { engine.authenticate(it, hint) }.last()
                assertEquals("genuine user=${index + 1} hint=$hint reason=${result.failureReason} coverage=${result.coverage} obs=${result.observableCount} support=${result.identitySupportCount}",
                    AuthDecision.SUCCESS, result.decision)
                assertEquals("USER_${index + 1}", result.matchedUserId)
                genuinePasses++
            }
            for (share in listOf(0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.75, 0.80, 0.85)) {
                val probe = withOcclusion(blend(BASE_FACE, IMPOSTOR_FACE, share), hint)
                val engine = AuthenticationEngine().apply { setProfiles(profiles) }
                val results = liveFrames(probe).map { engine.authenticate(it, hint) }
                assertTrue("impostor share=$share hint=$hint results=${results.map { it.decision to it.finalScore }}",
                    results.none { it.decision == AuthDecision.SUCCESS })
                impostorRejects++
            }
        }
        println("clean/mask/glasses/mask+glasses synthetic matrix: genuine=$genuinePasses/12 impostor=$impostorRejects/44")
    }

    private fun withOcclusion(base: DoubleArray, hint: OcclusionHint): DoubleArray = base.copyOf().apply {
        if (hint.lowerFaceCovered) {
            this[FeatureType.NoseToChin.ordinal] += 0.10
            this[FeatureType.MouthWidth.ordinal] -= 0.13
            this[FeatureType.JawWidth.ordinal] += 0.36
            this[FeatureType.NoseToMouth.ordinal] += 0.08
            this[FeatureType.FaceAspect.ordinal] -= 0.16
        }
        if (hint.glasses) {
            this[FeatureType.LeftEyeOpen.ordinal] *= 0.40
            this[FeatureType.RightEyeOpen.ordinal] *= 0.42
            this[FeatureType.BrowDistance.ordinal] += 0.006
        }
    }

    private fun blend(genuine: DoubleArray, impostor: DoubleArray, impostorShare: Double): DoubleArray {
        return DoubleArray(genuine.size) { index ->
            genuine[index] * (1.0 - impostorShare) + impostor[index] * impostorShare
        }
    }

    private fun enrollmentSamples(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 45).map { index ->
            val delta = ((index % 5) - 2) * 0.001
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.EyeDistance.ordinal] += delta
                    this[FeatureType.BrowDistance.ordinal] -= delta * 0.5
                    this[FeatureType.NoseWidth.ordinal] += delta * 0.4
                    this[FeatureType.MouthWidth.ordinal] -= delta * 0.3
                    this[FeatureType.Yaw.ordinal] += delta * 0.6
                    this[FeatureType.Pitch.ordinal] -= delta * 0.4
                },
                timestampMs = index * 33L
            )
        }
    }

    private fun liveFrames(base: DoubleArray): List<RawFeatureFrame> {
        return (0 until 14).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val yawMotion = when (index % 5) {
                1 -> 0.040
                2 -> -0.040
                else -> 0.0
            }
            val pitchMotion = when (index % 5) {
                3 -> -0.034
                4 -> 0.034
                else -> 0.0
            }
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.014
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                },
                timestampMs = 3_000L + index * 33L
            )
        }
    }

    private companion object {
        val BASE_FACE = completeFeatureVector(
            0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.02, 0.03, 0.00,
            0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03,
            0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000
        )

        val IMPOSTOR_FACE = completeFeatureVector(
            0.58, 0.120, 0.30, 0.42, 0.52, 1.80, 0.08, 0.10, 0.29, 1.05, 0.10, -0.12, 0.05,
            0.27, 0.13, 0.14, 0.33, 0.20, 0.09, 0.34, 0.42, 0.39, 0.12,
            0.94, 0.88, 0.49, 0.43, 0.06, 0.120, 0.115, 0.005, 0.320, 0.300, 0.020
        )

        val OTHER_FACE = completeFeatureVector(
            0.39, 0.112, 0.31, 0.44, 0.27, 3.05, 0.11, 0.08, 0.26, 1.64, -0.08, 0.11, -0.07,
            0.30, 0.14, 0.12, 0.27, 0.22, 0.11, 0.36, 0.45, 0.37, 0.18,
            0.70, 0.66, 0.52, 0.36, 0.16, 0.104, 0.125, 0.021, 0.350, 0.290, 0.060
        )
    }
}
