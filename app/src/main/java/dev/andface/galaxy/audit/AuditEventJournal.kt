package dev.andface.galaxy.audit

import org.json.JSONArray
import org.json.JSONObject

/** Storage errors propagate as failure; a damaged history is never silently replaced. */
internal class AuditEventJournal(
    private val read: () -> JSONArray,
    private val write: (JSONArray) -> Boolean,
    private val wallClockMs: () -> Long,
    private val elapsedClockMs: () -> Long,
    private val maxEvents: Int = 200
) {
    private var lastAuthKey: String? = null
    private var lastAuthAtMs = Long.MIN_VALUE
    private var healthy = true

    @Synchronized
    fun isHealthy(): Boolean {
        healthy = runCatching { AuditChain.validateOrMigrate(read()); true }.getOrDefault(false)
        return healthy
    }

    @Synchronized
    fun latestEventWallClockMs(): Long? = runCatching {
        val events = AuditChain.validateOrMigrate(read())
        if (events.length() == 0) null else events.getJSONObject(events.length() - 1).getLong("timestampMs")
    }.getOrElse { healthy = false; null }

    @Synchronized
    fun append(eventType: String, fields: JSONObject): Boolean {
        val nowMs = elapsedClockMs()
        val authKey = if (eventType == "AUTH_SUCCESS" || eventType == "AUTH_FAILED") {
            listOf(eventType, fields.optString("failureReason"), fields.optString("matchedUserId"),
                fields.optString("occlusion")).joinToString("|")
        } else null
        if (healthy && authKey != null && authKey == lastAuthKey &&
            nowMs >= lastAuthAtMs && nowMs - lastAuthAtMs < 1_000L
        ) return true
        val saved = runCatching {
            val events = AuditChain.append(read(), eventType, fields, wallClockMs())
            write(AuditChain.trim(events, maxEvents))
        }.getOrDefault(false)
        healthy = saved
        if (saved && authKey != null) {
            lastAuthKey = authKey
            lastAuthAtMs = nowMs
        }
        return saved
    }
}
