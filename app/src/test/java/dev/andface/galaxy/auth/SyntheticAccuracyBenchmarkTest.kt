package dev.andface.galaxy.auth

import dev.andface.galaxy.completeFeatureVector

import dev.andface.galaxy.enrollment.EnrollmentBuilder
import dev.andface.galaxy.feature.FaceFrameQuality
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.occlusion.OcclusionHint
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticAccuracyBenchmarkTest {
    @Test
    fun syntheticCohortAccuracyStaysAboveEightyPercent() {
        val profiles = BASE_USERS.mapIndexed { index, base ->
            EnrollmentBuilder.build("USER_${index + 1}", enrollmentSamples(base, seed = index + 1))
        }
        val engine = AuthenticationEngine()
        engine.setProfiles(profiles)

        var correct = 0
        var total = 0
        var genuineCorrect = 0
        var genuineTotal = 0
        var impostorRejected = 0
        var impostorTotal = 0
        var falseAccepts = 0
        var falseRejects = 0

        for ((index, base) in BASE_USERS.withIndex()) {
            val userId = "USER_${index + 1}"
            for (scenario in GENUINE_SCENARIOS) {
                val result = runAttempt(engine, scenario.apply(base), scenario.hint)
                total += 1
                genuineTotal += 1
                if (result.decision == AuthDecision.SUCCESS && result.matchedUserId == userId) {
                    correct += 1
                    genuineCorrect += 1
                } else {
                    falseRejects += 1
                }
            }
        }

        for ((index, impostor) in IMPOSTORS.withIndex()) {
            val scenario = IMPOSTOR_SCENARIOS[index % IMPOSTOR_SCENARIOS.size]
            val result = runAttempt(engine, scenario.apply(impostor), scenario.hint)
            total += 1
            impostorTotal += 1
            if (result.decision == AuthDecision.FAILED) {
                correct += 1
                impostorRejected += 1
            } else {
                falseAccepts += 1
            }
        }

        for (impostor in ADVERSARIAL_MASKED_IMPOSTORS) {
            val result = runAttempt(engine, impostor, OcclusionHint(lowerFaceCovered = true))
            total += 1
            impostorTotal += 1
            if (result.decision == AuthDecision.FAILED) {
                correct += 1
                impostorRejected += 1
            } else {
                falseAccepts += 1
            }
        }

        val accuracy = correct.toDouble() / total.toDouble()
        val genuineAcceptanceRate = genuineCorrect.toDouble() / genuineTotal.toDouble()
        val impostorRejectionRate = impostorRejected.toDouble() / impostorTotal.toDouble()
        println(
            "synthetic cohort accuracy=$accuracy correct=$correct/$total " +
                "genuineAcceptanceRate=$genuineAcceptanceRate genuineCorrect=$genuineCorrect/$genuineTotal " +
                "impostorRejectionRate=$impostorRejectionRate impostorRejected=$impostorRejected/$impostorTotal " +
                "falseRejects=$falseRejects falseAccepts=$falseAccepts"
        )
        assertTrue(
            "expected synthetic accuracy >= 0.80 but was $accuracy; " +
                "correct=$correct/$total, falseRejects=$falseRejects, falseAccepts=$falseAccepts",
            accuracy >= 0.80
        )
        assertTrue(
            "expected genuine acceptance >= 0.80 but was $genuineAcceptanceRate; " +
                "genuineCorrect=$genuineCorrect/$genuineTotal",
            genuineAcceptanceRate >= 0.80
        )
        assertTrue(
            "impostors must be rejected in clean and occluded scenarios; falseAccepts=$falseAccepts",
            falseAccepts == 0
        )
    }

    @Test
    fun syntheticCohortAutoDetectedOcclusionStaysAboveEightyPercentWithoutManualHints() {
        val profiles = BASE_USERS.mapIndexed { index, base ->
            EnrollmentBuilder.build("USER_${index + 1}", enrollmentSamples(base, seed = index + 1))
        }
        val engine = AuthenticationEngine()
        engine.setProfiles(profiles)

        var correct = 0
        var total = 0
        var genuineCorrect = 0
        var genuineTotal = 0
        var impostorRejected = 0
        var impostorTotal = 0
        var falseAccepts = 0
        var falseRejects = 0

        for ((index, base) in BASE_USERS.withIndex()) {
            val userId = "USER_${index + 1}"
            for (scenario in AUTO_OCCLUSION_GENUINE_SCENARIOS) {
                val result = runAttempt(engine, scenario.apply(base), OcclusionHint())
                total += 1
                genuineTotal += 1
                val occlusionDetected = scenario.expectedOcclusionToken == null ||
                    result.occlusionSummary.contains(scenario.expectedOcclusionToken)
                if (result.decision == AuthDecision.SUCCESS && result.matchedUserId == userId && occlusionDetected) {
                    correct += 1
                    genuineCorrect += 1
                } else {
                    falseRejects += 1
                }
            }
        }

        for ((index, impostor) in IMPOSTORS.withIndex()) {
            val scenario = AUTO_OCCLUSION_IMPOSTOR_SCENARIOS[index % AUTO_OCCLUSION_IMPOSTOR_SCENARIOS.size]
            val result = runAttempt(engine, scenario.apply(impostor), OcclusionHint())
            total += 1
            impostorTotal += 1
            if (result.decision == AuthDecision.FAILED) {
                correct += 1
                impostorRejected += 1
            } else {
                falseAccepts += 1
            }
        }

        val accuracy = correct.toDouble() / total.toDouble()
        val genuineAcceptanceRate = genuineCorrect.toDouble() / genuineTotal.toDouble()
        val impostorRejectionRate = impostorRejected.toDouble() / impostorTotal.toDouble()
        println(
            "synthetic auto-occlusion cohort accuracy=$accuracy correct=$correct/$total " +
                "genuineAcceptanceRate=$genuineAcceptanceRate genuineCorrect=$genuineCorrect/$genuineTotal " +
                "impostorRejectionRate=$impostorRejectionRate impostorRejected=$impostorRejected/$impostorTotal " +
                "falseRejects=$falseRejects falseAccepts=$falseAccepts"
        )
        assertTrue(
            "expected auto-occlusion synthetic accuracy >= 0.80 but was $accuracy; " +
                "correct=$correct/$total, falseRejects=$falseRejects, falseAccepts=$falseAccepts",
            accuracy >= 0.80
        )
        assertTrue(
            "expected auto-occlusion genuine acceptance >= 0.80 but was $genuineAcceptanceRate; " +
                "genuineCorrect=$genuineCorrect/$genuineTotal",
            genuineAcceptanceRate >= 0.80
        )
        assertTrue(
            "auto-occlusion mode must not accept impostors; falseAccepts=$falseAccepts",
            falseAccepts == 0
        )
    }
    @Test
    fun syntheticCohortWith468OnlyEnrollmentStaysAboveEightyPercent() {
        val profiles = BASE_USERS.mapIndexed { index, base ->
            EnrollmentBuilder.build(
                "USER_${index + 1}",
                enrollmentSamples(base, seed = index + 1, irisAvailable = false)
            )
        }
        val engine = AuthenticationEngine()
        engine.setProfiles(profiles)

        var correct = 0
        var total = 0
        var genuineCorrect = 0
        var genuineTotal = 0
        var impostorRejected = 0
        var impostorTotal = 0
        var falseAccepts = 0
        var falseRejects = 0

        for ((index, base) in BASE_USERS.withIndex()) {
            val userId = "USER_${index + 1}"
            for (scenario in GENUINE_SCENARIOS) {
                for (currentIrisAvailable in listOf(false, true)) {
                    val result = runAttempt(engine, scenario.apply(base), scenario.hint, currentIrisAvailable)
                    total += 1
                    genuineTotal += 1
                    if (result.decision == AuthDecision.SUCCESS && result.matchedUserId == userId) {
                        correct += 1
                        genuineCorrect += 1
                    } else {
                        falseRejects += 1
                    }
                }
            }
        }

        for ((index, impostor) in IMPOSTORS.withIndex()) {
            val scenario = IMPOSTOR_SCENARIOS[index % IMPOSTOR_SCENARIOS.size]
            val result = runAttempt(
                engine,
                scenario.apply(impostor),
                scenario.hint,
                irisAvailable = index % 2 == 0
            )
            total += 1
            impostorTotal += 1
            if (result.decision == AuthDecision.FAILED) {
                correct += 1
                impostorRejected += 1
            } else {
                falseAccepts += 1
            }
        }

        for (impostor in ADVERSARIAL_MASKED_IMPOSTORS) {
            val result = runAttempt(engine, impostor, OcclusionHint(lowerFaceCovered = true), irisAvailable = false)
            total += 1
            impostorTotal += 1
            if (result.decision == AuthDecision.FAILED) {
                correct += 1
                impostorRejected += 1
            } else {
                falseAccepts += 1
            }
        }

        val accuracy = correct.toDouble() / total.toDouble()
        val genuineAcceptanceRate = genuineCorrect.toDouble() / genuineTotal.toDouble()
        val impostorRejectionRate = impostorRejected.toDouble() / impostorTotal.toDouble()
        println(
            "synthetic 468-only cohort accuracy=$accuracy correct=$correct/$total " +
                "genuineAcceptanceRate=$genuineAcceptanceRate genuineCorrect=$genuineCorrect/$genuineTotal " +
                "impostorRejectionRate=$impostorRejectionRate impostorRejected=$impostorRejected/$impostorTotal " +
                "falseRejects=$falseRejects falseAccepts=$falseAccepts"
        )
        assertTrue(
            "expected 468-only synthetic accuracy >= 0.80 but was $accuracy; " +
                "correct=$correct/$total, falseRejects=$falseRejects, falseAccepts=$falseAccepts",
            accuracy >= 0.80
        )
        assertTrue(
            "expected 468-only genuine acceptance >= 0.80 but was $genuineAcceptanceRate; " +
                "genuineCorrect=$genuineCorrect/$genuineTotal",
            genuineAcceptanceRate >= 0.80
        )
        assertTrue(
            "468-only enrolled profiles must not accept impostors; falseAccepts=$falseAccepts",
            falseAccepts == 0
        )
    }
    private fun runAttempt(
        engine: AuthenticationEngine,
        base: DoubleArray,
        hint: OcclusionHint,
        irisAvailable: Boolean = true
    ): AuthResult {
        engine.resetLiveSession()
        return liveFrames(base, irisAvailable).map { frame -> engine.authenticate(frame, hint) }.last()
    }

    private fun enrollmentSamples(
        base: DoubleArray,
        seed: Int,
        irisAvailable: Boolean = true
    ): List<RawFeatureFrame> {
        return (0 until 45).map { index ->
            val wave = if ((index + seed) % 2 == 0) 1.0 else -1.0
            val small = (((index + seed) % 5) - 2) * 0.001
            RawFeatureFrame(
                values = base.copyOf().apply {
                    this[FeatureType.EyeDistance.ordinal] += small
                    this[FeatureType.BrowDistance.ordinal] -= small * 0.5
                    this[FeatureType.NoseWidth.ordinal] += small * 0.4
                    this[FeatureType.NoseToChin.ordinal] -= small * 0.3
                    this[FeatureType.MouthWidth.ordinal] += small * 0.2
                    this[FeatureType.JawWidth.ordinal] -= small * 0.8
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.006
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.005
                    this[FeatureType.Yaw.ordinal] += wave * 0.012
                    this[FeatureType.Pitch.ordinal] -= wave * 0.010
                },
                timestampMs = index * 33L,
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = irisAvailable)
            )
        }
    }

    private fun liveFrames(base: DoubleArray, irisAvailable: Boolean = true): List<RawFeatureFrame> {
        return (0 until 14).map { index ->
            val wave = if (index % 2 == 0) 1.0 else -1.0
            val drift = (index - 7) * 0.0005
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
                    this[FeatureType.EyeDistance.ordinal] += drift
                    this[FeatureType.BrowDistance.ordinal] -= drift * 0.4
                    this[FeatureType.NoseWidth.ordinal] += drift * 0.3
                    this[FeatureType.LeftEyeOpen.ordinal] += wave * 0.016
                    this[FeatureType.RightEyeOpen.ordinal] -= wave * 0.014
                    this[FeatureType.Yaw.ordinal] += yawMotion
                    this[FeatureType.Pitch.ordinal] += pitchMotion
                },
                timestampMs = 3000L + index * 33L,
                quality = FaceFrameQuality.TRUSTED.copy(irisLandmarksAvailable = irisAvailable)
            )
        }
    }

    private data class Scenario(
        val name: String,
        val hint: OcclusionHint,
        val expectedOcclusionToken: String? = null,
        val transform: (DoubleArray) -> DoubleArray
    ) {
        fun apply(base: DoubleArray): DoubleArray = transform(base.copyOf())
    }

    private companion object {
        val GENUINE_SCENARIOS = listOf(
            Scenario("clean", OcclusionHint()) { it },
            Scenario("mask", OcclusionHint(lowerFaceCovered = true)) {
                it.apply {
                    this[FeatureType.NoseToChin.ordinal] += 0.10
                    this[FeatureType.MouthWidth.ordinal] -= 0.13
                    this[FeatureType.JawWidth.ordinal] += 0.36
                    this[FeatureType.NoseToMouth.ordinal] += 0.08
                    this[FeatureType.FaceAspect.ordinal] -= 0.16
                }
            },
            Scenario("glasses", OcclusionHint(glasses = true)) {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] *= 0.40
                    this[FeatureType.RightEyeOpen.ordinal] *= 0.42
                    this[FeatureType.BrowDistance.ordinal] += 0.006
                }
            },
            Scenario("leftPatch", OcclusionHint(leftEyePatch = true)) {
                it.apply { this[FeatureType.LeftEyeOpen.ordinal] = 0.02 }
            },
            Scenario("rightPatch", OcclusionHint(rightEyePatch = true)) {
                it.apply { this[FeatureType.RightEyeOpen.ordinal] = 0.02 }
            }
        )

        val AUTO_OCCLUSION_GENUINE_SCENARIOS = listOf(
            Scenario("autoMask", OcclusionHint(), expectedOcclusionToken = "lower") {
                it.apply {
                    this[FeatureType.NoseToChin.ordinal] += 0.10
                    this[FeatureType.MouthWidth.ordinal] -= 0.13
                    this[FeatureType.JawWidth.ordinal] += 0.36
                    this[FeatureType.NoseToMouth.ordinal] += 0.08
                    this[FeatureType.FaceAspect.ordinal] -= 0.16
                }
            },
            Scenario("autoGlasses", OcclusionHint(), expectedOcclusionToken = "glasses") {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] *= 0.35
                    this[FeatureType.RightEyeOpen.ordinal] *= 0.36
                    this[FeatureType.LeftEyeHeight.ordinal] += 0.030
                    this[FeatureType.RightEyeHeight.ordinal] += 0.030
                    this[FeatureType.LeftEyeWidth.ordinal] += 0.030
                    this[FeatureType.RightEyeWidth.ordinal] += 0.030
                }
            },
            Scenario("autoLeftPatch", OcclusionHint(), expectedOcclusionToken = "left_eye") {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] = 0.02
                    this[FeatureType.LeftEyeHeight.ordinal] += 0.09
                    this[FeatureType.LeftEyeWidth.ordinal] += 0.09
                    this[FeatureType.LeftIrisEyeOffset.ordinal] += 0.09
                }
            },
            Scenario("autoRightPatch", OcclusionHint(), expectedOcclusionToken = "right_eye") {
                it.apply {
                    this[FeatureType.RightEyeOpen.ordinal] = 0.02
                    this[FeatureType.RightEyeHeight.ordinal] += 0.09
                    this[FeatureType.RightEyeWidth.ordinal] += 0.09
                    this[FeatureType.RightIrisEyeOffset.ordinal] += 0.09
                }
            }
        )

        val AUTO_OCCLUSION_IMPOSTOR_SCENARIOS = listOf(
            Scenario("autoClean", OcclusionHint()) { it },
            Scenario("autoMask", OcclusionHint(), expectedOcclusionToken = "lower") {
                it.apply {
                    this[FeatureType.NoseToChin.ordinal] += 0.10
                    this[FeatureType.MouthWidth.ordinal] -= 0.13
                    this[FeatureType.JawWidth.ordinal] += 0.36
                    this[FeatureType.NoseToMouth.ordinal] += 0.08
                    this[FeatureType.FaceAspect.ordinal] -= 0.16
                }
            },
            Scenario("autoGlasses", OcclusionHint(), expectedOcclusionToken = "glasses") {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] *= 0.35
                    this[FeatureType.RightEyeOpen.ordinal] *= 0.36
                    this[FeatureType.LeftEyeHeight.ordinal] += 0.030
                    this[FeatureType.RightEyeHeight.ordinal] += 0.030
                    this[FeatureType.LeftEyeWidth.ordinal] += 0.030
                    this[FeatureType.RightEyeWidth.ordinal] += 0.030
                }
            },
            Scenario("autoLeftPatch", OcclusionHint(), expectedOcclusionToken = "left_eye") {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] = 0.02
                    this[FeatureType.LeftEyeHeight.ordinal] += 0.09
                    this[FeatureType.LeftEyeWidth.ordinal] += 0.09
                    this[FeatureType.LeftIrisEyeOffset.ordinal] += 0.09
                }
            },
            Scenario("autoRightPatch", OcclusionHint(), expectedOcclusionToken = "right_eye") {
                it.apply {
                    this[FeatureType.RightEyeOpen.ordinal] = 0.02
                    this[FeatureType.RightEyeHeight.ordinal] += 0.09
                    this[FeatureType.RightEyeWidth.ordinal] += 0.09
                    this[FeatureType.RightIrisEyeOffset.ordinal] += 0.09
                }
            }
        )
        val IMPOSTOR_SCENARIOS = listOf(
            Scenario("clean", OcclusionHint()) { it },
            Scenario("mask", OcclusionHint(lowerFaceCovered = true)) {
                it.apply {
                    this[FeatureType.MouthWidth.ordinal] *= 0.75
                    this[FeatureType.NoseToMouth.ordinal] *= 1.35
                }
            },
            Scenario("glasses", OcclusionHint(glasses = true)) {
                it.apply {
                    this[FeatureType.LeftEyeOpen.ordinal] *= 0.48
                    this[FeatureType.RightEyeOpen.ordinal] *= 0.50
                }
            },
            Scenario("leftPatch", OcclusionHint(leftEyePatch = true)) {
                it.apply { this[FeatureType.LeftEyeOpen.ordinal] = 0.02 }
            },
            Scenario("rightPatch", OcclusionHint(rightEyePatch = true)) {
                it.apply { this[FeatureType.RightEyeOpen.ordinal] = 0.02 }
            },
            Scenario("maskWithLeftPatch", OcclusionHint(lowerFaceCovered = true)) {
                it.apply {
                    this[FeatureType.NoseToChin.ordinal] += 0.10
                    this[FeatureType.MouthWidth.ordinal] -= 0.13
                    this[FeatureType.JawWidth.ordinal] += 0.36
                    this[FeatureType.NoseToMouth.ordinal] += 0.08
                    this[FeatureType.FaceAspect.ordinal] -= 0.16
                    this[FeatureType.LeftEyeOpen.ordinal] = 0.02
                }
            }
        )

        val BASE_USERS = listOf(
            completeFeatureVector(0.48, 0.075, 0.22, 0.52, 0.38, 2.30, 0.16, 0.16, 0.18, 1.28, 0.02, 0.03, 0.00, 0.18, 0.20, 0.19, 0.06, 0.12, 0.02, 0.22, 0.28, 0.27, 0.03, 0.82, 0.78, 0.36, 0.35, 0.01, 0.067, 0.067, 0.000, 0.269, 0.269, 0.000),
            completeFeatureVector(0.54, 0.092, 0.25, 0.48, 0.34, 2.05, 0.13, 0.14, 0.21, 1.16, -0.01, 0.01, 0.02, 0.22, 0.18, 0.17, 0.04, 0.10, 0.015, 0.18, 0.25, 0.26, 0.02, 0.86, 0.82, 0.39, 0.40, 0.01, 0.082, 0.086, 0.004, 0.255, 0.265, 0.010),
            completeFeatureVector(0.43, 0.065, 0.19, 0.57, 0.43, 2.58, 0.18, 0.17, 0.16, 1.38, 0.03, -0.02, -0.01, 0.15, 0.22, 0.21, 0.05, 0.14, 0.025, 0.24, 0.30, 0.29, 0.025, 0.78, 0.74, 0.33, 0.34, 0.01, 0.060, 0.062, 0.002, 0.285, 0.275, 0.010)
        )

        val ADVERSARIAL_MASKED_IMPOSTORS = BASE_USERS.map { base ->
            base.copyOf().apply {
                this[FeatureType.NoseToChin.ordinal] += 0.10
                this[FeatureType.MouthWidth.ordinal] -= 0.13
                this[FeatureType.JawWidth.ordinal] += 0.36
                this[FeatureType.NoseToMouth.ordinal] += 0.08
                this[FeatureType.FaceAspect.ordinal] -= 0.16
                this[FeatureType.InterBrowDistance.ordinal] += 0.16
                this[FeatureType.LeftBrowNoseRoot.ordinal] += 0.12
                this[FeatureType.RightBrowNoseRoot.ordinal] -= 0.10
                this[FeatureType.LeftInnerEyeNoseRoot.ordinal] += 0.08
                this[FeatureType.RightInnerEyeNoseRoot.ordinal] -= 0.06
                this[FeatureType.LeftTempleEye.ordinal] += 0.12
                this[FeatureType.RightTempleEye.ordinal] -= 0.08
                this[FeatureType.EyeWidthAsymmetry.ordinal] += 0.42
                this[FeatureType.LeftBrowSlope.ordinal] += 0.26
                this[FeatureType.RightBrowSlope.ordinal] += 0.22
                this[FeatureType.BrowSlopeAsymmetry.ordinal] += 0.20
            }
        }

        val IMPOSTORS = listOf(
            completeFeatureVector(0.58, 0.120, 0.30, 0.42, 0.52, 1.80, 0.08, 0.10, 0.29, 1.05, 0.10, -0.12, 0.05, 0.27, 0.13, 0.14, 0.33, 0.20, 0.09, 0.34, 0.42, 0.39, 0.12, 0.94, 0.88, 0.49, 0.43, 0.06, 0.120, 0.115, 0.005, 0.320, 0.300, 0.020),
            completeFeatureVector(0.39, 0.112, 0.31, 0.44, 0.27, 3.05, 0.11, 0.08, 0.26, 1.64, -0.08, 0.11, -0.07, 0.30, 0.14, 0.12, 0.27, 0.22, 0.11, 0.36, 0.45, 0.37, 0.18, 0.70, 0.66, 0.52, 0.36, 0.16, 0.104, 0.125, 0.021, 0.350, 0.290, 0.060),
            completeFeatureVector(0.62, 0.052, 0.17, 0.64, 0.51, 1.65, 0.23, 0.20, 0.10, 0.94, 0.14, 0.08, 0.04, 0.11, 0.27, 0.24, 0.18, 0.08, 0.07, 0.12, 0.19, 0.23, 0.08, 0.98, 0.90, 0.28, 0.51, 0.23, 0.050, 0.075, 0.025, 0.240, 0.360, 0.120),
            completeFeatureVector(0.45, 0.130, 0.34, 0.37, 0.49, 2.02, 0.07, 0.09, 0.31, 1.09, -0.12, -0.10, 0.08, 0.28, 0.12, 0.16, 0.35, 0.21, 0.10, 0.31, 0.43, 0.35, 0.14, 0.72, 0.92, 0.55, 0.40, 0.15, 0.135, 0.115, 0.020, 0.370, 0.310, 0.060),
            completeFeatureVector(0.56, 0.070, 0.18, 0.62, 0.29, 2.75, 0.21, 0.19, 0.13, 1.48, 0.07, 0.14, -0.05, 0.13, 0.25, 0.22, 0.12, 0.16, 0.05, 0.27, 0.18, 0.34, 0.16, 0.88, 0.69, 0.31, 0.48, 0.17, 0.064, 0.095, 0.031, 0.270, 0.345, 0.075),
            completeFeatureVector(0.41, 0.100, 0.28, 0.46, 0.55, 1.92, 0.10, 0.12, 0.25, 1.00, -0.15, 0.04, 0.10, 0.25, 0.15, 0.13, 0.29, 0.19, 0.06, 0.32, 0.40, 0.33, 0.10, 0.76, 0.84, 0.46, 0.31, 0.15, 0.090, 0.126, 0.036, 0.355, 0.270, 0.085)
        )
    }
}
