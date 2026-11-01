package com.stash.core.data.artistphotos

import android.content.Context
import android.util.Log
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
    private val fanartConfig: FanartConfig,
    okHttp: OkHttpClient,
) {
    private val http = okHttp.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences("sonora_artist_identity_v2", Context.MODE_PRIVATE) }

    suspend fun find(artistName: String): ArtistIdentityImage? = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isEmpty()) return@withContext null
        val key = name.lowercase(Locale.ROOT)
        val checked = prefs.getLong("$key.checked", 0L)
        val cachedUrl = prefs.getString("$key.url", null)
        // The original V19 cached unsuccessful lookups for a full day. A slow
        // MusicBrainz/Wikidata response looked like "no identity" on repeat visits.
        // V2 only caches SUCCESS, so installing an update is enough to retry.
        if (cachedUrl != null && System.currentTimeMillis() - checked in 0 until POSITIVE_TTL_MS) {
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
        } catch (error: Exception) {
            // A timeout is NOT a confident absence of a representative image.
            // Do not negatively cache faults: the legacy photo still loads now.
            Log.w("ArtistIdentityLookup", "Identity request failed for $name", error)
            return@withContext null
        }
        if (selected != null) {
            prefs.edit()
                .putString("$key.url", selected.url)
                .putString("$key.kind", selected.kind.name)
                .putLong("$key.checked", System.currentTimeMillis())
                .apply()
            Log.d("ArtistIdentityLookup", "Selected ${selected.kind} for $name")
        }
        selected
    }

    private suspend fun resolve(name: String): ArtistIdentityImage? {
        val artist = musicBrainz.searchByName(name) ?: return null
        val mbid = (artist["id"] as? JsonPrimitive)?.content ?: return null
        if (!MBID_REGEX.matches(mbid)) return null
        // Independently sourced symbols always precede generic portraits.
        val qid = ArtistIdentityRules.wikidataId(artist, name)
        val entity = qid?.let { id ->
            runCatching { getJson("https://www.wikidata.org/wiki/Special:EntityData/$id.json") }
                .getOrNull()
        }

        if (qid != null && entity != null) {
            val symbolId = ArtistIdentityRules.officialSymbolId(entity, qid)
            if (symbolId != null) {
                val symbolData = runCatching {
                    getJson("https://www.wikidata.org/wiki/Special:EntityData/$symbolId.json")
                }.getOrNull()
                if (symbolData != null) {
                    for ((file, _) in ArtistIdentityRules.files(symbolData, symbolId)) {
                        safeCommons(file, ArtistIdentityKind.SYMBOL)?.let { return it }
                    }
                }
            }
            // Treat both emblem-style marks and wide wordmarks as valid identities.
            for ((file, kind) in ArtistIdentityRules.files(entity, qid)) {
                if (kind == ArtistIdentityKind.SYMBOL) {
                    safeCommons(file, kind)?.let { return it }
                }
            }
        }

        // Fanart's community-rated official artist logos cover many musicians
        // whose Wikidata entry only has an ordinary portrait, or no image.
        val key = fanartConfig.apiKey.trim()
        if (key.isNotBlank()) {
            val fanart = runCatching {
                getJson("https://webservice.fanart.tv/v3.2/music/$mbid", apiKey = key)
            }.getOrNull()
            if (fanart != null) {
                ArtistIdentityRules.fanartLogo(fanart, name, mbid)?.let { return it }
            }
        }

        // Finally use the explicitly curated Wikidata representative photo.
        // If none is eligible, the ViewModel invokes the UNMODIFIED photo rules.
        if (qid != null && entity != null) {
            for ((file, kind) in ArtistIdentityRules.files(entity, qid)) {
                if (kind == ArtistIdentityKind.PHOTO) {
                    safeCommons(file, kind)?.let { return it }
                }
            }
        }
        return null
    }

    private fun safeCommons(file: String, kind: ArtistIdentityKind): ArtistIdentityImage? =
        runCatching {
            commonsCandidate(file, kind)?.let { ArtistIdentityRules.select(listOf(it)) }
        }.getOrNull()

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

    private fun getJson(url: String, apiKey: String? = null): JsonObject {
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Sonora/0.9 (artist identity; github.com/FernandoDevCode/N-STOP)")
            .header("Accept", "application/json")
        if (apiKey != null) builder.header("api-key", apiKey)
        val request = builder.build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Identity source HTTP ${response.code}")
            val content = response.body?.string() ?: throw java.io.IOException("Empty identity response")
            json.parseToJsonElement(content).jsonObject
        }
    }

    private companion object {
        const val POSITIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        val MBID_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}
