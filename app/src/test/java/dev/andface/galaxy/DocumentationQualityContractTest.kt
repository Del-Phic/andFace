package dev.andface.galaxy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentationQualityContractTest {
    @Test
    fun koreanDeliverableDocsDoNotContainMojibakeCharacters() {
        val docs = koreanDocs()
        val offenders = docs.flatMap { path ->
            val text = readProjectFile(path)
            val hasReplacement = text.contains('\uFFFD')
            val hasCompatibilityHan = text.any { it in '\uF900'..'\uFAFF' }
            val hasUnifiedHan = text.any { it in '\u4E00'..'\u9FFF' }
            listOfNotNull(
                if (hasReplacement) "$path contains replacement character" else null,
                if (hasCompatibilityHan) "$path contains compatibility Han characters" else null,
                if (hasUnifiedHan) "$path contains Han characters often produced by mojibake" else null
            )
        }

        assertTrue(
            "Korean deliverable docs contain mojibake-like characters: ${offenders.joinToString()}",
            offenders.isEmpty()
        )
    }

    @Test
    fun packageDocsDescribeS147FeatureCountAndApkHash() {
        val docs = listOf(
            readProjectFile("PACKAGE_README_KR.md"),
            readProjectFile("FACE_AUTH_VERIFICATION_REPORT.md")
        ).joinToString("\n")

        assertTrue(docs.contains("\uC5BC\uAD74 \uC778\uC99D S147"))
        assertTrue(docs.contains("\uAD00\uCE21 \uD2B9\uC9D5 \uC218: 170"))
        assertTrue(docs.contains("\uB4F1\uB85D \uC815\uCC45 \uBC84\uC804: 15"))
        assertTrue(docs.contains("FD6D130113D61C827D3864123A18158AE482548B88105A4F722EA1F4F9F495CD"))
        assertTrue(docs.contains("\uC228\uACA8\uC9C4 \uD558\uAD00 \uB610\uB294 \uAC00\uB824\uC9C4 \uB208 \uC601\uC5ED \uBCF5\uC6D0 \uC5C6\uC74C"))
    }

    @Test
    fun packageDocsKeepCommercializationCaveatVisible() {
        val audit = readProjectFile("GOAL_COMPLETION_AUDIT.md")
        assertTrue(audit.contains("Galaxy"))
        assertTrue(audit.contains("FIELD_TEST_RESULTS_TEMPLATE.csv"))
        assertTrue(audit.contains("80%"))
        assertFalse(audit.contains('\uFFFD'))
    }

    private fun koreanDocs(): List<String> = listOf(
        "PACKAGE_README_KR.md",
        "FACE_AUTH_VERIFICATION_REPORT.md",
        "DEVICE_VALIDATION_STATUS.md",
        "DEMO_RUNBOOK_KR.md",
        "FIELD_DEPLOYMENT_CHECKLIST.md",
        "FIELD_TEST_PROTOCOL.md",
        "GOAL_COMPLETION_AUDIT.md"
    )

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