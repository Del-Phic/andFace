package dev.andface.galaxy.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeploymentSecurityConfigTest {
    @Test
    fun manifestKeepsAccessTerminalSecurityFlags() {
        val manifest = readProjectFile("app/src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("""<uses-permission android:name="android.permission.CAMERA" />"""))
        assertTrue(manifest.contains("""<uses-permission android:name="android.permission.HIDE_OVERLAY_WINDOWS" />"""))
        assertFalse(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertTrue(manifest.contains("""android:excludeFromRecents="true""""))
        assertTrue(manifest.contains("""android:lockTaskMode="if_whitelisted""""))
        assertTrue(manifest.contains("""android:resizeableActivity="false""""))
        assertTrue(manifest.contains("""android:screenOrientation="portrait""""))
        assertTrue(manifest.contains("""android:taskAffinity="""""))
    }

    @Test
    fun backupRulesExcludeAllAppDataDomains() {
        val backupRules = readProjectFile("app/src/main/res/xml/backup_rules.xml")
        listOf("sharedpref", "database", "file", "external", "root").forEach { domain ->
            assertTrue(
                "backup_rules.xml must exclude $domain",
                backupRules.contains("""domain="$domain"""") && backupRules.contains("""path="."""")
            )
        }
    }

    @Test
    fun androidTwelveDataExtractionRulesExcludeCloudAndDeviceTransfer() {
        val rules = readProjectFile("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue(rules.contains("<cloud-backup"))
        assertTrue(rules.contains("""disableIfNoEncryptionCapabilities="true""""))
        assertTrue(rules.contains("<device-transfer>"))
        listOf("sharedpref", "database", "file", "external", "root").forEach { domain ->
            assertTrue(
                "data_extraction_rules.xml must exclude $domain",
                rules.contains("""domain="$domain"""") && rules.contains("""path="."""")
            )
        }
    }

    private fun readProjectFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        )
        val file = candidates.firstOrNull { it.isFile }
        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText()
    }
}
