package com.stash.core.data.musicbrainz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        const val USER_AGENT = "Stash/1.0 ( https://github.com/rawnaldclark/Stash )"
        const val MIN_INTERVAL_MS = 1000L
    }
}
