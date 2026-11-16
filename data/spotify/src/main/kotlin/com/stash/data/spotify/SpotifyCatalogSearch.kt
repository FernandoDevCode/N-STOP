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
) {
    enum class Kind { TRACK, ARTIST, ALBUM }
}

internal fun parseSpotifyCatalog(body: String): List<SpotifyCatalogItem> {
    val root = Json.parseToJsonElement(body).jsonObject
    val buckets = SpotifyCatalogItem.Kind.entries.associateWith { kind ->
        (root[kind.name.lowercase() + "s"] as? JsonObject)?.get("items") as? JsonArray
    }
    if (buckets.values.all { it == null }) throw IOException("Missing Spotify search result buckets")

    // The API ranks each type separately. Weave equal positions in request order.
    return buildList {
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
                add(
                    SpotifyCatalogItem(
                        kind = kind,
                        id = id,
                        uri = item.text("uri"),
                        title = title,
                        artists = (item["artists"] as? JsonArray).orEmpty()
                            .mapNotNull { (it as? JsonObject)?.text("name") }
                            .joinToString(", ").takeIf { it.isNotBlank() },
                        artworkUrl = (artwork?.get("images") as? JsonArray).orEmpty()
                            .firstNotNullOfOrNull { (it as? JsonObject)?.text("url") },
                        durationMs = (item["duration_ms"] as? JsonPrimitive)?.longOrNull
                            ?.takeIf { it >= 0 },
                        isExplicit = (item["explicit"] as? JsonPrimitive)?.booleanOrNull == true,
                        rank = index * buckets.size + kind.ordinal,
                    ),
                )
            }
        }
    }
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
