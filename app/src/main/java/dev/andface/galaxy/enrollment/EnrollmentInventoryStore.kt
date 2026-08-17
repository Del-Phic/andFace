package dev.andface.galaxy.enrollment

import android.content.Context
import dev.andface.galaxy.BuildConfig
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class EnrollmentInventoryStore(context: Context) {
    data class VerificationResult(
        val mismatchUserIds: Set<String>,
        val storageError: Boolean
    )

    private data class InventoryReadResult(
        val expectedUserIds: Set<String>?,
        val storageError: Boolean
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secureCodec = SecureProfileCodec.auditLog()

    fun verifyOrBootstrap(currentUserIds: Set<String>): VerificationResult {
        val current = sanitizeRegisteredUserIds(currentUserIds)
        val restored = readInventory()
        if (restored.storageError) return VerificationResult(emptySet(), storageError = true)

        val expected = restored.expectedUserIds
        if (expected == null) {
            return VerificationResult(
                mismatchUserIds = emptySet(),
                storageError = !replaceWith(current)
            )
        }

        return VerificationResult(
            mismatchUserIds = mismatchUserIds(expected, current),
            storageError = false
        )
    }

    fun replaceWith(currentUserIds: Set<String>): Boolean {
        val current = sanitizeRegisteredUserIds(currentUserIds)
        val payload = JSONObject()
            .put("v", VERSION)
            .put("registeredUserIds", JSONArray(current.sorted()))
            .put("updatedAtMs", System.currentTimeMillis())
            .put("profilePolicyVersion", EnrollmentProfile.CURRENT_POLICY_VERSION)
            .put("modelSha256", BuildConfig.FACE_LANDMARKER_MODEL_SHA256)

        return runCatching {
            preferences.edit()
                .putString(INVENTORY_KEY, secureCodec.protect(payload.toString()))
                .commit()
        }.getOrDefault(false)
    }

    private fun readInventory(): InventoryReadResult {
        val stored = preferences.getString(INVENTORY_KEY, null)
            ?: return InventoryReadResult(expectedUserIds = null, storageError = false)

        return runCatching {
            check(secureCodec.isProtected(stored)) { "Unprotected enrollment inventory rejected." }
            val needsReprotection = secureCodec.needsReprotection(stored)
            val payload = JSONObject(secureCodec.unprotect(stored))
            check(payload.optInt("v", 0) == VERSION) { "Unsupported enrollment inventory version." }
            val obsoleteProfilePolicy = payload.getInt("profilePolicyVersion") !=
                EnrollmentProfile.CURRENT_POLICY_VERSION
            val obsoleteModel = normalizeSha256(payload.getString("modelSha256")) !=
                normalizeSha256(BuildConfig.FACE_LANDMARKER_MODEL_SHA256)
            if (obsoleteProfilePolicy || obsoleteModel) {
                return@runCatching InventoryReadResult(expectedUserIds = null, storageError = false)
            }

            val userIds = sanitizeRegisteredUserIds(payload.getJSONArray("registeredUserIds").toUserIdSet())
            check(!needsReprotection || replaceWith(userIds)) { "Enrollment inventory key rotation failed." }
            InventoryReadResult(expectedUserIds = userIds, storageError = false)
        }.getOrElse {
            InventoryReadResult(expectedUserIds = null, storageError = true)
        }
    }

    private fun JSONArray.toUserIdSet(): Set<String> {
        val values = mutableSetOf<String>()
        for (index in 0 until length()) {
            values += getString(index)
        }
        return values
    }

    companion object {
        private const val PREFERENCES_NAME = "andface_enrollment_inventory"
        private const val INVENTORY_KEY = "registered_user_ids"
        private const val VERSION = 1

        internal fun mismatchUserIds(expectedUserIds: Set<String>, actualUserIds: Set<String>): Set<String> {
            return (expectedUserIds - actualUserIds) + (actualUserIds - expectedUserIds)
        }

        internal fun sanitizeRegisteredUserIds(userIds: Set<String>): Set<String> {
            val supported = EnrollmentRepository.SUPPORTED_USER_IDS.toSet()
            check(userIds.all { userId -> userId in supported }) {
                "Unsupported enrollment slot in inventory."
            }
            return userIds.toSet()
        }

        internal fun shouldRewriteAfterClear(
            userId: String,
            profileExistedBeforeClear: Boolean,
            mismatchUserIdsBeforeClear: Set<String>,
            inventoryStorageFailedBeforeClear: Boolean,
            remainingUserIdsAfterClear: Set<String>
        ): Boolean {
            return when {
                inventoryStorageFailedBeforeClear -> remainingUserIdsAfterClear.isEmpty()
                mismatchUserIdsBeforeClear.isEmpty() -> true
                !profileExistedBeforeClear && mismatchUserIdsBeforeClear == setOf(userId) -> true
                else -> false
            }
        }

        private fun normalizeSha256(value: String): String {
            return value
                .replace(":", "")
                .replace(" ", "")
                .uppercase(Locale.US)
        }
    }
}
