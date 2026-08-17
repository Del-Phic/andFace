package dev.andface.galaxy.auth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadmeSecurityContractTest {
    @Test
    fun readmeDocumentsEveryStrictFailureReason() {
        val readme = loadDoc("README.md")
        val missing = FailureReason.values()
            .filterNot { reason -> reason == FailureReason.NONE }
            .filterNot { reason -> readme.contains("`${reason.name}`") }

        assertTrue(
            "README is missing strict failure reasons: ${missing.joinToString { it.name }}",
            missing.isEmpty()
        )
    }

    @Test
    fun docsDoNotDocumentRemovedPhysicalControlApi() {
        val docs = mapOf(
            "README.md" to loadDoc("README.md"),
            "FIELD_DEPLOYMENT_CHECKLIST.md" to loadDoc("FIELD_DEPLOYMENT_CHECKLIST.md")
        )
        val removedPhysicalControlTerms = listOf(
            "DoorAccessAuthorizer",
            "DoorControllerIdPolicy",
            "ACCESS_GRANT_CONSUMED",
            "ACCESS_GRANT_ISSUED",
            "DOOR_OPEN_DENIED",
            "consumeForDoorOpen",
            "door-control",
            "door-open",
            "secure-door",
            "physical-control",
            "relay controller"
        )
        val found = docs.flatMap { (name, content) ->
            removedPhysicalControlTerms
                .filter(content::contains)
                .map { term -> "$name:$term" }
        }

        assertTrue(
            "Documentation still references removed physical-control terms: ${found.joinToString()}",
            found.isEmpty()
        )
    }

    private fun loadDoc(name: String): String {
        val doc = listOf(
            File(name),
            File("../$name"),
            File("../../$name")
        ).firstOrNull { file -> file.isFile }

        requireNotNull(doc) { "$name was not found from test working directory." }
        return doc.readText()
    }
}
