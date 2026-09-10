package dev.andface.galaxy.audit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuditChainTest {
    @Test
    fun legacyRoundedMetricsRecoverOnlyWhenTheirOriginalHashMatches() {
        val material = "2|1000|AUTH_FAILED|GENESIS|{\"coverage\":0.0, \"finalScore\":0.0, \"margin\":0.0}"
        val hash = java.util.Base64.getEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        )
        val event = JSONObject().put("chainVersion", 2).put("timestampMs", 1000L)
            .put("eventType", "AUTH_FAILED").put("prevHash", "GENESIS").put("hash", hash)
            .put("fields", JSONObject().put("coverage", 0).put("finalScore", 0).put("margin", 0))
        assertEquals(1, AuditChain.validateOrMigrate(JSONArray().put(event)).length())
        event.getJSONObject("fields").put("finalScore", 1)
        assertThrows(IllegalStateException::class.java) { AuditChain.validateOrMigrate(JSONArray().put(event)) }
    }

    @Test
    fun trimReanchorsRetainedWindow() {
        val fullChain = appendEvent(appendEvent(appendEvent(JSONArray(), "A"), "B"), "C")

        val trimmed = AuditChain.trim(fullChain, 2)

        assertEquals(2, trimmed.length())
        assertEquals("GENESIS", trimmed.getJSONObject(0).getString("prevHash"))
        assertEquals(2, trimmed.getJSONObject(0).getInt("chainVersion"))
    }

    @Test
    fun firstRetainedEventDeletionBreaksCurrentChain() {
        val fullChain = appendEvent(appendEvent(appendEvent(JSONArray(), "A"), "B"), "C")
        val trimmed = AuditChain.trim(fullChain, 2)
        val tampered = JSONArray().put(trimmed.getJSONObject(1))

        assertThrows(IllegalStateException::class.java) {
            AuditChain.validateOrMigrate(tampered)
        }
    }

    @Test
    fun fieldModificationBreaksChain() {
        val chain = appendEvent(JSONArray(), "A")
        chain.getJSONObject(0).getJSONObject("fields").put("userId", "ATTACKER")

        assertThrows(IllegalStateException::class.java) {
            AuditChain.validateOrMigrate(chain)
        }
    }

    private fun appendEvent(events: JSONArray, eventType: String): JSONArray {
        return AuditChain.append(
            events = events,
            eventType = eventType,
            fields = JSONObject().put("userId", "USER_1"),
            timestampMs = eventType.first().code.toLong()
        )
    }
}
