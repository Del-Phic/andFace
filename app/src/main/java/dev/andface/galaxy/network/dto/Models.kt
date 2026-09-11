package dev.andface.galaxy.network.dto

import com.google.gson.annotations.SerializedName

// Explicit names survive release shrinking. No image, landmark or template DTO exists.
data class Credentials(@SerializedName("username") val username: String, @SerializedName("password") val password: String)
data class Registration(@SerializedName("userCode") val userCode: String, @SerializedName("username") val username: String, @SerializedName("password") val password: String)
data class ServerUser(@SerializedName("userCode") val userCode: String)
data class LoginResponse(@SerializedName("accessToken") val accessToken: String, @SerializedName("expiresIn") val expiresIn: Long, @SerializedName("user") val user: ServerUser)
data class DeviceRequest(@SerializedName("deviceId") val deviceId: String, @SerializedName("deviceName") val deviceName: String)
data class ResultRequest(
 @SerializedName("userCode") val userCode: String,
 @SerializedName("deviceId") val deviceId: String,
 @SerializedName("result") val result: String,
 @SerializedName("fuzzyScore") val fuzzyScore: Double,
 @SerializedName("mahalanobisScore") val mahalanobisScore: Double,
 @SerializedName("finalScore") val finalScore: Double,
 @SerializedName("coverage") val coverage: Double,
 @SerializedName("margin") val margin: Double,
 @SerializedName("liveness") val liveness: Boolean,
 @SerializedName("failureReason") val failureReason: String?,
 @SerializedName("eventId") val eventId: String,
 @SerializedName("occurredAt") val occurredAt: String
)
