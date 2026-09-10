package dev.andface.galaxy.mediapipe

/** All timestamps use uptime milliseconds, as does the MediaPipe submission timestamp. */
internal class LiveFrameSession(
    private val maxResultAgeMs: Long = 750L,
    private val streamTimeoutMs: Long = 1_500L
) {
    private var startedAtMs: Long? = null
    private var lastAcceptedAtMs: Long? = null

    fun start(nowMs: Long) {
        startedAtMs = nowMs
        lastAcceptedAtMs = null
    }

    fun stop() {
        startedAtMs = null
        lastAcceptedAtMs = null
    }

    fun accept(timestampMs: Long, nowMs: Long): Boolean {
        val start = startedAtMs ?: return false
        if (timestampMs <= start || timestampMs > nowMs || nowMs - timestampMs > maxResultAgeMs) return false
        if (lastAcceptedAtMs?.let { timestampMs <= it } == true) return false
        lastAcceptedAtMs = timestampMs
        return true
    }

    fun timedOut(nowMs: Long): Boolean {
        val reference = lastAcceptedAtMs ?: startedAtMs ?: return false
        return nowMs < reference || nowMs - reference >= streamTimeoutMs
    }
}
