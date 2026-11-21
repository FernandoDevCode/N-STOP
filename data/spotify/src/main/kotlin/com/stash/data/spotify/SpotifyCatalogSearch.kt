package com.stash.data.spotify

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

data class SpotifyCatalogItem(
    val kind: Kind,
    val id: String,
    val uri: String?,
    val title: String,
    val artists: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isExplicit: Boolean,
    val rank: Int,
    val artistIds: List<String> = emptyList(),
    val albumTitle: String? = null,
    val releaseYear: String? = null,
    val releaseType: String? = null,
    val artistNames: List<String> = emptyList(),
) {
    enum class Kind { TRACK, ARTIST, ALBUM }
}

data class SpotifyCatalogPage(
    val items: List<SpotifyCatalogItem>,
    val returnedKinds: Set<SpotifyCatalogItem.Kind>,
    val nextOffsets: Map<SpotifyCatalogItem.Kind, Int>,
)

internal fun parseSpotifyCatalog(body: String, requestedOffset: Int = 0): SpotifyCatalogPage {
    val root = Json.parseToJsonElement(body).jsonObject
    val pages = SpotifyCatalogItem.Kind.entries.associateWith { kind ->
        root[kind.name.lowercase() + "s"] as? JsonObject
    }
    val buckets = pages.mapValues { (_, page) -> page?.get("items") as? JsonArray }
    if (buckets.values.all { it == null }) throw IOException("Missing Spotify search result buckets")

    // Keep API positions even when individual rows are missing or malformed.
    val items = buildList {
        val size = buckets.values.maxOf { it?.size ?: 0 }
        for (index in 0 until size) {
            for ((kind, items) in buckets) {
                val item = items?.getOrNull(index) as? JsonObject ?: continue
                val id = item.text("id") ?: continue
                val title = item.text("name") ?: continue
                val artwork = if (kind == SpotifyCatalogItem.Kind.TRACK) {
                    item["album"] as? JsonObject
                } else {
                    item
                }
                val artists = (item["artists"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val artistNames = artists.mapNotNull { it.text("name") }
                add(
                    SpotifyCatalogItem(
                        kind = kind,
                        id = id,
                        uri = item.text("uri"),
                        title = title,
                        artists = artistNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        artworkUrl = (artwork?.get("images") as? JsonArray).orEmpty()
                            .firstNotNullOfOrNull { (it as? JsonObject)?.text("url") },
                        durationMs = (item["duration_ms"] as? JsonPrimitive)?.longOrNull
                            ?.takeIf { it >= 0 },
                        isExplicit = (item["explicit"] as? JsonPrimitive)?.booleanOrNull == true,
                        rank = (requestedOffset + index) * buckets.size + kind.ordinal,
                        artistIds = artists.mapNotNull { it.text("id") },
                        albumTitle = artwork?.text("name"),
                        releaseYear = artwork?.text("release_date")?.take(4),
                        releaseType = artwork?.text("album_type"),
                        artistNames = artistNames,
                    ),
                )
            }
        }
    }
    val returnedKinds = buckets.filterValues { it != null }.keys
    val nextOffsets = buildMap {
        for (kind in returnedKinds) {
            val page = pages.getValue(kind) ?: continue
            if (page.text("next") == null) continue
            val offset = (page["offset"] as? JsonPrimitive)?.intOrNull ?: requestedOffset
            val limit = (page["limit"] as? JsonPrimitive)?.intOrNull ?: continue
            val next = offset.toLong() + limit
            // Rebuild authenticated requests locally instead of following a response URL.
            if (offset == requestedOffset && next > offset && next <= 1_000) put(kind, next.toInt())
        }
    }
    return SpotifyCatalogPage(items, returnedKinds, nextOffsets)
}


/**
 * Parses the Spotify Web Player's searchDesktop persisted-query response, not REST /v1/search.
 * Missing buckets remain missing (the universal search engine falls back to YouTube Music);
 * a present empty bucket is a legitimate empty response.
 */
internal fun parseSpotifyPartnerCatalog(
    body: String,
    requestedOffset: Int = 0,
    requestedLimit: Int = 10,
    requestedKind: SpotifyCatalogItem.Kind? = null,
): SpotifyCatalogPage {
    val root = Json.parseToJsonElement(body) as? JsonObject
        ?: throw IOException("Spotify partner catalog did not return a JSON object")
    if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) {
        throw IOException("Spotify partner catalog returned GraphQL errors")
    }
    val data = root["data"] as? JsonObject
        ?: throw IOException("Spotify partner catalog has no data")
    val search = (data["search"] as? JsonObject) ?: (data["searchV2"] as? JsonObject)
        ?: throw IOException("Spotify partner catalog has no search container")
    val kinds = requestedKind?.let(::listOf) ?: SpotifyCatalogItem.Kind.entries
    val pages = kinds.associateWith { kind ->
        val bucket = kind.name.lowercase(java.util.Locale.ROOT) + "s"
        (search[bucket] as? JsonObject) ?: (search[bucket + "V2"] as? JsonObject)
    }
    val buckets = pages.mapValues { (_, page) -> page?.get("items") as? JsonArray }
    if (buckets.values.all { it == null }) throw IOException("Missing Spotify partner search buckets")
    val items = buildList {
        val max = buckets.values.maxOf { it?.size ?: 0 }
        for (index in 0 until max) {
            for (kind in kinds) {
                val wrapper = buckets[kind]?.getOrNull(index) as? JsonObject ?: continue
                val item = wrapper.partnerPayload(kind) ?: continue
                val id = item.partnerId(kind) ?: continue
                val name = (item.text("name")
                    ?: (item["profile"] as? JsonObject)?.text("name")) ?: continue
                val album = if (kind == SpotifyCatalogItem.Kind.TRACK) {
                    (item["albumOfTrack"] as? JsonObject) ?: (item["album"] as? JsonObject)
                } else item
                val credits = item.partnerCredits()
                val image = when (kind) {
                    SpotifyCatalogItem.Kind.TRACK, SpotifyCatalogItem.Kind.ALBUM -> album?.partnerCover()
                    SpotifyCatalogItem.Kind.ARTIST -> item.partnerArtistImage()
                }
                val releaseDate = album?.get("date") as? JsonObject
                val releaseType = album?.text("type")?.lowercase(java.util.Locale.ROOT)
                    ?.takeIf { it in setOf("album", "single", "compilation") }
                    ?: album?.text("albumType")?.lowercase(java.util.Locale.ROOT)
                        ?.takeIf { it in setOf("album", "single", "compilation") }
                val duration = ((item["duration"] as? JsonObject)?.get("totalMilliseconds")
                    as? JsonPrimitive)?.longOrNull
                    ?: (item["duration_ms"] as? JsonPrimitive)?.longOrNull
                val label = (item["contentRating"] as? JsonObject)?.text("label")
                add(SpotifyCatalogItem(
                    kind = kind,
                    id = id,
                    uri = item.text("uri"),
                    title = name,
                    artists = credits.map { it.first }.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    artworkUrl = image,
                    durationMs = duration?.takeIf { it >= 0 },
                    isExplicit = label.equals("EXPLICIT", ignoreCase = true) ||
                        (item["explicit"] as? JsonPrimitive)?.booleanOrNull == true,
                    rank = (requestedOffset + index) * 3 + kind.ordinal,
                    artistIds = credits.mapNotNull { it.second },
                    albumTitle = album?.text("name"),
                    releaseYear = releaseDate?.get("year")?.let { (it as? JsonPrimitive)?.contentOrNull }
                        ?: album?.text("release_date")?.take(4),
                    releaseType = releaseType,
                    artistNames = credits.map { it.first },
                ))
            }
        }
    }
    val returnedKinds = buckets.filterValues { it != null }.keys
    val nextOffsets = buildMap {
        for (kind in returnedKinds) {
            val page = pages[kind] ?: continue
            val paging = page["pagingInfo"] as? JsonObject
            val explicitNext = (paging?.get("nextOffset") as? JsonPrimitive)?.intOrNull
                ?: (page["nextOffset"] as? JsonPrimitive)?.intOrNull
            val total = (page["totalCount"] as? JsonPrimitive)?.intOrNull
                ?: (page["total"] as? JsonPrimitive)?.intOrNull
            val next = requestedOffset + requestedLimit
            val hasMore = if (explicitNext != null) explicitNext == next
                else total != null && total > next
            if (hasMore && next > requestedOffset && next <= 1_000) put(kind, next)
        }
    }
    return SpotifyCatalogPage(items, returnedKinds, nextOffsets)
}

private fun JsonObject.partnerPayload(kind: SpotifyCatalogItem.Kind): JsonObject? {
    val nested = when (kind) {
        SpotifyCatalogItem.Kind.TRACK -> this["track"] as? JsonObject
        else -> null
    }
    return nested
        ?: (this["data"] as? JsonObject)
        ?: ((this["item"] as? JsonObject)?.get("data") as? JsonObject)
        ?: this.takeIf { it["uri"] is JsonPrimitive }
}

private fun JsonObject.partnerId(kind: SpotifyCatalogItem.Kind): String? {
    val prefix = "spotify:" + kind.name.lowercase(java.util.Locale.ROOT) + ":"
    return text("uri")?.takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.takeIf { it.isNotBlank() && ':' !in it }
}

private fun JsonObject.partnerCredits(): List<Pair<String, String?>> {
    val artists = (this["artists"] as? JsonObject)?.get("items") as? JsonArray
        ?: this["artists"] as? JsonArray
        ?: return emptyList()
    return artists.mapNotNull { element ->
        val wrapper = element as? JsonObject ?: return@mapNotNull null
        val artist = wrapper.partnerPayload(SpotifyCatalogItem.Kind.ARTIST)
            ?: return@mapNotNull null
        val name = (artist["profile"] as? JsonObject)?.text("name")
            ?: artist.text("name") ?: return@mapNotNull null
        name to artist.partnerId(SpotifyCatalogItem.Kind.ARTIST)
    }
}

private fun JsonObject.partnerCover(): String? {
    val cover = (this["coverArt"] as? JsonObject) ?: (this["visuals"] as? JsonObject)
    val sources = cover?.get("sources") as? JsonArray
        ?: ((cover?.get("data") as? JsonObject)?.get("sources") as? JsonArray)
    return sources.partnerBestImage()
}

private fun JsonObject.partnerArtistImage(): String? {
    val visuals = this["visuals"] as? JsonObject ?: return null
    val avatar = visuals["avatarImage"] as? JsonObject ?: return null
    val sources = avatar["sources"] as? JsonArray
        ?: ((avatar["data"] as? JsonObject)?.get("sources") as? JsonArray)
    return sources.partnerBestImage()
}

/** Use the largest published HTTPS source, not an arbitrarily small thumbnail. */
private fun JsonArray?.partnerBestImage(): String? =
    this?.mapNotNull { element ->
        val image = element as? JsonObject ?: return@mapNotNull null
        val url = image.text("url")?.takeIf { it.startsWith("https://") }
            ?: return@mapNotNull null
        val width = (image["width"] as? JsonPrimitive)?.intOrNull ?: 0
        url to width
    }?.maxByOrNull { it.second }?.first

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal data class CatalogResponse(val code: Int, val retryAfterSeconds: Long?, val body: String?)

internal suspend fun Call.awaitCatalogResponse(): CatalogResponse =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        CatalogResponse(
                            it.code,
                            it.header("Retry-After")?.toLongOrNull(),
                            // GraphQL persisted-query misses are returned as HTTP 400 JSON.
                            if (it.isSuccessful || it.code == 400) it.body?.string() else null,
                        )
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }
