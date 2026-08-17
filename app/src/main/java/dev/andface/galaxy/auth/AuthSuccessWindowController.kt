package dev.andface.galaxy.auth

import java.security.SecureRandom

internal class AuthSuccessWindowController(
    private val clockMs: () -> Long,
    private val windowDurationMs: Long = DEFAULT_WINDOW_DURATION_MS,
    private val reissueCooldownMs: Long = 0L,
    private val windowIdFactory: () -> String = { generateWindowId() }
) {
    data class Window(
        val windowId: String,
        val userId: String,
        val shownAtMs: Long,
        val expiresAtMs: Long
    )

    private var currentWindow: Window? = null
    private var lastShownAtMs: Long = Long.MIN_VALUE

    fun show(userId: String?): Window? {
        if (userId.isNullOrBlank()) {
            clear()
            return null
        }
        val nowMs = clockMs()
        if (isWithinReissueCooldown(nowMs)) return null
        return Window(
            windowId = windowIdFactory(),
            userId = userId,
            shownAtMs = nowMs,
            expiresAtMs = nowMs + windowDurationMs
        ).also {
            currentWindow = it
            lastShownAtMs = nowMs
        }
    }

    fun activeWindow(): Window? {
        val window = currentWindow ?: return null
        if (clockMs() < window.expiresAtMs) return window

        currentWindow = null
        return null
    }

    fun clear() {
        currentWindow = null
    }

    private fun isWithinReissueCooldown(nowMs: Long): Boolean {
        return reissueCooldownMs > 0L &&
            lastShownAtMs != Long.MIN_VALUE &&
            nowMs - lastShownAtMs < reissueCooldownMs
    }

    companion object {
        const val DEFAULT_WINDOW_DURATION_MS = 1_200L
        private val random = SecureRandom()

        private fun generateWindowId(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}
