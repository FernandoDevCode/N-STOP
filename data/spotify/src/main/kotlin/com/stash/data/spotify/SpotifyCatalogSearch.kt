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
                            if (it.isSuccessful) it.body?.string() else null,
                        )
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }
