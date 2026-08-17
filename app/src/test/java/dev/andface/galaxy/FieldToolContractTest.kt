package dev.andface.galaxy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FieldToolContractTest {
    @Test
    fun fieldSummaryRequiresCommercialDemoAcceptanceGate() {
        val script = readProjectFile("tools/Summarize-FieldTestResults.ps1")

        assertTrue(script.contains("[double]\$MinimumAccuracy = 0.80"))
        assertTrue(script.contains("[double]\$MinimumGenuineAcceptance = 0.80"))
        assertTrue(script.contains("[int]\$MaximumFalseAccepts = 0"))
        assertTrue(script.contains("[int]\$MinimumFilledTrials = 30"))
        assertTrue(script.contains("[int]\$MinimumTrialsPerRequiredScenario = 3"))
        assertTrue(script.contains("[int]\$MinimumDistinctGenuineUsers = 3"))
        assertTrue(script.contains("[string]\$ExpectedApkSha256"))
        assertTrue(script.contains("[int]\$ExpectedFeatureCount = 170"))
        assertTrue(script.contains("[string[]]\$RequiredMeasurementColumns"))
        assertTrue(script.contains("[string[]]\$RequiredNumericColumns"))
        assertTrue(script.contains("face_quality"))
        assertTrue(script.contains("mesh_symmetry"))
        assertTrue(script.contains("landmark_topology"))
        assertTrue(script.contains("Avg Face Quality"))
        assertTrue(script.contains("Avg Mesh Symmetry"))
        assertTrue(script.contains("Avg Landmark Topology"))
        assertTrue(script.contains("Face Quality"))
        assertTrue(script.contains("Landmark Topology"))
        assertTrue(script.contains("APK hash matches expected build"))
        assertTrue(script.contains("Feature count matches expected vector"))
        assertTrue(script.contains("Required live metric columns are filled"))
        assertTrue(script.contains("Numeric live metric columns parse"))
        assertTrue(script.contains("\$RequiredScenarios = @("))
        assertTrue(script.contains("genuine_mask"))
        assertTrue(script.contains("genuine_auto_mask"))
        assertTrue(script.contains("genuine_left_patch"))
        assertTrue(script.contains("genuine_right_patch"))
        assertTrue(script.contains("impostor_mask"))
        assertTrue(script.contains("impostor_auto_mask"))
        assertTrue(script.contains("static_spoof"))
        assertTrue(script.contains("FIELD_TEST_GATE_PASSED"))
        assertTrue(script.contains("FIELD_TEST_GATE_FAILED"))
        assertTrue(script.contains("Minimum trials per required scenario"))
        assertTrue(script.contains("Minimum distinct enrolled users"))
        assertTrue(script.contains("Scenario-specific genuine acceptance"))
        assertTrue(script.contains("genuine_clean = \$MinimumGenuineAcceptance"))
        assertTrue(script.contains("genuine_glasses = \$MinimumGenuineAcceptance"))
        assertTrue(script.contains("Quality Diagnostic Flags"))
        assertTrue(script.contains("Actionable Calibration Guidance"))
        assertTrue(script.contains("CRITICAL: false accepts were observed"))
        assertTrue(script.contains("Camera/MediaPipe quality warning"))
        assertTrue(script.contains("Liveness warning"))
        assertTrue(script.contains("Identity scoring warning"))
        assertTrue(script.contains("Per-user genuine scenario acceptance"))
        assertTrue(script.contains("Per-User Scenario Breakdown"))
        assertTrue(script.contains("Group-Object EnrolledUser, Scenario"))
        assertTrue(script.contains("Scenario false accept limit"))
        assertTrue(script.contains("Occlusion display check"))
        assertTrue(script.contains("genuine_mask = \$MinimumGenuineAcceptance"))
        assertTrue(script.contains("genuine_auto_mask = \$MinimumGenuineAcceptance"))
        assertTrue(script.contains("impostor_mask = \"lower\""))
        assertTrue(script.contains("impostor_auto_mask = \"lower\""))
        assertTrue(script.contains("genuine_left_patch = \"left_eye\""))
        assertTrue(script.contains("genuine_right_patch = \"right_eye\""))
        assertTrue(script.contains("exit 1"))
    }

    @Test
    fun fieldSummaryNormalizesKoreanFailureReasonsCopiedFromApp() {
        val script = readProjectFile("tools/Summarize-FieldTestResults.ps1")

        assertTrue(script.contains("function Normalize-FailureReason"))
        assertTrue(script.contains("Decode-UiText \"\\uC810\\uC218 \\uBD80\\uC871\""))
        assertTrue(script.contains("= \"LOW_SCORE\""))
        assertTrue(script.contains("Decode-UiText \"\\uB4F1\\uB85D \\uC0D8\\uD50C \\uBD88\\uC548\\uC815\""))
        assertTrue(script.contains("= \"UNSTABLE_ENROLLMENT\""))
        assertTrue(script.contains("\$failureReason = Normalize-FailureReason \$row.failure_reason"))
    }

    @Test
    fun evidenceCollectorDoesNotHideFailedFieldGate() {
        val script = readProjectFile("tools/Collect-GalaxyFieldEvidence.ps1")

        assertTrue(script.contains("\$csvSummaryGateFailed = \$false"))
        assertTrue(script.contains("\$LASTEXITCODE -ne 0"))
        assertTrue(script.contains("CSV summary gate failed"))
        assertTrue(script.contains("throw \"CSV summary gate failed. Fix field CSV results before treating evidence as complete.\""))
    }


    @Test
    fun fieldCsvTemplateSupportsCommercialGate() {
        val csv = readProjectFile("FIELD_TEST_RESULTS_TEMPLATE.csv")
        val rows = csv.lineSequence()
            .filter { it.isNotBlank() }
            .drop(1)
            .map { parseCsvLine(it) }
            .toList()

        assertTrue("template should contain at least 30 field rows", rows.size >= 30)
        assertTrue("template should include USER_1", rows.any { it.getOrNull(6) == "USER_1" })
        assertTrue("template should include USER_2", rows.any { it.getOrNull(6) == "USER_2" })
        assertTrue("template should include USER_3", rows.any { it.getOrNull(6) == "USER_3" })

        val scenarios = rows.groupingBy { it.getOrNull(8).orEmpty() }.eachCount()
        listOf(
            "genuine_clean",
            "genuine_mask",
            "genuine_auto_mask",
            "genuine_glasses",
            "genuine_left_patch",
            "genuine_right_patch",
            "excessive_occlusion",
            "impostor_clean",
            "impostor_mask",
            "impostor_auto_mask",
            "static_spoof"
        ).forEach { scenario ->
            assertTrue("$scenario should have at least 3 template rows", (scenarios[scenario] ?: 0) >= 3)
        }
    }



    @Test
    fun finalWorkspaceVerificationAutoDetectsJava17() {
        val script = readProjectFile("tools/Run-FinalWorkspaceVerification.ps1")

        assertTrue(script.contains("function Find-JavaHome"))
        assertTrue(script.contains("function Get-JavaMajorVersion"))
        assertTrue(script.contains("Java 17 or newer was not found"))
        assertTrue(script.contains("Write-Output \"Java:"))
        assertTrue(script.contains(".tools/jdk17"))
    }
    @Test
    fun fieldSummarySmokeTestToolVerifiesFailAndPassPaths() {
        val script = readProjectFile("tools/Test-FieldSummaryGate.ps1")

        assertTrue(script.contains("FIELD_TEST_GATE_FAILED"))
        assertTrue(script.contains("FIELD_TEST_GATE_PASSED"))
        assertTrue(script.contains("FIELD_SUMMARY_GATE_SMOKE_PASSED"))
        assertTrue(script.contains("Field CSV template must contain at least 30 rows"))
    }
    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }
    private fun readProjectFile(path: String): String {
        val file = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        ).firstOrNull { candidate -> candidate.isFile }

        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText(Charsets.UTF_8)
    }
}



