package com.stash.data.spotify

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads the Spotify web-player searchDesktop GraphQL response without treating
 * a missing/changed schema as an empty search. Data may come from search or
 * searchV2; each bucket contains actual Spotify URIs, never YouTube IDs.
 *
 * Null = unsupported/failed response (caller may attempt authorized REST).
 * Empty page = a recognized response whose buckets contain no items.
 */
internal fun parseSpotifyPartnerSearchPage(
    root: JsonObject,
    offset: Int,
    limit: Int,
): SpotifyCatalogPage? {
    val data = root["data"] as? JsonObject ?: return null
    val search = (data["search"] ?: data["searchV2"]) as? JsonObject ?: return null
    if (root["errors"] != null) return null
    val results = mutableListOf<SpotifyCatalogItem>()
    var recognized = false
    var hasMore = false

    for ((keys, kind) in listOf(
        listOf("tracks", "tracksV2") to SpotifyCatalogKind.TRACK,
        listOf("artists", "artistsV2") to SpotifyCatalogKind.ARTIST,
        listOf("albums", "albumsV2") to SpotifyCatalogKind.ALBUM,
    )) {
        val bucket = keys.firstNotNullOfOrNull { search[it] as? JsonObject } ?: continue
        val items = bucket["items"] as? JsonArray ?: continue
        recognized = true
        val total = bucket.number("totalCount")
            ?: (bucket["pagingInfo"] as? JsonObject)?.number("total")
            ?: (bucket["pagingInfo"] as? JsonObject)?.number("totalCount")
        hasMore = hasMore || if (total != null) offset + items.size < total
            else items.size >= limit && items.isNotEmpty()

        items.forEach { wrapped ->
            val wrapper = wrapped as? JsonObject ?: return@forEach
            val payload = (
                wrapper["track"] ?: wrapper["artist"] ?: wrapper["album"] ?:
                (wrapper["item"] as? JsonObject)?.get("data") ?: wrapper["data"] ?: wrapped
            ) as? JsonObject ?: return@forEach
            val uri = payload.string("uri") ?: return@forEach
            val prefix = when (kind) {
                SpotifyCatalogKind.TRACK -> "spotify:track:"
                SpotifyCatalogKind.ARTIST -> "spotify:artist:"
                SpotifyCatalogKind.ALBUM -> "spotify:album:"
            }
            if (!uri.startsWith(prefix)) return@forEach
            val id = uri.removePrefix(prefix).takeIf { it.isNotBlank() } ?: return@forEach
            val profile = payload["profile"] as? JsonObject
            val name = (payload.string("name") ?: profile?.string("name"))
                ?.takeIf { it.isNotBlank() } ?: return@forEach
            val artistNames = artistNames(payload["artists"])
            val album = (payload["albumOfTrack"] ?: payload["album"]) as? JsonObject
            val artwork = when (kind) {
                SpotifyCatalogKind.TRACK ->
                    imageUrl(album?.get("coverArt")) ?: imageUrl(album?.get("images")) ?:
                    imageUrl(album?.get("visuals")) ?: imageUrl(payload["coverArt"])
                SpotifyCatalogKind.ARTIST ->
                    imageUrl(payload["visuals"]) ?: imageUrl(payload["images"])
                SpotifyCatalogKind.ALBUM ->
                    imageUrl(payload["coverArt"]) ?: imageUrl(payload["images"])
            }
            results += SpotifyCatalogItem(
                kind = kind,
                id = id,
                title = name,
                subtitle = when (kind) {
                    SpotifyCatalogKind.ARTIST -> "Artista"
                    else -> artistNames.joinToString(", ").ifBlank { null }
                },
                artworkUrl = artwork,
                durationMs = if (kind == SpotifyCatalogKind.TRACK)
                    (payload["duration"] as? JsonObject)?.number("totalMilliseconds")
                        ?: payload.number("duration_ms")
                    else null,
                explicit = ((payload["contentRating"] as? JsonObject)
                    ?.string("label") == "EXPLICIT"),
                releaseDate = payload.string("date") ?: payload.string("releaseDate"),
                releaseGroup = null,
            )
        }
    }
    if (!recognized) return null
    return SpotifyCatalogPage(results.distinctBy { it.kind to it.id }, hasMore)
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.number(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

private fun artistNames(value: JsonElement?): List<String> {
    val array = when (value) {
        is JsonArray -> value
        is JsonObject -> value["items"] as? JsonArray
        else -> null
    } ?: return emptyList()
    return array.mapNotNull { value ->
        val artist = value as? JsonObject ?: return@mapNotNull null
        val nested = (artist["data"] ?: artist["artist"]) as? JsonObject
        artist.string("name")
            ?: (artist["profile"] as? JsonObject)?.string("name")
            ?: nested?.string("name")
            ?: (nested?.get("profile") as? JsonObject)?.string("name")
    }
}

private fun imageUrl(element: JsonElement?, depth: Int = 0): String? {
    if (depth > 6) return null
    return when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { imageUrl(it, depth + 1) }
        is JsonObject -> element.string("url")
            ?: listOf("sources", "items", "avatarImage", "coverArt", "images")
                .firstNotNullOfOrNull { imageUrl(element[it], depth + 1) }
        else -> null
    }
}
