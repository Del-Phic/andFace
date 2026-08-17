package dev.andface.galaxy.audit

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object AuditChain {
    private const val CURRENT_VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val GENESIS_HASH = "GENESIS"

    fun append(events: JSONArray, eventType: String, fields: JSONObject, timestampMs: Long): JSONArray {
        val chain = validateOrMigrate(events)
        val previousHash = if (chain.length() == 0) {
            GENESIS_HASH
        } else {
            chain.getJSONObject(chain.length() - 1).getString("hash")
        }
        chain.put(buildEvent(eventType, fields, timestampMs, previousHash))
        return chain
    }

    fun trim(events: JSONArray, maxEvents: Int): JSONArray {
        val chain = validateOrMigrate(events)
        val trimmed = JSONArray()
        val start = (chain.length() - maxEvents).coerceAtLeast(0)
        for (index in start until chain.length()) {
            trimmed.put(chain.getJSONObject(index))
        }
        return reanchor(trimmed)
    }

    fun validateOrMigrate(events: JSONArray): JSONArray {
        val migrated = JSONArray()
        var previousStoredHash: String? = null
        var previousMigratedHash: String? = null
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index)
            val migratedEvent = if (event.has("hash") && event.has("prevHash")) {
                validateExisting(event, previousStoredHash)
                previousStoredHash = event.getString("hash")
                buildEvent(
                    eventType = event.getString("eventType"),
                    fields = event.getJSONObject("fields"),
                    timestampMs = event.getLong("timestampMs"),
                    previousHash = previousMigratedHash ?: GENESIS_HASH
                )
            } else {
                previousStoredHash = null
                buildEvent(
                    eventType = event.getString("eventType"),
                    fields = event.getJSONObject("fields"),
                    timestampMs = event.getLong("timestampMs"),
                    previousHash = previousMigratedHash ?: GENESIS_HASH
                )
            }
            previousMigratedHash = migratedEvent.getString("hash")
            migrated.put(migratedEvent)
        }
        return migrated
    }

    private fun validateExisting(event: JSONObject, previousHash: String?) {
        val version = event.optInt("chainVersion", LEGACY_VERSION)
        check(version in LEGACY_VERSION..CURRENT_VERSION) { "Unsupported audit chain version." }

        val actualPreviousHash = event.getString("prevHash")
        if (previousHash != null) {
            check(actualPreviousHash == previousHash) { "Audit event chain is broken." }
        } else if (version == CURRENT_VERSION) {
            check(actualPreviousHash == GENESIS_HASH) { "Audit event chain is missing its retained-window anchor." }
        }
        val expectedHash = hashFor(
            version = version,
            eventType = event.getString("eventType"),
            fields = event.getJSONObject("fields"),
            timestampMs = event.getLong("timestampMs"),
            previousHash = actualPreviousHash
        )
        check(event.getString("hash") == expectedHash) { "Audit event hash is invalid." }
    }

    private fun buildEvent(
        eventType: String,
        fields: JSONObject,
        timestampMs: Long,
        previousHash: String
    ): JSONObject {
        return JSONObject()
            .put("chainVersion", CURRENT_VERSION)
            .put("timestampMs", timestampMs)
            .put("eventType", eventType)
            .put("fields", fields)
            .put("prevHash", previousHash)
            .put("hash", hashFor(CURRENT_VERSION, eventType, fields, timestampMs, previousHash))
    }

    private fun reanchor(events: JSONArray): JSONArray {
        val reanchored = JSONArray()
        var previousHash: String? = null
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index)
            val rebuilt = buildEvent(
                eventType = event.getString("eventType"),
                fields = event.getJSONObject("fields"),
                timestampMs = event.getLong("timestampMs"),
                previousHash = previousHash ?: GENESIS_HASH
            )
            previousHash = rebuilt.getString("hash")
            reanchored.put(rebuilt)
        }
        return reanchored
    }

    private fun hashFor(
        version: Int,
        eventType: String,
        fields: JSONObject,
        timestampMs: Long,
        previousHash: String
    ): String {
        val material = listOf(
            version.toString(),
            timestampMs.toString(),
            eventType,
            previousHash,
            canonicalize(fields)
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun canonicalize(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> {
                val keys = value.keys().asSequence().toList().sorted()
                keys.joinToString(prefix = "{", postfix = "}") { key ->
                    "${JSONObject.quote(key)}:${canonicalize(value.get(key))}"
                }
            }
            is JSONArray -> {
                (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index ->
                    canonicalize(value.get(index))
                }
            }
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }
}
