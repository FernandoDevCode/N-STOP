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
        // Revalidate only on profile open (the only caller). Search never calls
        // Spotify. A failure retains the last valid reference without clearing it.
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

}
