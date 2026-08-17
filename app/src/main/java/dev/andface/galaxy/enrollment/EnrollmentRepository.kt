package dev.andface.galaxy.enrollment

import android.content.Context
import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.feature.FeatureType
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class EnrollmentRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secureProfileCodec = SecureProfileCodec.enrollmentProfiles()

    data class ProfileReadResult(
        val profile: EnrollmentProfile?,
        val storageError: Boolean
    )

    data class ProfileSetReadResult(
        val profiles: List<EnrollmentProfile>,
        val failedUserIds: Set<String>
    )

    fun loadProfiles(): List<EnrollmentProfile> {
        return loadProfilesResult().profiles
    }

    fun loadProfilesResult(): ProfileSetReadResult {
        val profiles = mutableListOf<EnrollmentProfile>()
        val failedUserIds = mutableSetOf<String>()
        SUPPORTED_USER_IDS.forEach { userId ->
            val result = loadProfileResult(userId)
            if (result.storageError) {
                failedUserIds += userId
            } else {
                result.profile?.let(profiles::add)
            }
        }
        return ProfileSetReadResult(
            profiles = profiles,
            failedUserIds = failedUserIds
        )
    }

    fun loadProfile(userId: String): EnrollmentProfile? {
        return loadProfileResult(userId).profile
    }

    fun loadProfileResult(userId: String): ProfileReadResult {
        val rawJson = preferences.getString(keyForUser(userId), null)
            ?: return ProfileReadResult(profile = null, storageError = false)
        return runCatching {
            check(secureProfileCodec.isProtected(rawJson)) { "Unprotected enrollment profile rejected" }
            val needsReprotection = secureProfileCodec.needsReprotection(rawJson)
            val plainJson = secureProfileCodec.unprotect(rawJson)
            if (isReEnrollmentRequiredProfile(plainJson, expectedUserId = userId)) {
                preferences.edit().remove(keyForUser(userId)).commit()
                return ProfileReadResult(profile = null, storageError = false)
            }
            val profile = parseProfile(plainJson, expectedUserId = userId)
            check(!needsReprotection || saveProfile(profile)) { "Enrollment profile key rotation failed" }
            profile
        }.fold(
            onSuccess = { profile -> ProfileReadResult(profile = profile, storageError = false) },
            onFailure = { ProfileReadResult(profile = null, storageError = true) }
        )
    }

    private fun parseProfile(rawJson: String, expectedUserId: String): EnrollmentProfile {
        val json = JSONObject(rawJson)
        val profile = EnrollmentProfile(
            userId = json.getString("userId"),
            createdAtMs = json.getLong("createdAtMs"),
            modelSha256 = json.getString("modelSha256"),
            policyVersion = json.getInt("policyVersion"),
            sampleCount = json.getInt("sampleCount"),
            means = json.getJSONArray("means").toDoubleArray(),
            sigmas = json.getJSONArray("sigmas").toDoubleArray(),
            covariance = json.getJSONArray("covariance").toMatrix(),
            calibratedCleanMahalanobisFloor = json.getDouble("calibratedCleanMahalanobisFloor"),
            calibratedCleanFinalScoreFloor = json.getDouble("calibratedCleanFinalScoreFloor")
        )
        validateProfile(profile, expectedUserId)
        return profile
    }

    private fun isReEnrollmentRequiredProfile(rawJson: String, expectedUserId: String): Boolean {
        return runCatching {
            val json = JSONObject(rawJson)
            check(json.getString("userId") == expectedUserId)
            val meansLength = json.optJSONArray("means")?.length() ?: -1
            val sigmasLength = json.optJSONArray("sigmas")?.length() ?: -1
            val covariance = json.optJSONArray("covariance")
            val covarianceSizeMatches = covariance != null &&
                covariance.length() == FeatureType.COUNT &&
                (0 until covariance.length()).all { row -> covariance.getJSONArray(row).length() == FeatureType.COUNT }
            json.getInt("policyVersion") != EnrollmentProfile.CURRENT_POLICY_VERSION ||
                meansLength != FeatureType.COUNT ||
                sigmasLength != FeatureType.COUNT ||
                !covarianceSizeMatches ||
                !json.has("calibratedCleanMahalanobisFloor") ||
                !json.has("calibratedCleanFinalScoreFloor") ||
                normalizeSha256(json.getString("modelSha256")) != normalizeSha256(BuildConfig.FACE_LANDMARKER_MODEL_SHA256)
        }.getOrDefault(false)
    }

    private fun validateProfile(profile: EnrollmentProfile, expectedUserId: String) {
        check(profile.userId == expectedUserId)
        check(profile.policyVersion == EnrollmentProfile.CURRENT_POLICY_VERSION)
        check(normalizeSha256(profile.modelSha256) == normalizeSha256(BuildConfig.FACE_LANDMARKER_MODEL_SHA256))
        check(EnrollmentSecurityPolicy.hasRequiredCleanSampleCount(profile.sampleCount))
        check(profile.means.size == FeatureType.COUNT)
        check(profile.sigmas.size == FeatureType.COUNT)
        check(profile.covariance.size == FeatureType.COUNT)
        check(profile.covariance.all { row -> row.size == FeatureType.COUNT })
        check(profile.means.all { value -> value.isFinite() })
        check(profile.sigmas.all { value -> value.isFinite() && value > 0.0 })
        check(profile.covariance.all { row -> row.all { value -> value.isFinite() } })
        check(profile.calibratedCleanMahalanobisFloor.isFinite())
        check(profile.calibratedCleanMahalanobisFloor in 0.0..1.0)
        check(profile.calibratedCleanFinalScoreFloor.isFinite())
        check(profile.calibratedCleanFinalScoreFloor in 0.0..1.0)
    }

    fun saveProfile(profile: EnrollmentProfile): Boolean {
        val json = JSONObject()
            .put("userId", profile.userId)
            .put("createdAtMs", profile.createdAtMs)
            .put("modelSha256", profile.modelSha256)
            .put("policyVersion", profile.policyVersion)
            .put("sampleCount", profile.sampleCount)
            .put("means", profile.means.toJsonArray())
            .put("sigmas", profile.sigmas.toJsonArray())
            .put("covariance", profile.covariance.toJsonArray())
            .put("calibratedCleanMahalanobisFloor", profile.calibratedCleanMahalanobisFloor)
            .put("calibratedCleanFinalScoreFloor", profile.calibratedCleanFinalScoreFloor)

        return runCatching {
            validateProfile(profile, expectedUserId = profile.userId)
            preferences.edit()
                .putString(keyForUser(profile.userId), secureProfileCodec.protect(json.toString()))
                .commit()
        }.getOrDefault(false)
    }

    fun clear(userId: String): Boolean {
        return runCatching {
            preferences.edit().remove(keyForUser(userId)).commit()
        }.getOrDefault(false)
    }

    fun clearAll(): Boolean {
        return runCatching {
            val editor = preferences.edit()
            SUPPORTED_USER_IDS.forEach { userId -> editor.remove(keyForUser(userId)) }
            editor.commit()
        }.getOrDefault(false)
    }

    private fun JSONArray.toDoubleArray(): DoubleArray {
        val values = DoubleArray(length())
        for (index in 0 until length()) {
            values[index] = getDouble(index)
        }
        return values
    }

    private fun JSONArray.toMatrix(): Array<DoubleArray> {
        val matrix = Array(length()) { DoubleArray(FeatureType.COUNT) }
        for (row in 0 until length()) {
            matrix[row] = getJSONArray(row).toDoubleArray()
        }
        return matrix
    }

    private fun DoubleArray.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { array.put(it) }
        return array
    }

    private fun Array<DoubleArray>.toJsonArray(): JSONArray {
        val outer = JSONArray()
        forEach { row -> outer.put(row.toJsonArray()) }
        return outer
    }

    private fun normalizeSha256(value: String): String {
        return value
            .replace(":", "")
            .replace(" ", "")
            .uppercase(Locale.US)
    }

    private fun keyForUser(userId: String): String = "profile_$userId"

    companion object {
        private const val PREFERENCES_NAME = "andface_enrollment"
        val SUPPORTED_USER_IDS = listOf("USER_1", "USER_2", "USER_3")
    }
}
