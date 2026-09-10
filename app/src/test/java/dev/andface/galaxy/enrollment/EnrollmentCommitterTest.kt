package dev.andface.galaxy.enrollment

import org.junit.Assert.*
import org.junit.Test

class EnrollmentCommitterTest {
    private val original = EnrollmentProfile("USER_1", 1L, "model", 16, 45,
        doubleArrayOf(0.4), doubleArrayOf(0.1), arrayOf(doubleArrayOf(0.01)))
    private val replacement = original.copy(createdAtMs = 2L)

    @Test fun auditFailureRestoresExistingEnrollmentAndInventory() {
        var stored: EnrollmentProfile? = original
        var inventoryTimestamp: Long? = original.createdAtMs
        val committer = EnrollmentCommitter(
            { stored = it; true },
            { inventoryTimestamp = stored?.createdAtMs; true },
            { false }
        )
        assertFalse(committer.commit(original, replacement))
        assertSame(original, stored)
        assertEquals(original.createdAtMs, inventoryTimestamp)
    }

    @Test fun failedNewEnrollmentRemovesOnlyTheUncommittedReplacement() {
        var stored: EnrollmentProfile? = null
        val committer = EnrollmentCommitter({ stored = it; true }, { true }, { false })
        assertFalse(committer.commit(null, replacement))
        assertNull(stored)
    }

    @Test fun failedWriteThatMutatedMemoryAlsoRestoresPreviousValue() {
        var stored: EnrollmentProfile? = original
        var auditCalls = 0
        val committer = EnrollmentCommitter(
            { stored = it; it === original }, { true }, { auditCalls++; true }
        )
        assertFalse(committer.commit(original, replacement))
        assertSame(original, stored)
        assertEquals(0, auditCalls)
    }

    @Test fun inventoryFailureRestoresPreviousProfileBeforeAuditing() {
        var stored: EnrollmentProfile? = original
        var auditCalls = 0
        val committer = EnrollmentCommitter(
            { stored = it; true }, { stored === original }, { auditCalls++; true }
        )
        assertFalse(committer.commit(original, replacement))
        assertSame(original, stored)
        assertEquals(0, auditCalls)
    }

    @Test fun successfulReplacementRemainsStored() {
        var stored: EnrollmentProfile? = original
        val committer = EnrollmentCommitter({ stored = it; true }, { true }, { true })
        assertTrue(committer.commit(original, replacement))
        assertSame(replacement, stored)
    }
}
