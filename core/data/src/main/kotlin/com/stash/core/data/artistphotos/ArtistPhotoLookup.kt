package com.stash.core.data.artistphotos

import android.content.Context
import com.stash.core.data.musicbrainz.MusicBrainzClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort, on-demand photo selection. Existing YouTube artwork remains the
 * first-paint fallback. No AI, no catalog-wide prefetch, no database migration.
 *
 * Fanart.tv publishes upload dates and community likes, but NOT verified
 * capture dates. Consequently its undated band photos are rejected by policy;
 * we must not misrepresent a historic lineup as the current one.
 */
@Singleton
class ArtistPhotoLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicBrainz: MusicBrainzClient,
    private val fanartConfig: FanartConfig,
    okHttp: OkHttpClient,
) {
    private val http = okHttp.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences("sonora_artist_photos_v1", Context.MODE_PRIVATE) }

    suspend fun find(artistName: String): String? = withContext(Dispatchers.IO) {
        val key = fanartConfig.apiKey.trim()
        if (key.isEmpty() || artistName.isBlank()) return@withContext null
        val cacheId = artistName.trim().lowercase()
        val checkedAt = prefs.getLong("$cacheId.checked", 0L)
        val cachedUrl = prefs.getString("$cacheId.url", null)
        val ttl = if (cachedUrl == null) NEGATIVE_CACHE_TTL_MS else CACHE_TTL_MS
        if (System.currentTimeMillis() - checkedAt in 0 until ttl) {
            return@withContext prefs.getString("$cacheId.url", null)
        }

        val resolved = try {
            val mb = musicBrainz.searchByName(artistName) ?: return@withContext null
            val mbid = mb["id"]?.jsonPrimitive?.content ?: return@withContext null
            if (!UUID_PATTERN.matches(mbid)) return@withContext null
            val type = mb["type"]?.jsonPrimitive?.content
            if (type != "Person" && type != "Group") return@withContext null

            val isGroup = type == "Group"
            val floor = if (isGroup) {
                musicBrainz.lookupMemberRelations(mbid)
                    ?.let(ArtistPhotoRules::currentLineupFloor)
            } else null

            // All Fanart candidates currently have unknown capture dates.
            // Enforce lineup/date gate BEFORE calling an API whose entries
            // cannot pass it, rather than spending bandwidth unnecessarily.
            if (isGroup) null
            else chooseFanartPhoto(mbid, key, floor, isGroup)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null // do not negatively cache network faults
        }

        prefs.edit().putString("$cacheId.url", resolved)
            .putLong("$cacheId.checked", System.currentTimeMillis()).apply()
        resolved
    }

    private fun chooseFanartPhoto(
        mbid: String, apiKey: String, floor: java.time.LocalDate?, isGroup: Boolean,
    ): String? {
        val request = Request.Builder()
            .url("https://webservice.fanart.tv/v3.2/music/$mbid")
            .header("api-key", apiKey)
            .build()
        val root = http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw java.io.IOException("Fanart returned HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            json.parseToJsonElement(body).jsonObject
        }

        val candidates = listOf("artistthumb", "artistbackground").flatMap { category ->
            root[category]?.jsonArray?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val width = obj["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                ArtistPhotoCandidate(
                    url = url, category = category, width = width, height = height,
                    likes = obj["likes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    // Fanart "added" is an upload timestamp, never a photo date.
                    capturedAt = null,
                )
            }.orEmpty()
        }
        return ArtistPhotoRules.select(candidates, isGroup, floor)?.url
    }

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val NEGATIVE_CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}
