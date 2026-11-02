package com.stash.feature.search

import android.content.Context
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyArtistPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort official Spotify photo lookup. Positive results are cached per device.
 * A transient 429, missing Spotify login or API outage never overwrites a good result.
 */
@Singleton
class SpotifyArtistPhotoLookup @Inject constructor(
    private val spotifyApi: SpotifyApiClient,
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences("sonora_spotify_artist_photos_v1", Context.MODE_PRIVATE) }

    suspend fun find(name: String): SpotifyArtistPhoto? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        val key = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        val lastUrl = prefs.getString("$key.image", null)
        val lastArtist = prefs.getString("$key.artist", null)
        val cached = if (lastUrl?.startsWith("https://i.scdn.co/image/") == true &&
            lastArtist?.startsWith("https://open.spotify.com/artist/") == true
        ) SpotifyArtistPhoto(lastUrl, lastArtist) else null
        val age = System.currentTimeMillis() - prefs.getLong("$key.updated", 0L)
        if (cached != null && age in 0 until CACHE_MS) return@withContext cached
        val found = try {
            spotifyApi.searchArtistPhoto(name)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        if (found != null) {
            prefs.edit()
                .putString("$key.image", found.imageUrl)
                .putString("$key.artist", found.artistUrl)
                .putLong("$key.updated", System.currentTimeMillis()).apply()
        }
        found ?: cached
    }

    private companion object {
        const val CACHE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
