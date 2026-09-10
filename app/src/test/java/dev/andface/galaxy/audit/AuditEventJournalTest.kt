package dev.andface.galaxy.audit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuditEventJournalTest {
    private var stored = "[]"
    private var elapsed = 1000L
    private var writes = 0
    private var allowWrite = true
    private fun journal(maxEvents: Int = 200) = AuditEventJournal(
        read = { JSONArray(stored) },
        write = { if (allowWrite) { stored = it.toString(); writes++; true } else false },
        wallClockMs = { 100000L + elapsed }, elapsedClockMs = { elapsed }, maxEvents = maxEvents
    )

    @Test
    fun appendPersistsAValidatedChainAndRestoresAfterRestart() {
        val journal = journal()
        assertTrue(journal.append("AUTH_SUCCESS", JSONObject().put("matchedUserId", "USER_1")))
        assertEquals(1, AuditChain.validateOrMigrate(JSONArray(stored)).length())
        val restarted = journal()
        assertTrue(restarted.isHealthy())
        assertEquals(101000L, restarted.latestEventWallClockMs())
    }

    @Test
    fun identicalFrameResultsAreSampledButIdentityAndFailureTransitionsAreRecorded() {
        val journal = journal()
        val fields = JSONObject().put("matchedUserId", "USER_1")
        repeat(30) { assertTrue(journal.append("AUTH_SUCCESS", fields)); elapsed += 20L }
        assertEquals(1, writes)
        assertTrue(journal.append("AUTH_FAILED", JSONObject().put("failureReason", "LOW_SCORE")))
        assertEquals(2, writes)
        elapsed += 1000L
        assertTrue(journal.append("AUTH_FAILED", JSONObject().put("failureReason", "LOW_SCORE")))
        assertEquals(3, writes)
    }

    @Test
    fun failedWriteIsReportedAndRetriedInsteadOfBeingThrottledAsSuccess() {
        val journal = journal()
        allowWrite = false
        assertFalse(journal.append("AUTH_SUCCESS", JSONObject()))
        allowWrite = true
        assertTrue(journal.append("AUTH_SUCCESS", JSONObject()))
        assertEquals(1, writes)
    }

    @Test
    fun corruptHistoryIsNotErasedOrAccepted() {
        val journal = journal()
        assertTrue(journal.append("ENROLLMENT_COMPLETED", JSONObject()))
        val events = JSONArray(stored)
        events.getJSONObject(0).put("eventType", "TAMPERED")
        stored = events.toString()
        val corrupt = stored
        assertFalse(journal.isHealthy())
        assertFalse(journal.append("AUTH_SUCCESS", JSONObject()))
        assertEquals(corrupt, stored)
        assertEquals(1, writes)
    }

    @Test
    fun retentionIsBoundedAndRetainedChainRemainsValid() {
        val journal = journal(maxEvents = 3)
        repeat(10) { assertTrue(journal.append("EVENT_$it", JSONObject())) }
        val events = AuditChain.validateOrMigrate(JSONArray(stored))
        assertEquals(3, events.length())
        assertEquals("EVENT_7", events.getJSONObject(0).getString("eventType"))
    }

    @Test
    fun wholeNumberScoresSurviveJsonPersistenceAndTheNextWrite() {
        val journal = journal()
        val fields = JSONObject().put("finalScore", 0.0).put("coverage", 1.0).put("margin", 0.0)
        assertTrue(journal.append("AUTH_FAILED", fields))
        assertTrue(journal.isHealthy())
        elapsed += 1000L
        assertTrue(journal.append("AUTH_FAILED", fields))
        assertTrue(journal().isHealthy())
        assertEquals(2, JSONArray(stored).length())
    }
}
