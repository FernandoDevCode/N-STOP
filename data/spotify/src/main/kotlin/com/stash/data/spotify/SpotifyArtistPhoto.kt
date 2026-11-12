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
        val exact = items.mapNotNull { node ->
            val obj = node.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (normalizedArtistName(name) != normalizedArtistName(requestedName)) return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!id.matches(Regex("[A-Za-z0-9]{22}"))) return@mapNotNull null
            val images = obj["images"]?.jsonArray ?: return@mapNotNull null
            val imageUrl = images.firstNotNullOfOrNull { image ->
                image.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("https://i.scdn.co/image/") }
            } ?: return@mapNotNull null
            SpotifyArtistPhoto(imageUrl, "https://open.spotify.com/artist/" + id)
        }.distinctBy { it.artistUrl }
        // Ambiguous same-name artists must not silently steal each other's
        // portraits. Keep the stored selection instead until identity is certain.
        exact.singleOrNull()
    }.getOrNull()
}

/** Get Artist by pinned Spotify ID. Check name and ID before displaying art. */
internal fun parseSpotifyArtistDetail(body: String, requestedId: String, requestedName: String): SpotifyArtistPhoto? =
    runCatching {
        if (!requestedId.matches(Regex("[A-Za-z0-9]{22}")) || requestedName.isBlank()) return null
        val artist = Json.parseToJsonElement(body).jsonObject
        if (artist["id"]?.jsonPrimitive?.contentOrNull != requestedId ||
            normalizedArtistName(artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty()) !=
            normalizedArtistName(requestedName)
        ) return null
        val url = artist["images"]?.jsonArray
            ?.mapNotNull { image ->
                val obj = image.jsonObject
                val u = obj["url"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("https://i.scdn.co/image/") } ?: return@mapNotNull null
                val width = obj["width"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val height = obj["height"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                Triple(u, width.toLong() * height, width)
            }?.maxWithOrNull(compareBy<Triple<String, Long, Int>> { it.second }.thenBy { it.third })?.first
            ?: return null
        SpotifyArtistPhoto(url, "https://open.spotify.com/artist/$requestedId")
    }.getOrNull()
