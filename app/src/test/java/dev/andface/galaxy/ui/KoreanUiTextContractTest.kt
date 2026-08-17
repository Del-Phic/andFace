package dev.andface.galaxy.ui

import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanUiTextContractTest {
    @Test
    fun mainLayoutXmlParsesAndUsesReadableFaceAuthenticationText() {
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(layout.toByteArray(Charsets.UTF_8)))

        val decodedText = collectTextAttributes(document.documentElement)
        listOf(
            "\uC778\uC99D \uC2E4\uD328",
            "\uC778\uC99D \uD655\uC778 \uC911",
            "\uB4F1\uB85D 0/3",
            "Fuzzy 0.000",
            "\uB9C8\uD560\uB77C\uB178\uBE44\uC2A4 0.000",
            "\uAD00\uCE21 0/170",
            "\uB9C8\uC2A4\uD06C",
            "\uC548\uACBD",
            "\uC67C\uC548\uB300",
            "\uC624\uB978\uC548\uB300"
        ).forEach { expected ->
            assertTrue("UI text is missing readable face-auth phrase: $expected", decodedText.contains(expected))
        }
    }

    @Test
    fun occlusionHintsAppearAboveResultMetrics() {
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")

        assertTrue(layout.indexOf("@+id/hintsRow") < layout.indexOf("@+id/resultText"))
        assertTrue(layout.indexOf("@+id/hintsRow") < layout.indexOf("@+id/metricsGrid"))
        assertTrue(layout.indexOf("@+id/hintsRow") < layout.indexOf("@+id/registerButton"))
        assertTrue(layout.contains("android:paddingBottom=\"28dp\""))
    }
    @Test
    fun mainUiDoesNotContainReplacementCharacters() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")
        val overlay = readProjectFile("app/src/main/java/dev/andface/galaxy/ui/LandmarkOverlayView.kt")
        val combined = mainActivity + "\n" + layout + "\n" + overlay

        assertFalse("UI source should not contain Unicode replacement characters", combined.contains('\uFFFD'))
    }

    @Test
    fun userFacingTextDoesNotDescribePhysicalAccessControl() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")
        val layout = readProjectFile("app/src/main/res/layout/activity_main.xml")
        val combined = mainActivity + "\n" + layout

        listOf("AccessGrant", "ACCESS_GRANT").forEach { forbidden ->
            assertFalse("UI text should stay focused on face authentication: $forbidden", combined.contains(forbidden))
        }
        listOf("\uCD9C\uC785", "\uBB38 \uC81C\uC5B4", "\uB9B4\uB808\uC774").forEach { forbidden ->
            assertFalse("UI text should stay focused on face authentication: $forbidden", combined.contains(forbidden))
        }
    }

    @Test
    fun debugDemoBuildShowsDetailedMetricsByDefault() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("BuildConfig.DEBUG || SystemClock.elapsedRealtime() < detailedMetricsVisibleUntilMs"))
        assertTrue(mainActivity.contains("Fuzzy"))
        assertTrue(mainActivity.contains("\\uC0DD\\uB3D9\\uC131"))
        assertTrue(mainActivity.contains("\\uB9C8\\uD560\\uB77C\\uB178\\uBE44\\uC2A4"))
        assertTrue(mainActivity.contains("\\uAD00\\uCE21"))
    }

    @Test
    fun landmarkOverlayDrawsMeshContoursNotOnlyDots() {
        val overlay = readProjectFile("app/src/main/java/dev/andface/galaxy/ui/LandmarkOverlayView.kt")

        assertTrue(overlay.contains("FACE_OVAL"))
        assertTrue(overlay.contains("LEFT_EYE"))
        assertTrue(overlay.contains("RIGHT_EYE"))
        assertTrue(overlay.contains("OUTER_LIP"))
        assertTrue(overlay.contains("drawLine"))
    }

    @Test
    fun dashboardLongPressCopiesFieldCsvMeasurementRow() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")
        val csvHeader = readProjectFile("FIELD_TEST_RESULTS_TEMPLATE.csv").lineSequence().first()
        val headerColumns = parseCsvLine(csvHeader)

        assertTrue(mainActivity.contains("copyCurrentFieldCsvRowToClipboard"))
        assertTrue(mainActivity.contains("ClipData.newPlainText(\"AndFace field CSV row\""))
        assertTrue(mainActivity.contains("findViewById<View>(R.id.dashboard).setOnLongClickListener"))
        assertTrue(mainActivity.contains("FINAL_APK_FILE_NAME"))
        assertTrue(mainActivity.contains("installedApkSha256ForFieldCsv()"))
        assertTrue(mainActivity.contains("applicationInfo.sourceDir"))
        assertTrue(mainActivity.contains("APK_HASH_BUFFER_SIZE"))
        assertFalse(mainActivity.replace("\r\n", "\n").contains("FINAL_APK_FILE_NAME,\n            \"\",\n            selectedUserId"))
        assertTrue(mainActivity.contains("FeatureType.COUNT.toString()"))
        assertTrue(mainActivity.contains("authDecisionForFieldCsv"))
        assertTrue(mainActivity.contains("result.faceQualityScore"))
        assertTrue(mainActivity.contains("result.meshSymmetryScore"))
        assertTrue(mainActivity.contains("result.landmarkTopologyScore"))
        assertTrue(headerColumns.contains("actual_result"))
        assertTrue(headerColumns.contains("fuzzy_score"))
        assertTrue(headerColumns.contains("mahalanobis_score"))
        assertTrue(headerColumns.contains("identity_consistency"))
        assertTrue(headerColumns.contains("face_quality"))
        assertTrue(headerColumns.contains("mesh_symmetry"))
        assertTrue(headerColumns.contains("landmark_topology"))
        assertTrue(headerColumns.contains("feature_count"))
    }

    @Test
    fun enrollmentDisablesClearActionsWhileCollecting() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("clearButton.isEnabled = false"))
        assertTrue(mainActivity.contains("clearButton.isEnabled = !collectingEnrollment &&"))
        assertTrue(Regex("""private fun showClearConfirmation\(userId: String\)\s*\{\s*if \(collectingEnrollment\) return""").containsMatchIn(mainActivity))
    }
    @Test
    fun enrollmentCanBeCancelledFromRegisterButtonWhileCollecting() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("if (collectingEnrollment) cancelEnrollmentByUser() else beginEnrollment(selectedUserId)"))
        assertTrue(mainActivity.contains("private fun cancelEnrollmentByUser()"))
        assertTrue(mainActivity.contains("registerButton.text = \"\\uB4F1\\uB85D \\uCDE8\\uC18C\""))
        assertTrue(mainActivity.contains("collectingEnrollment -> \"\\uB4F1\\uB85D \\uCDE8\\uC18C\""))
        assertTrue(mainActivity.contains("registerButton.isEnabled = true"))
        assertTrue(mainActivity.contains("registerButton.isEnabled = collectingEnrollment ||"))
    }
    @Test
    fun enrollmentKeepsSessionOpenForTransientMissingFeatureFrames() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(
            Regex("""renderEnrollmentCollectionProgress\(\s*enrollmentRetryGuidance\(FailureReason\.TOO_FEW_FEATURES\)""").containsMatchIn(mainActivity)
        )
        assertFalse(mainActivity.contains("abortEnrollment(FailureReason.TOO_FEW_FEATURES, \"TOO_FEW_FEATURES\")"))
        assertTrue(mainActivity.contains("ENROLLMENT_NO_FACE_TIMEOUT_MS = 30_000L"))
    }
    @Test
    fun enrollmentClearsAndLocksOcclusionHintsWhileCollecting() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("clearOcclusionHintsForEnrollment()"))
        assertTrue(mainActivity.contains("lowerFaceHint.isChecked = false"))
        assertTrue(mainActivity.contains("glassesHint.isChecked = false"))
        assertTrue(mainActivity.contains("setOcclusionHintsEnabled(enabled)"))
        assertTrue(mainActivity.contains("lowerFaceHint.isEnabled = enabled"))
    }
    @Test
    fun enrollmentProgressSeparatesSavedFramesFromMinimumTime() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("\\uC800\\uC7A5 %d/%d"))
        assertTrue(mainActivity.contains("\\uCD5C\\uC18C\\uC2DC\\uAC04"))
        assertFalse(mainActivity.contains("%s %s %d/%d %.1f/%.1fs"))
    }
    @Test
    fun failureReasonsUseReadableKoreanLabels() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(mainActivity.contains("FailureReason.LOW_SCORE -> \"\\uC810\\uC218 \\uBD80\\uC871\""))
        assertTrue(mainActivity.contains("FailureReason.UNSTABLE_ENROLLMENT -> \"\\uB4F1\\uB85D \\uC0D8\\uD50C \\uBD88\\uC548\\uC815\""))
        assertTrue(mainActivity.contains("FailureReason.EXCESSIVE_OCCLUSION -> \"\\uAC00\\uB9BC\\uC774 \\uB108\\uBB34 \\uB9CE\\uC74C\""))
        assertFalse(mainActivity.contains("FailureReason.LOW_SCORE -> \"LOW_SCORE\""))
        assertFalse(mainActivity.contains("FailureReason.UNSTABLE_ENROLLMENT -> \"UNSTABLE_ENROLLMENT\""))
    }

    @Test
    fun mainActivityRuntimeTextDoesNotContainMojibakeCharacters() {
        val mainActivity = readProjectFile("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertFalse(mainActivity.contains('\uFFFD'))
        assertFalse(mainActivity.any { it in '\uF900'..'\uFAFF' })
        assertFalse(mainActivity.any { it in '\u4E00'..'\u9FFF' })
    }

    private fun collectTextAttributes(node: org.w3c.dom.Node): List<String> {
        val values = mutableListOf<String>()
        val attributes = node.attributes
        if (attributes != null) {
            for (index in 0 until attributes.length) {
                val item = attributes.item(index)
                if (item.localName == "text") values += item.nodeValue
            }
        }
        val children = node.childNodes
        for (index in 0 until children.length) {
            values += collectTextAttributes(children.item(index))
        }
        return values
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
        val file = listOf(File(path), File("../$path"), File("../../$path"))
            .firstOrNull { candidate -> candidate.isFile }

        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText(Charsets.UTF_8)
    }
}