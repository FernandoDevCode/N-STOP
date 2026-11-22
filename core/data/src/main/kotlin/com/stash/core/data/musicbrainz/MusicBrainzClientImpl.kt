package com.stash.core.data.musicbrainz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-direct MusicBrainz client. No proxy: MusicBrainz rate-limits per IP
 * with no API key, so per-device residential IPs are naturally compliant. A
 * simple on-device gate keeps requests ~1s apart (their published limit).
 */
@Singleton
class MusicBrainzClientImpl @Inject constructor(
    okHttpClient: OkHttpClient,
) : MusicBrainzClient {
    // MusicBrainz answers in 1-7 s on a good day. A coroutine timeout cannot
    // interrupt a blocking call, so the bound lives on the call itself.
    private val http: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val rateGate = Mutex()
    @Volatile private var lastCallMs = 0L

    override suspend fun lookupUrlRels(mbid: String): JsonObject? = runCatching {
        get("artist/$mbid?inc=url-rels&fmt=json")
    }.getOrNull()

    override suspend fun lookupMemberRelations(mbid: String): JsonObject? = runCatching {
        if (!Regex("^[0-9a-fA-F-]{36}$").matches(mbid)) return null
        get("artist/$mbid?inc=artist-rels&fmt=json")
    }.getOrNull()

    override suspend fun searchByName(name: String): JsonObject? = runCatching {
        val query = URLEncoder.encode("artist:\"${escapeLucene(name)}\"", "UTF-8")
        val body = get("artist?query=$query&fmt=json") ?: return null
        val first = body["artists"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val score = first["score"]?.jsonPrimitive?.int ?: return null
        val type = first["type"]?.jsonPrimitive?.content
        if (score < 95 || type !in setOf("Person", "Group")) return null
        val mbid = first["id"]?.jsonPrimitive?.content ?: return null
        lookupUrlRels(mbid)
    }.getOrNull()

    /** A name alone never proves identity if multiple MusicBrainz artists match. */
    override suspend fun searchUniqueByName(name: String): JsonObject? {
        if (name.isBlank()) return null
        return try {
            val query = URLEncoder.encode("artist:\"${escapeLucene(name)}\"", "UTF-8")
            val body = get("artist?query=$query&limit=15&fmt=json") ?: return null
            fun normalize(text: String) = text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
            val matches = body["artists"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { entry ->
                    entry["score"]?.jsonPrimitive?.int?.let { it >= 95 } == true &&
                        entry["type"]?.jsonPrimitive?.content in setOf("Person", "Group") &&
                        normalize(entry["name"]?.jsonPrimitive?.content.orEmpty()) == normalize(name)
                }
                .mapNotNull { it["id"]?.jsonPrimitive?.content }
                .distinct()
            if (matches.size != 1) return null
            lookupUrlRels(matches.single())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }


    override suspend fun albumGroups(artistName: String): List<MusicBrainzAlbumGroup>? {
        if (artistName.isBlank()) return emptyList()
        return try {
            val artistId = searchUniqueByName(artistName)?.text("id") ?: return null
            val body = get("release-group?artist=$artistId&type=album&limit=100&fmt=json") ?: return null
            parseMusicBrainzGroups(body)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun editions(groupId: String): List<MusicBrainzEdition>? {
        if (!validMbid(groupId)) return null
        return try {
            val collected = mutableListOf<MusicBrainzEdition>()
            var offset = 0
            // A browse page may contain far fewer than 100 releases (the MB API's
            // 500-track cap). Advance by rows actually returned to preserve old editions.
            repeat(8) {
                val body = get("release?release-group=$groupId&limit=100&offset=$offset&fmt=json")
                    ?: return if (collected.isEmpty()) null else sortMusicBrainzEditions(collected)
                val page = body.array("releases").orEmpty()
                if (page.isEmpty()) return sortMusicBrainzEditions(collected)
                collected += parseMusicBrainzEditions(body)
                offset += page.size
                val total = (body["release-count"] as? JsonPrimitive)?.content?.toIntOrNull()
                if (total != null && offset >= total) return sortMusicBrainzEditions(collected)
                if (total == null && page.size < 100) return sortMusicBrainzEditions(collected)
            }
            sortMusicBrainzEditions(collected)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun releaseTracks(releaseId: String): List<MusicBrainzEditionTrack>? {
        if (!validMbid(releaseId)) return null
        return try {
            val body = get("release/$releaseId?inc=recordings&fmt=json") ?: return null
            parseMusicBrainzTracks(body)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    /** GET [path] under the MB base with our UA + rate gate; null on non-2xx. */
    private suspend fun get(path: String): JsonObject? {
        throttle()
        val req = Request.Builder()
            .url("$BASE$path")
            .header("User-Agent", USER_AGENT)
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.let { json.parseToJsonElement(it).jsonObject }
            }
        }
    }

    /** Serialize + space calls ≥ MIN_INTERVAL_MS apart. */
    private suspend fun throttle() = rateGate.withLock {
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastCallMs)
        if (wait > 0) delay(wait)
        lastCallMs = System.currentTimeMillis()
    }

    private companion object {
        const val BASE = "https://musicbrainz.org/ws/2/"
        private const val CALL_TIMEOUT_SECONDS = 5L
        const val USER_AGENT = "Sonora/0.9 ( https://github.com/FernandoDevCode/N-STOP )"
        const val MIN_INTERVAL_MS = 1000L
    }
}

/** Pure parsers preserve release identity, avoid making MusicBrainz IDs into streams. */
private fun JsonObject.text(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
private fun validMbid(id: String) =
    Regex("^[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}$").matches(id)

internal fun parseMusicBrainzGroups(body: JsonObject): List<MusicBrainzAlbumGroup> =
    body.array("release-groups").orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.text("id")?.takeIf(::validMbid) ?: return@mapNotNull null
        val title = obj.text("title") ?: return@mapNotNull null
        MusicBrainzAlbumGroup(id, title, obj.text("first-release-date")?.take(4))
    }.distinctBy { it.id }.sortedWith(compareByDescending<MusicBrainzAlbumGroup> { it.year.orEmpty() }.thenBy { it.title })

internal fun parseMusicBrainzEditions(body: JsonObject): List<MusicBrainzEdition> =
    sortMusicBrainzEditions(body.array("releases").orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.text("id")?.takeIf(::validMbid) ?: return@mapNotNull null
        MusicBrainzEdition(
            id, obj.text("title") ?: return@mapNotNull null,
            obj.text("disambiguation"), obj.text("date"), obj.text("country"),
        )
    })

private fun sortMusicBrainzEditions(releases: List<MusicBrainzEdition>): List<MusicBrainzEdition> =
    releases.distinctBy { it.id }.sortedWith(
        compareByDescending<MusicBrainzEdition> {
            it.description?.contains("anniversary", ignoreCase = true) == true ||
                it.title.contains("anniversary", ignoreCase = true)
        }.thenBy { it.date.orEmpty() }.thenBy { it.title }
    )

internal fun parseMusicBrainzTracks(body: JsonObject): List<MusicBrainzEditionTrack> =
    buildList {
        body.array("media").orEmpty().forEachIndexed { mediaIndex, medium ->
            val disc = (medium as? JsonObject) ?: return@forEachIndexed
            disc.array("tracks").orEmpty().forEachIndexed { trackIndex, element ->
                val track = element as? JsonObject ?: return@forEachIndexed
                val title = track.text("title") ?: return@forEachIndexed
                val recording = track["recording"] as? JsonObject
                add(MusicBrainzEditionTrack(
                    position = (track["position"] as? JsonPrimitive)?.content?.toIntOrNull() ?: trackIndex + 1,
                    disc = (disc["position"] as? JsonPrimitive)?.content?.toIntOrNull() ?: mediaIndex + 1,
                    title = title,
                    durationMs = (track["length"] as? JsonPrimitive)?.longOrNull
                        ?: (recording?.get("length") as? JsonPrimitive)?.longOrNull,
                    recordingId = recording?.text("id")?.takeIf(::validMbid),
                ))
            }
        }
    }
