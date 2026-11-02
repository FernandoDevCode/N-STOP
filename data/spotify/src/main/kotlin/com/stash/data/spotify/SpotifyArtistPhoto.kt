package com.stash.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/** Spotify's own profile photograph, never an album cover or guessed image. */
data class SpotifyArtistPhoto(
    val imageUrl: String,
    val artistUrl: String,
)

private fun normalizedArtistName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

/** Search matches the exact artist name; never show a similarly named artist's photo. */
internal fun parseSpotifyArtistPhoto(body: String, requestedName: String): SpotifyArtistPhoto? {
    if (requestedName.isBlank()) return null
    return runCatching {
        val items = Json.parseToJsonElement(body).jsonObject["artists"]
            ?.jsonObject?.get("items")?.jsonArray ?: return null
        items.firstNotNullOfOrNull { node ->
            val obj = node.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (normalizedArtistName(name) != normalizedArtistName(requestedName)) return@firstNotNullOfOrNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!id.matches(Regex("[A-Za-z0-9]{22}"))) return@firstNotNullOfOrNull null
            val images = obj["images"]?.jsonArray ?: return@firstNotNullOfOrNull null
            val imageUrl = images.firstNotNullOfOrNull { image ->
                image.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("https://i.scdn.co/image/") }
            } ?: return@firstNotNullOfOrNull null
            SpotifyArtistPhoto(imageUrl, "https://open.spotify.com/artist/" + id)
        }
    }.getOrNull()
}
