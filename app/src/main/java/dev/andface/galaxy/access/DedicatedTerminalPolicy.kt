package dev.andface.galaxy.access

object DedicatedTerminalPolicy {
    const val LOCK_TASK_MODE_NONE = 0
    const val LOCK_TASK_MODE_LOCKED = 1
    const val LOCK_TASK_MODE_PINNED = 2

    fun isSatisfied(requireLockTaskMode: Boolean, lockTaskModeState: Int): Boolean {
        return !requireLockTaskMode || lockTaskModeState == LOCK_TASK_MODE_LOCKED
    }

    fun shouldAttemptManagedLockTask(
        requireLockTaskMode: Boolean,
        lockTaskModeState: Int,
        lockTaskPermitted: Boolean
    ): Boolean {
        return requireLockTaskMode &&
            lockTaskPermitted &&
            lockTaskModeState == LOCK_TASK_MODE_NONE
    }
}
