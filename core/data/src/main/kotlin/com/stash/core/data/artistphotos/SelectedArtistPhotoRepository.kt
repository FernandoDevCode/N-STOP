package com.stash.core.data.artistphotos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * One per-device, per-catalog-artist identity for the photo already chosen.
 *
 * The browse ID, NOT the artist name, is the key. Search observes [photos]
 * without any lookup or network request; only the opened profile can resolve
 * Spotify and publish a replacement. This is independent from artist_images,
 * which is a name-keyed Library backfill cache.
 *
 * Stores URLs/selection metadata, not copies of Spotify image bytes. The
 * existing user-override file store still owns imported files and cleanup.
 */
enum class ArtistPhotoOrigin { SPOTIFY, FALLBACK, USER }

data class SelectedArtistPhoto(
    val photoUrl: String,
    val origin: ArtistPhotoOrigin,
    val spotifyPhotoUrl: String? = null,
    val spotifyArtistUrl: String? = null,
)

internal object ArtistPhotoSelectionRules {
    fun spotify(
        previous: SelectedArtistPhoto?,
        sourceUrl: String,
        artistUrl: String,
    ): SelectedArtistPhoto = when {
        previous?.origin == ArtistPhotoOrigin.USER ->
            previous.copy(spotifyPhotoUrl = sourceUrl, spotifyArtistUrl = artistUrl)
        // Do not replace a previously verified higher-resolution *same* photo.
        previous?.origin == ArtistPhotoOrigin.SPOTIFY &&
            previous.spotifyPhotoUrl == sourceUrl ->
            previous.copy(spotifyArtistUrl = artistUrl)
        else -> SelectedArtistPhoto(sourceUrl, ArtistPhotoOrigin.SPOTIFY, sourceUrl, artistUrl)
    }

    fun manual(previous: SelectedArtistPhoto?, url: String): SelectedArtistPhoto =
        SelectedArtistPhoto(url, ArtistPhotoOrigin.USER, previous?.spotifyPhotoUrl, previous?.spotifyArtistUrl)

    fun restore(previous: SelectedArtistPhoto?): SelectedArtistPhoto? {
        if (previous?.origin != ArtistPhotoOrigin.USER) return previous
        return previous.spotifyPhotoUrl?.let { url ->
            SelectedArtistPhoto(url, ArtistPhotoOrigin.SPOTIFY, url, previous.spotifyArtistUrl)
        }
    }

    fun fallback(previous: SelectedArtistPhoto?, url: String): SelectedArtistPhoto =
        previous ?: SelectedArtistPhoto(url, ArtistPhotoOrigin.FALLBACK)
}

@Singleton
class SelectedArtistPhotoRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sonora_selected_artist_photo_v1", Context.MODE_PRIVATE)
    private val _photos = MutableStateFlow(load())
    val photos: StateFlow<Map<String, SelectedArtistPhoto>> = _photos.asStateFlow()

    fun get(artistBrowseId: String): SelectedArtistPhoto? = _photos.value[artistBrowseId]

    @Synchronized
    fun selectSpotify(artistBrowseId: String, spotifyPhotoUrl: String, spotifyArtistUrl: String) {
        if (!validId(artistBrowseId) || !spotifyPhotoUrl.startsWith("https://i.scdn.co/image/") ||
            !spotifyArtistUrl.startsWith("https://open.spotify.com/artist/")) return
        store(artistBrowseId, ArtistPhotoSelectionRules.spotify(get(artistBrowseId), spotifyPhotoUrl, spotifyArtistUrl))
    }

    /** Apply a user selection immediately to Search, before the profile is closed. */
    @Synchronized
    fun selectManual(artistBrowseId: String, url: String) {
        if (!validId(artistBrowseId) || url.isBlank()) return
        store(artistBrowseId, ArtistPhotoSelectionRules.manual(get(artistBrowseId), url))
    }

    @Synchronized
    fun restoreAutomatic(artistBrowseId: String) {
        if (!validId(artistBrowseId)) return
        store(artistBrowseId, ArtistPhotoSelectionRules.restore(get(artistBrowseId)))
    }

    /** Network failures must never overwrite an existing official or manual photo. */
    @Synchronized
    fun selectFallbackIfAbsent(artistBrowseId: String, url: String) {
        if (!validId(artistBrowseId) || url.isBlank()) return
        store(artistBrowseId, ArtistPhotoSelectionRules.fallback(get(artistBrowseId), url))
    }

    private fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= 160 && id.none { it.isISOControl() }

    private fun store(id: String, value: SelectedArtistPhoto?) {
        if (_photos.value[id] == value) return
        val edit = prefs.edit()
        if (value == null) edit.remove(id) else edit.putString(id, encode(value))
        edit.apply()
        _photos.value = if (value == null) _photos.value - id else _photos.value + (id to value)
    }

    private fun load(): Map<String, SelectedArtistPhoto> =
        prefs.all.mapNotNull { (id, raw) ->
            if (!validId(id) || raw !is String) return@mapNotNull null
            decode(raw)?.let { id to it }
        }.toMap()

    private fun encode(p: SelectedArtistPhoto): String =
        JSONObject()
            .put("url", p.photoUrl)
            .put("origin", p.origin.name)
            .put("spotifyPhoto", p.spotifyPhotoUrl)
            .put("spotifyArtist", p.spotifyArtistUrl)
            .toString()

    private fun decode(raw: String): SelectedArtistPhoto? = runCatching {
        val obj = JSONObject(raw)
        val url = obj.optString("url")
        if (url.isBlank()) return@runCatching null
        val origin = ArtistPhotoOrigin.valueOf(obj.getString("origin"))
        SelectedArtistPhoto(
            photoUrl = url,
            origin = origin,
            spotifyPhotoUrl = obj.optString("spotifyPhoto").takeIf { it.isNotBlank() && it != "null" },
            spotifyArtistUrl = obj.optString("spotifyArtist").takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrNull()
}
