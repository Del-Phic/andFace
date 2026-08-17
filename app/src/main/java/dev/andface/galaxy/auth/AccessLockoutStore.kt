package dev.andface.galaxy.auth

import android.content.Context
import android.os.SystemClock
import dev.andface.galaxy.enrollment.SecureProfileCodec
import org.json.JSONObject
import kotlin.math.abs

class AccessLockoutStore(context: Context) {
    data class RestoreResult(
        val untilElapsedMs: Long?,
        val storageError: Boolean
    )

    data class RiskyFailureRestoreResult(
        val state: AuthenticationEngine.RiskyFailureState?,
        val storageError: Boolean
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secureCodec = SecureProfileCodec.auditLog()

    fun save(untilElapsedMs: Long): Boolean {
        return runCatching {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            val nowWallMs = System.currentTimeMillis()
            val remainingMs = (untilElapsedMs - nowElapsedMs).coerceIn(0L, MAX_RESTORED_LOCKOUT_MS)
            val payload = JSONObject()
                .put("v", VERSION)
                .put("untilElapsedMs", untilElapsedMs)
                .put("untilWallMs", nowWallMs + remainingMs)
                .put("bootMarkerMs", nowWallMs - nowElapsedMs)

            preferences.edit()
                .putString(LOCKOUT_KEY, secureCodec.protect(payload.toString()))
                .remove(RISKY_FAILURE_KEY)
                .commit()
        }.getOrDefault(false)
    }

    fun saveRiskyFailureWindow(state: AuthenticationEngine.RiskyFailureState): Boolean {
        return runCatching {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            val nowWallMs = System.currentTimeMillis()
            val windowAgeMs = (nowElapsedMs - state.windowStartMs)
                .coerceIn(0L, RISKY_FAILURE_WINDOW_MS)
            val remainingMs = RISKY_FAILURE_WINDOW_MS - windowAgeMs
            val payload = JSONObject()
                .put("v", VERSION)
                .put("count", state.count)
                .put("windowStartElapsedMs", state.windowStartMs)
                .put("lastCountedElapsedMs", state.lastCountedMs)
                .put("windowExpiresWallMs", nowWallMs + remainingMs)
                .put("bootMarkerMs", nowWallMs - nowElapsedMs)

            preferences.edit()
                .putString(RISKY_FAILURE_KEY, secureCodec.protect(payload.toString()))
                .commit()
        }.getOrDefault(false)
    }

    fun restoreActive(): RestoreResult {
        val stored = preferences.getString(LOCKOUT_KEY, null) ?: return RestoreResult(null, false)
        return runCatching {
            check(secureCodec.isProtected(stored)) { "Unprotected access lockout state rejected." }
            val needsReprotection = secureCodec.needsReprotection(stored)
            val payload = JSONObject(secureCodec.unprotect(stored))
            check(payload.optInt("v", 0) == VERSION) { "Unsupported access lockout state version." }

            val nowElapsedMs = SystemClock.elapsedRealtime()
            val nowWallMs = System.currentTimeMillis()
            val remainingMs = restoredRemainingMs(
                nowElapsedMs = nowElapsedMs,
                nowWallMs = nowWallMs,
                storedUntilElapsedMs = payload.getLong("untilElapsedMs"),
                storedUntilWallMs = payload.getLong("untilWallMs"),
                storedBootMarkerMs = payload.getLong("bootMarkerMs")
            )

            val restored = restoreResultForRemaining(
                remainingMs = remainingMs,
                nowElapsedMs = nowElapsedMs,
                clearStored = { clear() }
            )
            if (restored.untilElapsedMs != null && needsReprotection && !save(restored.untilElapsedMs)) {
                RestoreResult(null, true)
            } else {
                restored
            }
        }.getOrElse {
            RestoreResult(null, true)
        }
    }

    fun restoreRiskyFailureWindow(): RiskyFailureRestoreResult {
        val stored = preferences.getString(RISKY_FAILURE_KEY, null) ?: return RiskyFailureRestoreResult(null, false)
        return runCatching {
            check(secureCodec.isProtected(stored)) { "Unprotected risky failure state rejected." }
            val needsReprotection = secureCodec.needsReprotection(stored)
            val payload = JSONObject(secureCodec.unprotect(stored))
            check(payload.optInt("v", 0) == VERSION) { "Unsupported risky failure state version." }

            val nowElapsedMs = SystemClock.elapsedRealtime()
            val nowWallMs = System.currentTimeMillis()
            val restored = restoredRiskyFailureState(
                nowElapsedMs = nowElapsedMs,
                nowWallMs = nowWallMs,
                storedCount = payload.getInt("count"),
                storedWindowStartElapsedMs = payload.getLong("windowStartElapsedMs"),
                storedLastCountedElapsedMs = payload.getLong("lastCountedElapsedMs"),
                storedWindowExpiresWallMs = payload.getLong("windowExpiresWallMs"),
                storedBootMarkerMs = payload.getLong("bootMarkerMs"),
                clearStored = { clearRiskyFailureWindow() }
            )
            if (restored.state != null && needsReprotection && !saveRiskyFailureWindow(restored.state)) {
                RiskyFailureRestoreResult(null, true)
            } else {
                restored
            }
        }.getOrElse {
            RiskyFailureRestoreResult(null, true)
        }
    }

    fun clearRiskyFailureWindow(): Boolean {
        return preferences.edit().remove(RISKY_FAILURE_KEY).commit()
    }

    fun clear(): Boolean {
        return preferences.edit()
            .remove(LOCKOUT_KEY)
            .remove(RISKY_FAILURE_KEY)
            .commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "andface_access_lockout"
        private const val LOCKOUT_KEY = "state"
        private const val RISKY_FAILURE_KEY = "risky_failure_window"
        private const val VERSION = 1
        private const val MAX_RESTORED_LOCKOUT_MS = 30_000L
        private const val RISKY_FAILURE_WINDOW_MS = 12_000L
        private const val BOOT_MARKER_TOLERANCE_MS = 60_000L

        internal fun restoredRemainingMs(
            nowElapsedMs: Long,
            nowWallMs: Long,
            storedUntilElapsedMs: Long,
            storedUntilWallMs: Long,
            storedBootMarkerMs: Long
        ): Long {
            val sameBoot = abs((nowWallMs - nowElapsedMs) - storedBootMarkerMs) <= BOOT_MARKER_TOLERANCE_MS
            val elapsedRemainingMs = storedUntilElapsedMs - nowElapsedMs
            val wallRemainingMs = storedUntilWallMs - nowWallMs
            return when {
                sameBoot -> maxOf(elapsedRemainingMs, wallRemainingMs).coerceIn(0L, MAX_RESTORED_LOCKOUT_MS)
                wallRemainingMs > 0L -> wallRemainingMs.coerceAtMost(MAX_RESTORED_LOCKOUT_MS)
                else -> MAX_RESTORED_LOCKOUT_MS
            }
        }

        internal fun restoreResultForRemaining(
            remainingMs: Long,
            nowElapsedMs: Long,
            clearStored: () -> Boolean
        ): RestoreResult {
            if (remainingMs > 0L) {
                return RestoreResult(nowElapsedMs + remainingMs, false)
            }
            return if (clearStored()) {
                RestoreResult(null, false)
            } else {
                RestoreResult(null, true)
            }
        }

        internal fun restoredRiskyFailureState(
            nowElapsedMs: Long,
            nowWallMs: Long,
            storedCount: Int,
            storedWindowStartElapsedMs: Long,
            storedLastCountedElapsedMs: Long,
            storedWindowExpiresWallMs: Long,
            storedBootMarkerMs: Long,
            clearStored: () -> Boolean
        ): RiskyFailureRestoreResult {
            if (storedCount <= 0) {
                return if (clearStored()) {
                    RiskyFailureRestoreResult(null, false)
                } else {
                    RiskyFailureRestoreResult(null, true)
                }
            }

            val sameBoot = abs((nowWallMs - nowElapsedMs) - storedBootMarkerMs) <= BOOT_MARKER_TOLERANCE_MS
            val elapsedWindowAgeMs = nowElapsedMs - storedWindowStartElapsedMs
            val wallRemainingMs = storedWindowExpiresWallMs - nowWallMs
            val active = if (sameBoot) {
                elapsedWindowAgeMs in 0L..RISKY_FAILURE_WINDOW_MS || wallRemainingMs > 0L
            } else {
                wallRemainingMs > 0L
            }
            if (!active) {
                return if (clearStored()) {
                    RiskyFailureRestoreResult(null, false)
                } else {
                    RiskyFailureRestoreResult(null, true)
                }
            }

            val restoredWindowStartMs = if (sameBoot && elapsedWindowAgeMs in 0L..RISKY_FAILURE_WINDOW_MS) {
                storedWindowStartElapsedMs
            } else {
                nowElapsedMs - (RISKY_FAILURE_WINDOW_MS - wallRemainingMs.coerceIn(0L, RISKY_FAILURE_WINDOW_MS))
            }
            val restoredLastCountedMs = if (sameBoot) {
                storedLastCountedElapsedMs
            } else {
                Long.MIN_VALUE
            }

            return RiskyFailureRestoreResult(
                state = AuthenticationEngine.RiskyFailureState(
                    count = storedCount,
                    windowStartMs = restoredWindowStartMs,
                    lastCountedMs = restoredLastCountedMs
                ),
                storageError = false
            )
        }
    }
}
