package com.stash.core.data.artistphotos

import android.content.Context
import com.stash.core.data.musicbrainz.MusicBrainzClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional first-tier hero lookup. No generative AI or per-artist allowlist.
 *
 * MusicBrainz (exact name + URL relation) -> Wikidata (P154/P18) ->
 * Wikimedia Commons (real dimensions, MIME and bounded thumbnail).
 * Only curated, eligible images win. Every miss returns control to the
 * UNCHANGED ArtistPhotoLookup (formation floor + photo rating).
 */
@Singleton
class ArtistIdentityLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicBrainz: MusicBrainzClient,
    okHttp: OkHttpClient,
) {
    private val http = okHttp.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences("sonora_artist_identity_v1", Context.MODE_PRIVATE) }

    suspend fun find(artistName: String): ArtistIdentityImage? = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isEmpty()) return@withContext null
        val key = name.lowercase(Locale.ROOT)
        val checked = prefs.getLong("$key.checked", 0L)
        val cachedUrl = prefs.getString("$key.url", null)
        val ttl = if (cachedUrl == null) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
        if (System.currentTimeMillis() - checked in 0 until ttl) {
            val kind = prefs.getString("$key.kind", null)?.let {
                runCatching { ArtistIdentityKind.valueOf(it) }.getOrNull()
            }
            return@withContext if (cachedUrl != null && kind != null)
                ArtistIdentityImage(cachedUrl, kind) else null
        }

        val selected = try {
            resolve(name)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // A timeout is NOT a confident absence of a representative image.
            // Do not negatively cache faults: the legacy photo still loads now.
            return@withContext null
        }
        prefs.edit()
            .putString("$key.url", selected?.url)
            .putString("$key.kind", selected?.kind?.name)
            .putLong("$key.checked", System.currentTimeMillis())
            .apply()
        selected
    }

    private suspend fun resolve(name: String): ArtistIdentityImage? {
        val artist = musicBrainz.searchByName(name) ?: return null
        val qid = ArtistIdentityRules.wikidataId(artist, name) ?: return null
        val entity = getJson("https://www.wikidata.org/wiki/Special:EntityData/$qid.json")
        // The official emblem is sometimes represented by a distinct Wikidata
        // entity (P2238), as with the Rolling Stones. Resolve only this explicit
        // link, never a guessed similar-looking item or a search by name.
        val officialSymbolId = ArtistIdentityRules.officialSymbolId(entity, qid)
        if (officialSymbolId != null) {
            val symbolEntity = runCatching {
                getJson("https://www.wikidata.org/wiki/Special:EntityData/$officialSymbolId.json")
            }.getOrNull()
            if (symbolEntity != null) {
                for ((file, _) in ArtistIdentityRules.files(symbolEntity, officialSymbolId)) {
                    val candidate = commonsCandidate(file, ArtistIdentityKind.SYMBOL) ?: continue
                    ArtistIdentityRules.select(listOf(candidate))?.let { return it }
                }
            }
        }
        for ((filename, kind) in ArtistIdentityRules.files(entity, qid)) {
            val candidate = commonsCandidate(filename, kind) ?: continue
            // Ordered: compact symbol first, then the source's main representative photo.
            ArtistIdentityRules.select(listOf(candidate))?.let { return it }
        }
        return null
    }

    private fun commonsCandidate(file: String, kind: ArtistIdentityKind): ArtistIdentityCandidate? {
        val title = URLEncoder.encode("File:$file", "UTF-8")
        val body = getJson("https://commons.wikimedia.org/w/api.php" +
            "?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cthumbmime" +
            "&iiurlwidth=1280&titles=$title")
        val pages = (body["query"] as? JsonObject)?.get("pages") as? JsonObject ?: return null
        val imageInfo = pages.values.asSequence().mapNotNull { page ->
            ((page as? JsonObject)?.get("imageinfo"))?.jsonArray?.firstOrNull() as? JsonObject
        }.firstOrNull() ?: return null
        val mime = (imageInfo["mime"] as? JsonPrimitive)?.content ?: return null
        val thumb = (imageInfo["thumburl"] as? JsonPrimitive)?.content
        val thumbMime = (imageInfo["thumbmime"] as? JsonPrimitive)?.content
        // Android's standard Coil loader does not promise native SVG support.
        // For SVGs require an actual Commons-rendered raster thumbnail.
        val svgRaster = mime == "image/svg+xml" && thumb != null &&
            thumbMime in setOf("image/png", "image/jpeg", "image/webp")
        if (mime == "image/svg+xml" && !svgRaster) return null
        val url = if (mime == "image/svg+xml") thumb else
            thumb ?: (imageInfo["url"] as? JsonPrimitive)?.content
        return ArtistIdentityCandidate(
            url = url ?: return null,
            kind = kind,
            width = (imageInfo["width"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null,
            height = (imageInfo["height"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null,
            mime = mime,
            hasRasterThumbnail = svgRaster,
        )
    }

    private fun getJson(url: String): JsonObject {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Sonora/0.9 (artist identity; github.com/FernandoDevCode/N-STOP)")
            .header("Accept", "application/json")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Identity source HTTP ${response.code}")
            val content = response.body?.string() ?: throw java.io.IOException("Empty identity response")
            json.parseToJsonElement(content).jsonObject
        }
    }

    private companion object {
        const val POSITIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val NEGATIVE_TTL_MS = 24L * 60 * 60 * 1000
    }
}
