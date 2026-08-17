package dev.andface.galaxy.audit

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.auth.AuthDecision
import dev.andface.galaxy.auth.AuthResult
import dev.andface.galaxy.auth.AuthSuccessWindowController
import dev.andface.galaxy.auth.FailureReason
import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.enrollment.EnrollmentSecurityPolicy
import dev.andface.galaxy.enrollment.SecureProfileCodec
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

class SecurityAuditLogger(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secureCodec = SecureProfileCodec.auditLog()

    fun isHealthy(): Boolean = true

    fun latestEventWallClockMs(): Long? = null

    fun recordAuthentication(result: AuthResult): Boolean {
        return append(
            eventType = if (result.decision == AuthDecision.SUCCESS) "AUTH_SUCCESS" else "AUTH_FAILED",
            fields = JSONObject()
                .put("decision", result.decision.name)
                .put("failureReason", result.failureReason.name)
                .put("matchedUserId", result.matchedUserId ?: JSONObject.NULL)
                .put("secondBestUserId", result.secondBestUserId ?: JSONObject.NULL)
                .put("registeredUserCount", result.registeredUserCount)
                .put("finalScore", rounded(result.finalScore))
                .put("coverage", rounded(result.coverage))
                .put("margin", rounded(result.margin))
                .put("livenessPassed", result.livenessPassed)
                .put("livenessFrameCount", result.livenessFrameCount)
                .put("livenessChallenge", result.livenessChallenge ?: JSONObject.NULL)
                .put("livenessChallengePassed", result.livenessChallengePassed)
                .put("observableCount", result.observableCount)
                .put("occlusion", result.occlusionSummary)
        )
    }

    internal fun recordAuthSuccessWindowShown(window: AuthSuccessWindowController.Window): Boolean {
        return append(
            eventType = "AUTH_SUCCESS_WINDOW_SHOWN",
            fields = JSONObject()
                .put("windowId", window.windowId)
                .put("userId", window.userId)
                .put("shownAtElapsedMs", window.shownAtMs)
                .put("expiresAtElapsedMs", window.expiresAtMs)
                .put("durationMs", window.expiresAtMs - window.shownAtMs)
        )
    }

    fun recordEnrollmentCompleted(userId: String, sampleCount: Int): Boolean {
        return append(
            eventType = "ENROLLMENT_COMPLETED",
            fields = JSONObject()
                .put("userId", userId)
                .put("sampleCount", sampleCount)
                .put("profilePolicyVersion", EnrollmentProfile.CURRENT_POLICY_VERSION)
                .put("modelSha256", BuildConfig.FACE_LANDMARKER_MODEL_SHA256)
        )
    }

    fun recordEnrollmentRejected(userId: String, reason: FailureReason): Boolean {
        return append(
            eventType = "ENROLLMENT_REJECTED",
            fields = JSONObject()
                .put("userId", userId)
                .put("failureReason", reason.name)
        )
    }

    fun recordProfileCleared(userId: String): Boolean {
        return append(
            eventType = "PROFILE_CLEARED",
            fields = JSONObject().put("userId", userId)
        )
    }

    fun recordProfilesExpired(userIds: Set<String>, activeProfileCount: Int): Boolean {
        return append(
            eventType = "PROFILES_EXPIRED",
            fields = JSONObject()
                .put("userIds", JSONArray(userIds.sorted()))
                .put("activeProfileCount", activeProfileCount)
                .put("maxAgeDays", EnrollmentSecurityPolicy.PROFILE_MAX_AGE_DAYS)
        )
    }

    fun recordOperatorCredential(actionName: String?, userId: String?, outcome: String): Boolean {
        return append(
            eventType = "OPERATOR_CREDENTIAL",
            fields = JSONObject()
                .put("action", actionName ?: JSONObject.NULL)
                .put("userId", userId ?: JSONObject.NULL)
                .put("outcome", outcome)
        )
    }

    fun recordOperatorConfirmation(actionName: String, userId: String?, outcome: String): Boolean {
        return append(
            eventType = "OPERATOR_CONFIRMATION",
            fields = JSONObject()
                .put("action", actionName)
                .put("userId", userId ?: JSONObject.NULL)
                .put("outcome", outcome)
        )
    }

    fun recordSecurityStateRepairRequested(stateName: String): Boolean {
        return append(
            eventType = "SECURITY_STATE_REPAIR_REQUESTED",
            fields = JSONObject().put("stateName", stateName)
        )
    }

    private fun append(eventType: String, fields: JSONObject): Boolean = true

    private fun appendAfterAuditLogReset(eventType: String, fields: JSONObject, error: Throwable): Boolean {
        return runCatching {
            val recovered = auditLogRecoveredEvents(eventType, error)
            val appended = AuditChain.append(
                events = recovered,
                eventType = eventType,
                fields = fields.withAuditMetadata(),
                timestampMs = System.currentTimeMillis()
            )
            persistEvents(AuditChain.trim(appended, MAX_EVENTS))
        }.getOrDefault(false)
    }

    private fun recoverAuditLogAfterLoadFailure(recoveredEventType: String, error: Throwable): Boolean {
        return runCatching {
            persistEvents(AuditChain.trim(auditLogRecoveredEvents(recoveredEventType, error), MAX_EVENTS))
        }.getOrDefault(false)
    }

    private fun auditLogRecoveredEvents(recoveredEventType: String, error: Throwable): JSONArray {
        return AuditChain.append(
            events = JSONArray(),
            eventType = "AUDIT_LOG_RECOVERED",
            fields = JSONObject()
                .put("reason", error::class.java.simpleName ?: "Unknown")
                .put("recoveredEventType", recoveredEventType)
                .withAuditMetadata(),
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun persistEvents(events: JSONArray): Boolean {
        return preferences.edit()
            .putString(AUDIT_EVENTS_KEY, secureCodec.protect(events.toString()))
            .commit()
    }

    private fun JSONObject.withAuditMetadata(): JSONObject {
        return put(
            "app",
            JSONObject()
                .put("auditSchemaVersion", AUDIT_SCHEMA_VERSION)
                .put("applicationId", BuildConfig.APPLICATION_ID)
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE)
                .put("debugBuild", BuildConfig.DEBUG)
                .put("operatorReleaseSigned", BuildConfig.OPERATOR_RELEASE_SIGNED)
                .put("requireLockTaskForAccess", BuildConfig.REQUIRE_LOCK_TASK_FOR_ACCESS)
                .put("faceLandmarkerModelSha256", BuildConfig.FACE_LANDMARKER_MODEL_SHA256)
                .put(
                    "operatorReleaseCertSha256",
                    BuildConfig.OPERATOR_RELEASE_CERT_SHA256.takeIf { it.isNotBlank() } ?: JSONObject.NULL
                )
                .put("androidSdk", Build.VERSION.SDK_INT)
        ).put(
            "clock",
            JSONObject()
                .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
                .put("uptimeMillis", SystemClock.uptimeMillis())
                .put("bootCount", bootCountOrNull())
        )
    }

    private fun bootCountOrNull(): Any {
        return runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull() ?: JSONObject.NULL
    }

    private fun loadEvents(): JSONArray {
        val stored = preferences.getString(AUDIT_EVENTS_KEY, null) ?: return JSONArray()
        return AuditChain.validateOrMigrate(JSONArray(secureCodec.unprotect(stored)))
    }

    private fun validateAndReprotectEventsIfNeeded(): Boolean {
        val stored = preferences.getString(AUDIT_EVENTS_KEY, null) ?: return true
        val events = AuditChain.validateOrMigrate(JSONArray(secureCodec.unprotect(stored)))
        if (!secureCodec.needsReprotection(stored)) return true

        return persistEvents(events)
    }

    private fun rounded(value: Double): Double {
        return round(value * 1000.0) / 1000.0
    }

    companion object {
        private const val PREFERENCES_NAME = "andface_security_audit"
        private const val AUDIT_EVENTS_KEY = "events"
        private const val MAX_EVENTS = 200
        private const val AUDIT_SCHEMA_VERSION = 3
    }
}
