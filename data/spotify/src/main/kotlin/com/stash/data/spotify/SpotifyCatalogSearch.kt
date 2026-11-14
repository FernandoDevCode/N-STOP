package com.stash.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Catalog identity comes from Spotify; it must never be used as a YouTube video ID. */
enum class SpotifyCatalogKind { TRACK, ARTIST, ALBUM }

data class SpotifyCatalogItem(
    val kind: SpotifyCatalogKind,
    val id: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val durationMs: Long? = null,
    val explicit: Boolean = false,
    val releaseDate: String? = null,
    val releaseGroup: String? = null,
)

data class SpotifyCatalogPage(
    val items: List<SpotifyCatalogItem>,
    val hasMore: Boolean,
)

/** Parses the three independent buckets returned by Spotify's /v1/search. */
internal fun parseSpotifyCatalogPage(raw: String): SpotifyCatalogPage {
    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw) as? JsonObject
        ?: return SpotifyCatalogPage(emptyList(), false)
    val results = mutableListOf<SpotifyCatalogItem>()
    var hasMore = false
    for ((bucket, kind) in listOf(
        "tracks" to SpotifyCatalogKind.TRACK,
        "artists" to SpotifyCatalogKind.ARTIST,
        "albums" to SpotifyCatalogKind.ALBUM,
    )) {
        val section = root[bucket] as? JsonObject ?: continue
        hasMore = hasMore || section.string("next") != null
        val entries = section["items"] as? JsonArray ?: continue
        entries.forEach { entry ->
            val item = entry as? JsonObject ?: return@forEach
            val id = item.string("id")?.takeIf { it.isNotBlank() } ?: return@forEach
            val name = item.string("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            val artists = (item["artists"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.string("name") }
            val album = item["album"] as? JsonObject
            val artOwner = when (kind) {
                SpotifyCatalogKind.TRACK -> album
                else -> item
            }
            val artwork = ((artOwner?.get("images") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.string("url")
            val subtitle = when (kind) {
                SpotifyCatalogKind.TRACK -> artists.joinToString(", ").ifBlank { null }
                SpotifyCatalogKind.ALBUM -> artists.joinToString(", ").ifBlank { null }
                SpotifyCatalogKind.ARTIST -> "Artista"
            }
            results += SpotifyCatalogItem(
                kind = kind,
                id = id,
                title = name,
                subtitle = subtitle,
                artworkUrl = artwork,
                durationMs = if (kind == SpotifyCatalogKind.TRACK)
                    (item["duration_ms"] as? JsonPrimitive)?.longOrNull else null,
                explicit = (item["explicit"] as? JsonPrimitive)?.booleanOrNull == true,
                releaseDate = item.string("release_date"),
                releaseGroup = item.string("album_group"),
            )
        }
    }
    return SpotifyCatalogPage(results.distinctBy { it.kind to it.id }, hasMore)
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** Artist albums and album track endpoints return a page at the root, not a search bucket. */
internal fun parseSpotifyArtistAlbumsPage(raw: String): SpotifyCatalogPage {
    val root = Json.parseToJsonElement(raw) as? JsonObject
        ?: return SpotifyCatalogPage(emptyList(), false)
    return parseSpotifyCatalogPage(JsonObject(mapOf("albums" to root)).toString())
}

internal fun parseSpotifyAlbumTracksPage(raw: String): SpotifyCatalogPage {
    val root = Json.parseToJsonElement(raw) as? JsonObject
        ?: return SpotifyCatalogPage(emptyList(), false)
    return parseSpotifyCatalogPage(JsonObject(mapOf("tracks" to root)).toString())
}
