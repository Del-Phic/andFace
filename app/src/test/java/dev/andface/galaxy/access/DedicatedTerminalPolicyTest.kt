package dev.andface.galaxy.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedicatedTerminalPolicyTest {
    @Test
    fun acceptsAnyLockTaskStateWhenPolicyIsDisabled() {
        assertTrue(
            DedicatedTerminalPolicy.isSatisfied(
                requireLockTaskMode = false,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_NONE
            )
        )
    }

    @Test
    fun rejectsNoLockTaskWhenPolicyIsEnabled() {
        assertFalse(
            DedicatedTerminalPolicy.isSatisfied(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_NONE
            )
        )
    }

    @Test
    fun acceptsActiveLockTaskWhenPolicyIsEnabled() {
        assertTrue(
            DedicatedTerminalPolicy.isSatisfied(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_LOCKED
            )
        )
    }

    @Test
    fun rejectsUserScreenPinningWhenPolicyIsEnabled() {
        assertFalse(
            DedicatedTerminalPolicy.isSatisfied(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_PINNED
            )
        )
    }

    @Test
    fun attemptsManagedLockTaskOnlyWhenRequiredPermittedAndNotAlreadyLocked() {
        assertTrue(
            DedicatedTerminalPolicy.shouldAttemptManagedLockTask(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_NONE,
                lockTaskPermitted = true
            )
        )
        assertFalse(
            DedicatedTerminalPolicy.shouldAttemptManagedLockTask(
                requireLockTaskMode = false,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_NONE,
                lockTaskPermitted = true
            )
        )
        assertFalse(
            DedicatedTerminalPolicy.shouldAttemptManagedLockTask(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_NONE,
                lockTaskPermitted = false
            )
        )
        assertFalse(
            DedicatedTerminalPolicy.shouldAttemptManagedLockTask(
                requireLockTaskMode = true,
                lockTaskModeState = DedicatedTerminalPolicy.LOCK_TASK_MODE_LOCKED,
                lockTaskPermitted = true
            )
        )
    }
}
