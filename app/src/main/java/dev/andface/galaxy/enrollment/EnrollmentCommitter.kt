package dev.andface.galaxy.enrollment

/** Keeps an existing enrollment when a replacement cannot be fully persisted. */
class EnrollmentCommitter(
    private val writeProfile: (EnrollmentProfile?) -> Boolean,
    private val writeInventory: () -> Boolean,
    private val recordCompletion: (EnrollmentProfile) -> Boolean
) {
    fun commit(previous: EnrollmentProfile?, replacement: EnrollmentProfile): Boolean {
        require(previous == null || previous.userId == replacement.userId)
        val committed = runCatching {
            writeProfile(replacement) && writeInventory() && recordCompletion(replacement)
        }.getOrDefault(false)
        if (committed) return true

        // A failed SharedPreferences commit can still change its in-memory value.
        // Restore even after a reported write failure; the caller remains fail closed.
        val restored = runCatching { writeProfile(previous) }.getOrDefault(false)
        if (restored) runCatching { writeInventory() }
        return false
    }
}
