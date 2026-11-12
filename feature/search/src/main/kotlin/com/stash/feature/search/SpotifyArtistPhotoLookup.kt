package com.stash.feature.search

import com.stash.core.data.musicbrainz.MusicBrainzClient
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyArtistPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify is the primary photo authority, including without a connected Spotify account.
 *
 * First visit: authenticated Spotify exact-name search, or a uniquely matching
 * MusicBrainz artist with one verified Spotify URL -> official Spotify oEmbed.
 * Subsequent visits: query the PINNED Spotify ID (official artist endpoint or
 * oEmbed), never search the artist name again. No name-keyed photo caching.
 *
 * On error, return null. The selected-photo repository keeps the previously
 * verified portrait; neither a transient error nor Deezer can overwrite it.
 */
@Singleton
class SpotifyArtistPhotoLookup @Inject constructor(
    private val spotifyApi: SpotifyApiClient,
    private val musicBrainz: MusicBrainzClient,
    okHttpClient: OkHttpClient,
) {
    private val http = okHttpClient.newBuilder().callTimeout(7, TimeUnit.SECONDS).build()

    suspend fun find(name: String): SpotifyArtistPhoto? = find(name, null)

    suspend fun find(name: String, pinnedSpotifyUrl: String?): SpotifyArtistPhoto? =
        withContext(Dispatchers.IO) {
            if (name.isBlank()) return@withContext null
            try {
                if (pinnedSpotifyUrl != null) {
                    val id = artistIdFromUrl(pinnedSpotifyUrl) ?: return@withContext null
                    // A pinned identity survives name homonyms and changing ranking.
                    spotifyApi.getArtistPhotoById(id, name)
                        ?: officialOEmbedPhoto(name, "https://open.spotify.com/artist/$id")
                } else {
                    spotifyApi.searchArtistPhoto(name) ?: run {
                        val relations = musicBrainz.searchUniqueByName(name)
                            ?: return@withContext null
                        val mbName = relations["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (normalize(mbName) != normalize(name)) return@withContext null
                        val urls = relations["relations"]?.jsonArray.orEmpty()
                            .mapNotNull { rel ->
                                val url = rel.jsonObject["url"]?.jsonObject
                                    ?.get("resource")?.jsonPrimitive?.contentOrNull
                                val id = url?.let(::artistIdFromUrl) ?: return@mapNotNull null
                                "https://open.spotify.com/artist/$id"
                            }.distinct()
                        if (urls.size != 1) return@withContext null
                        val id = artistIdFromUrl(urls.single()) ?: return@withContext null
                        spotifyApi.getArtistPhotoById(id, name)
                            ?: officialOEmbedPhoto(name, urls.single())
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
        }

    private fun officialOEmbedPhoto(name: String, spotifyArtistUrl: String): SpotifyArtistPhoto? {
        val id = artistIdFromUrl(spotifyArtistUrl) ?: return null
        val url = "https://open.spotify.com/oembed?url=" +
            URLEncoder.encode("https://open.spotify.com/artist/$id", "UTF-8")
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty())
            val provider = json.optString("provider_name")
            val title = json.optString("title")
            val image = json.optString("thumbnail_url")
            if (provider != "Spotify" || normalize(title) != normalize(name) ||
                !image.startsWith("https://i.scdn.co/image/")) return@use null
            SpotifyArtistPhoto(image, "https://open.spotify.com/artist/$id")
        }
    }

    private fun artistIdFromUrl(raw: String): String? = try {
        val url = URI(raw)
        if (url.scheme != "https" || url.host != "open.spotify.com" ||
            url.userInfo != null || url.port != -1) null
        else url.path.trimEnd('/').removePrefix("/artist/")
            .takeIf { url.path.startsWith("/artist/") &&
                it.matches(Regex("[a-zA-Z0-9]{22}")) }
    } catch (_: Exception) { null }

    private fun normalize(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
