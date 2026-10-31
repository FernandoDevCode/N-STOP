package com.stash.data.ytmusic

import kotlinx.serialization.json.*

// ── JsonElement navigation extensions ────────────────────────────────────────
// Exposed as `internal` so SearchResponseParser.kt (and future parser files in
// this module) can reuse them without duplication.

/**
 * Safely navigates a chain of JSON object keys.
 *
 * Returns the [JsonElement] at the end of the path, or null if any key is missing
 * or the intermediate value is not a [JsonObject].
 */
internal fun JsonObject.navigatePath(vararg keys: String): JsonElement? {
    var current: JsonElement = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}

/** Safely casts to [JsonObject], returning null on type mismatch. */
internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

/** Safely casts to [JsonArray], returning null on type mismatch. */
internal fun JsonElement.asArray(): JsonArray? = this as? JsonArray

/**
 * If this element is a [JsonArray], returns it; otherwise returns null.
 * Useful when the navigation target might already be an array.
 */
internal fun JsonElement.firstArray(): JsonArray? = this as? JsonArray

/** Safely extracts a string primitive value, returning null on type mismatch. */
internal fun JsonElement.asString(): String? =
    try { jsonPrimitive.contentOrNull } catch (_: Exception) { null }

