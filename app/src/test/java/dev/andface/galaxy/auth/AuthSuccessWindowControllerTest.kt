package dev.andface.galaxy.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSuccessWindowControllerTest {
    @Test
    fun windowExpiresAfterConfiguredDuration() {
        var nowMs = 10_000L
        val controller = AuthSuccessWindowController(clockMs = { nowMs }, windowDurationMs = 1_200L)

        val window = controller.show("USER_1")

        assertEquals("USER_1", window?.userId)
        assertEquals("USER_1", controller.activeWindow()?.userId)

        nowMs += 1_199L
        assertEquals("USER_1", controller.activeWindow()?.userId)

        nowMs += 1L
        assertNull(controller.activeWindow())
    }

    @Test
    fun newWindowReplacesPreviousWindow() {
        var nowMs = 20_000L
        val controller = AuthSuccessWindowController(clockMs = { nowMs }, windowDurationMs = 1_000L)

        controller.show("USER_1")
        nowMs += 200L
        controller.show("USER_2")

        assertEquals("USER_2", controller.activeWindow()?.userId)
    }

    @Test
    fun reissueCooldownSuppressesRapidRepeatedWindows() {
        var nowMs = 21_000L
        var nextId = 1
        val controller = AuthSuccessWindowController(
            clockMs = { nowMs },
            windowDurationMs = 1_000L,
            reissueCooldownMs = 5_000L,
            windowIdFactory = { "window-${nextId++}" }
        )

        val first = controller.show("USER_1")
        nowMs += 1_200L
        val suppressed = controller.show("USER_1")
        nowMs += 3_800L
        val second = controller.show("USER_1")

        assertEquals("window-1", first?.windowId)
        assertNull(suppressed)
        assertEquals("window-2", second?.windowId)
    }

    @Test
    fun clearDoesNotBypassReissueCooldown() {
        var nowMs = 22_000L
        val controller = AuthSuccessWindowController(
            clockMs = { nowMs },
            windowDurationMs = 1_000L,
            reissueCooldownMs = 5_000L
        )

        controller.show("USER_1")
        controller.clear()
        nowMs += 1_000L

        assertNull(controller.show("USER_1"))
    }

    @Test
    fun windowIncludesAuditableWindowId() {
        var nextId = 1
        val controller = AuthSuccessWindowController(
            clockMs = { 25_000L },
            windowDurationMs = 1_000L,
            windowIdFactory = { "window-${nextId++}" }
        )

        val first = controller.show("USER_1")
        val second = controller.show("USER_2")

        assertEquals("window-1", first?.windowId)
        assertEquals("window-2", second?.windowId)
        assertEquals("window-2", controller.activeWindow()?.windowId)
    }

    @Test
    fun clearRemovesActiveWindowImmediately() {
        val controller = AuthSuccessWindowController(clockMs = { 30_000L }, windowDurationMs = 1_000L)

        controller.show("USER_1")
        controller.clear()

        assertNull(controller.activeWindow())
    }
}
