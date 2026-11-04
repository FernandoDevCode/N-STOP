package com.stash.feature.home

import com.stash.core.data.repository.MusicRepository
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** A catalog album resolved to the existing playable album route. */
data class HomeFeaturedRelease(
    val album: AlbumSummary,
    val source: Source,
    val isFollowedArtist: Boolean,
) {
    enum class Source { SPOTIFY, YOUTUBE_MUSIC }
}

/**
 * Home V3 source policy:
 * 1. Spotify followed artists, supplemented by local listening (never mislabeled as follows).
 * 2. Recent Spotify album metadata (exact artist, real release day).
 * 3. YouTube Music resolves the album into a playable app route.
 * 4. If Spotify has no available result, recent-year YouTube albums are a secondary source.
 *
 * Fail closed: no fabricated album/date or unrelated Qobuz catalog banner.
 * Kept to four artist seeds and one fetch per HomeViewModel lifetime.
 */
class HomeFeaturedReleaseLookup @Inject constructor(
    private val spotify: SpotifyApiClient,
    private val youtube: YTMusicApiClient,
    private val music: MusicRepository,
) {
    suspend fun find(): HomeFeaturedRelease? {
        val followed = safeCall { spotify.getFollowedArtistNames(12) }.orEmpty()
            .filter { it.isNotBlank() }.distinctBy { it.trim().lowercase() }
        val listening = safeCall {
            music.getMostPlayed(limit = 40).first()
                .filter { it.artist.isNotBlank() && it.playCount > 0 }
                .map { it.artist.trim() }
        }.orEmpty()
        val libraryArtists = if (followed.isEmpty() && listening.isEmpty()) {
            safeCall {
                music.getAllArtists().first().sortedByDescending { it.trackCount }
                    .map { it.artist.trim() }.filter { it.isNotBlank() }
            }.orEmpty()
        } else emptyList()
        val seeds = (followed + listening + libraryArtists)
            .distinctBy { it.trim().lowercase() }.take(4)
        if (seeds.isEmpty()) return null

        val currentYear = LocalDate.now().year.toString()
        val startDate = LocalDate.now().minusDays(120).toString()
        // Spotify determines whether a release is recent. YouTube only supplies
        // the app's existing playable album identity, not the choice.
        for (artist in seeds) {
            val releases = safeCall { spotify.searchRecentArtistAlbums(artist, startDate) }.orEmpty()
            for (release in releases.take(3)) {
                val resolved = safeCall { youtube.resolveAlbum(release.title, release.artist) }
                    ?: continue
                if (!sameArtist(resolved.artist, artist) ||
                    !resolved.title.equals(release.title, ignoreCase = true)) continue
                return HomeFeaturedRelease(
                    resolved, HomeFeaturedRelease.Source.SPOTIFY,
                    followed.any { sameArtist(it, artist) },
                )
            }
        }

        // YouTube Music secondary. It exposes a year but not a verified day:
        // the UI must describe these as an artist album, not a fresh release.
        for (artist in seeds) {
            val identity = safeCall { youtube.resolveArtist(artist) } ?: continue
            if (!sameArtist(identity.name, artist)) continue
            val profile = safeCall { youtube.getArtist(identity.id) } ?: continue
            val album = (profile.albums + profile.singles)
                .firstOrNull { it.year == currentYear && sameArtist(it.artist, artist) }
                ?: continue
            return HomeFeaturedRelease(
                album, HomeFeaturedRelease.Source.YOUTUBE_MUSIC,
                followed.any { sameArtist(it, artist) },
            )
        }
        return null
    }

    private fun sameArtist(first: String, second: String): Boolean =
        first.trim().equals(second.trim(), ignoreCase = true)

    private suspend fun <T> safeCall(block: suspend () -> T): T? =
        try { block() } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
}
