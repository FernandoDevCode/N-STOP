package com.stash.data.spotify

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Spotify's web-player catalogue details use different nested schemas than
 * searchDesktop. A missing/changed container is a FAILURE (null), never an
 * empty discography or a fabricated album. No YouTube identifiers are accepted.
 */
internal fun parseSpotifyPartnerArtistReleasesPage(
    response: JsonObject,
    offset: Int,
    limit: Int,
): SpotifyCatalogPage? {
    if (response["errors"] != null) return null
    val artist = (response.obj("data")?.obj("artistUnion")) ?: return null
    val discography = artist.obj("discography") ?: return null
    val all = discography.obj("all") ?: return null
    val groups = all.array("items") ?: return null
    val releases = mutableListOf<SpotifyCatalogItem>()
    groups.forEach { groupElement ->
        val group = groupElement as? JsonObject ?: return@forEach
        val items = group.obj("releases")?.array("items")
            ?: group.array("items")
            ?: return@forEach
        items.forEach { releaseElement ->
            val wrapper = releaseElement as? JsonObject ?: return@forEach
            val album = wrapper.obj("data") ?: wrapper.obj("album") ?: wrapper
            val uri = album.str("uri") ?: return@forEach
            if (!uri.startsWith("spotify:album:")) return@forEach
            val id = uri.removePrefix("spotify:album:")
                .takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) } ?: return@forEach
            val name = album.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            val artistNames = album.obj("artists")?.array("items").orEmpty()
                .mapNotNull { element ->
                    val a = element as? JsonObject ?: return@mapNotNull null
                    a.obj("profile")?.str("name") ?: a.obj("data")?.obj("profile")?.str("name")
                }
            val date = album.obj("date")?.str("isoString")?.take(10)
                ?: album.obj("date")?.long("year")?.toString()
            releases += SpotifyCatalogItem(
                kind = SpotifyCatalogKind.ALBUM,
                id = id,
                title = name,
                subtitle = artistNames.joinToString(", ").ifBlank { null },
                artworkUrl = album.obj("coverArt")?.array("sources")?.firstImageUrl(),
                releaseDate = date,
                releaseGroup = album.str("type")?.lowercase(),
            )
        }
    }
    val count = all.long("totalCount")
    // The GraphQL offset refers to group count, not the expanded set of
    // anniversary/deluxe releases inside groups.
    val more = if (count != null) offset + groups.size < count
        else groups.isNotEmpty() && groups.size >= limit
    return SpotifyCatalogPage(releases.distinctBy { it.id }, more)
}

internal fun parseSpotifyPartnerAlbumTracksPage(
    response: JsonObject,
    offset: Int,
    limit: Int,
): SpotifyCatalogPage? {
    if (response["errors"] != null) return null
    val album = response.obj("data")?.obj("albumUnion") ?: return null
    val tracks = album.obj("tracksV2") ?: album.obj("tracks") ?: return null
    val rawItems = tracks.array("items") ?: return null
    val artwork = album.obj("coverArt")?.array("sources")?.firstImageUrl()
    val albumArtists = album.obj("artists")?.array("items").orEmpty().mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        obj.obj("profile")?.str("name") ?: obj.obj("data")?.obj("profile")?.str("name")
    }
    val items = rawItems.mapNotNull { element ->
        val wrapper = element as? JsonObject ?: return@mapNotNull null
        val track = wrapper.obj("track")?.obj("data")
            ?: wrapper.obj("track")
            ?: wrapper.obj("data")
            ?: return@mapNotNull null
        val uri = track.str("uri") ?: track.str("_uri") ?: return@mapNotNull null
        if (!uri.startsWith("spotify:track:")) return@mapNotNull null
        val id = uri.removePrefix("spotify:track:")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) } ?: return@mapNotNull null
        val title = track.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artistNames = track.obj("artists")?.array("items").orEmpty().mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            a.obj("profile")?.str("name") ?: a.obj("data")?.obj("profile")?.str("name")
        }.ifEmpty { albumArtists }
        SpotifyCatalogItem(
            kind = SpotifyCatalogKind.TRACK,
            id = id,
            title = title,
            subtitle = artistNames.joinToString(", ").ifBlank { null },
            artworkUrl = artwork,
            durationMs = track.obj("duration")?.long("totalMilliseconds")
                ?: track.long("durationMs") ?: track.long("duration_ms"),
            explicit = track.obj("contentRating")?.str("label") == "EXPLICIT"
                || (track["explicit"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
    val total = tracks.long("totalCount")
    val more = if (total != null) offset + rawItems.size < total
        else rawItems.isNotEmpty() && rawItems.size >= limit
    return SpotifyCatalogPage(items.distinctBy { it.id }, more)
}

private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray
private fun JsonObject.str(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
private fun List<JsonElement>.firstImageUrl(): String? = asSequence()
    .mapNotNull { (it as? JsonObject)?.str("url") }.firstOrNull()
