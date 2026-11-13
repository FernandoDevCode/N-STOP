package com.stash.feature.home

import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.entity.TrackTagEntity
import com.stash.data.spotify.SpotifyTrackCandidate
import com.stash.core.data.repository.MusicRepository
import com.stash.core.model.Track
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.SearchResultSection
import com.stash.data.ytmusic.model.TopResultItem
import com.stash.data.ytmusic.model.TrackSummary
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Only real catalog identities appear here, never invented songs or artists. */
data class SonoraWeeklyDiscovery(
    val releases: List<AlbumSummary> = emptyList(),
    val current: List<TrackSummary> = emptyList(),
    val forgotten: List<TrackSummary> = emptyList(),
    val currentTag: String? = null,
    val forgottenTag: String? = null,
)

/**
 * Taste snapshots from the whole synchronized library (all sources) and actual
 * local listening_events in the last 30 days. A saved track is one positive
 * signal, NOT a play; saved and recent listening are never conflated.
 */
internal object SonoraTasteScorer {
    private val nonGenreTags = setOf(
        "__untaggable__", "seen live", "favorites", "favourite", "favorite",
        "music", "good", "awesome", "male vocalists", "female vocalists",
        "albums i own", "under 2000 listeners", "american", "british",
    )

    data class Profile(val currentTags: List<String>, val forgottenTags: List<String>)

    fun rank(
        tracks: List<Track>,
        tags: List<TrackTagEntity>,
        plays30d: Map<Long, Int>,
    ): Profile {
        val byTrack = tracks.associateBy { it.id }
        val historical = mutableMapOf<String, Double>()
        val recent = mutableMapOf<String, Double>()
        val support = mutableMapOf<String, Int>()
        for (tag in tags) {
            val track = byTrack[tag.trackId] ?: continue
            val name = tag.tag.trim().lowercase()
            if (name.length < 3 || name in nonGenreTags ||
                name.startsWith("http") || tag.weight <= 0f) continue
            val strength = tag.weight.toDouble().coerceIn(0.0, 1.0)
            val saved = track.spotifySavedAt != null || track.ytMusicSavedAt != null ||
                track.stashLikedAt != null || track.lastFmLovedAt != null
            val historicScore = strength * (if (saved) 1.30 else 1.0)
            historical[name] = (historical[name] ?: 0.0) + historicScore
            support[name] = (support[name] ?: 0) + 1
            val plays = (plays30d[track.id] ?: 0).coerceIn(0, 12)
            if (plays > 0) recent[name] = (recent[name] ?: 0.0) + strength * plays
        }
        val totalHistorical = historical.values.sum().coerceAtLeast(1.0)
        val totalRecent = recent.values.sum().coerceAtLeast(1.0)
        val current = recent.entries.sortedByDescending { it.value }
            .map { it.key }.filter { (support[it] ?: 0) >= 2 }.take(3)
        // Distinguish genuinely under-heard genres from tiny one-off tags.
        // Relative exposure matters: rock should not dominate simply because
        // it occupies a big share of both the whole library and last month.
        val forgotten = historical.entries.asSequence()
            .filter { (support[it.key] ?: 0) >= 3 }
            .map { (tag, mass) ->
                val historicalShare = mass / totalHistorical
                val recentShare = (recent[tag] ?: 0.0) / totalRecent
                tag to (historicalShare - recentShare)
            }
            .filter { it.second > 0.008 }
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { it !in current }
            .take(3)
            .toList()
        return Profile(current, forgotten)
    }
}

/**
 * Sonora candidate engine: Spotify for release dates and authenticated
 * music candidates, YouTube Music for verification and playback identities.
 * Local listening tags rank user interests. Search relevance is a proxy,
 * not an objective measure of artistic quality.
 *
 * Bounded requests: 6 artist release searches, at most 3 tags per mode,
 * 12 catalog resolutions per mode. The VM loads it once per screen lifetime.
 */
class SonoraWeeklyDiscoveryEngine @Inject constructor(
    private val music: MusicRepository,
    private val trackTagDao: TrackTagDao,
    private val events: ListeningEventDao,
    private val spotify: SpotifyApiClient,
    private val youtube: YTMusicApiClient,
    private val cache: SonoraWeeklyDiscoveryCache,
    private val feedback: SonoraDiscoveryFeedbackStore,
) {
    fun reject(videoId: String) = feedback.reject(videoId)

    suspend fun generate(onPartial: suspend (SonoraWeeklyDiscovery) -> Unit = {}): SonoraWeeklyDiscovery {
        val tracks = attempt { music.getAllTracks().first() }.orEmpty()
        if (tracks.isEmpty()) return SonoraWeeklyDiscovery()
        val libraryFingerprint = fingerprint(tracks)
        cache.read(libraryFingerprint)?.let { snapshot ->
            val rejected = feedback.rejectedVideoIds()
            return snapshot.copy(
                current = snapshot.current.filterNot { it.videoId in rejected },
                forgotten = snapshot.forgotten.filterNot { it.videoId in rejected },
            )
        }
        val last30 = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recentPlays = attempt { events.getPlayCountsSince(last30) }.orEmpty()
            .associate { it.trackId to it.plays }
        val storedTags = attempt { trackTagDao.getByTracks(tracks.map { it.id }) }.orEmpty()
        // Never request Last.fm to infer artist tags. Previously enriched
        // library tags remain usable, but they trigger no new API requests.
        val tags = storedTags
        val taste = SonoraTasteScorer.rank(tracks, tags, recentPlays)
        val owned = tracks.map { identity(it.artist, it.title) }.toSet()
        val recentArtistPlays = tracks.asSequence()
            .map { it.artist.trim() to (recentPlays[it.id] ?: 0) }
            .filter { it.first.isNotBlank() && it.second > 0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.map { it.key }
        val libraryArtists = tracks.asSequence()
            .map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().take(35).toList()
        val followed = attempt { spotify.getFollowedArtistNames(12) }.orEmpty()
        val seeds = (followed + recentArtistPlays + libraryArtists)
            .distinctBy { it.lowercase() }.take(6)

        val releases = mutableListOf<AlbumSummary>()
        val today = LocalDate.now()
        for (artist in seeds) {
            val found = attempt { spotify.searchRecentArtistAlbums(artist, today.minusDays(120).toString()) }
                .orEmpty().take(3)
            for (item in found) {
                if (item.releaseDate > today.toString()) continue
                val resolved = attempt { youtube.resolveAlbum(item.title, item.artist) } ?: continue
                if (!same(resolved.artist, artist) || !same(resolved.title, item.title)) continue
                if (releases.none { same(it.artist, resolved.artist) && same(it.title, resolved.title) }) {
                    releases += resolved
                }
                if (releases.size == 10) break
            }
            if (releases.size == 10) break
        }

        if (releases.isNotEmpty()) onPartial(SonoraWeeklyDiscovery(releases = releases.toList()))
        val used = owned.toMutableSet()
        // When a newly synchronized library has no saved tags, use real
        // recent/listened artists instead of secretly fetching Last.fm data.
        val currentQueries = taste.currentTags.ifEmpty { recentArtistPlays.take(3) }
        val recentLower = recentArtistPlays.take(3).map { it.lowercase() }.toSet()
        val forgottenQueries = taste.forgottenTags.ifEmpty {
            libraryArtists.filterNot { it.lowercase() in recentLower }.take(3)
        }
        val current = discover(currentQueries, used)
        if (current.isNotEmpty()) onPartial(SonoraWeeklyDiscovery(
            releases = releases.toList(), current = current, currentTag = taste.currentTags.firstOrNull(),
        ))
        val forgotten = discover(forgottenQueries, used)
        return SonoraWeeklyDiscovery(
            releases = releases,
            current = current,
            forgotten = forgotten,
            currentTag = taste.currentTags.firstOrNull(),
            forgottenTag = taste.forgottenTags.firstOrNull(),
        ).also { cache.write(it, libraryFingerprint) }
    }

    /**
     * Spotify catalog candidates, verified conservatively against YouTube
     * Music. Do not fabricate a version when the artist/title does not match.
     * Queries are derived from real saved tags or recent/local artist names.
     */
    private suspend fun discover(queries: List<String>, used: MutableSet<String>): List<TrackSummary> {
        if (queries.isEmpty()) return emptyList()
        val candidates = linkedMapOf<String, Pair<SpotifyTrackCandidate, Int>>()
        val perQuery = queries.take(3).map { query ->
            attempt { spotify.searchTracksGraphQL(query, limit = 20) }.orEmpty()
                .filter { it.artists.firstOrNull()?.isNotBlank() == true && it.name.isNotBlank() }
                .take(20)
        }
        val depth = perQuery.maxOfOrNull { it.size } ?: 0
        for (index in 0 until depth) for (list in perQuery) {
            val candidate = list.getOrNull(index) ?: continue
            val artist = candidate.artists.firstOrNull() ?: continue
            val key = identity(artist, candidate.name)
            if (key !in used) {
                val previous = candidates[key]
                candidates[key] = candidate to ((previous?.second ?: 0) + 1)
            }
        }

        val chosen = mutableListOf<TrackSummary>()
        val artistCounts = mutableMapOf<String, Int>()
        var attempts = 0
        for ((key, scored) in candidates.entries.sortedWith(
            compareByDescending<Map.Entry<String, Pair<SpotifyTrackCandidate, Int>>> { it.value.second },
        )) {
            if (attempts >= 16 || chosen.size >= 10) break
            val candidate = scored.first
            val artist = candidate.artists.firstOrNull() ?: continue
            val artistKey = artist.trim().lowercase()
            if ((artistCounts[artistKey] ?: 0) >= 2 || key in used) continue
            attempts++
            val songs = attempt { youtube.searchAll("$"+"{artist} $"+"{candidate.name}") }
                ?.sections.orEmpty().flatMap { section ->
                    when (section) {
                        is SearchResultSection.Songs -> section.tracks
                        is SearchResultSection.Top ->
                            listOfNotNull((section.item as? TopResultItem.TrackTop)?.track)
                        else -> emptyList()
                    }
                }
            val rejected = feedback.rejectedVideoIds()
            val verified = songs.firstOrNull {
                it.videoId.isNotBlank() && it.videoId !in rejected &&
                    same(it.artist, artist) &&
                    same(it.title, candidate.name) && !it.thumbnailUrl.isNullOrBlank()
            } ?: continue
            chosen += verified
            used += key
            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
        }
        return chosen
    }

    /** Stable across app restarts; changes with the synchronized library identity. */
    private fun fingerprint(tracks: List<Track>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        tracks.sortedBy { it.id }.forEach {
            digest.update(("${it.id}|${it.artist}|${it.title}|${it.source}|" +
                "${it.spotifyUri.orEmpty()}|${it.youtubeId.orEmpty()}\\n")
                .toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)
    private fun identity(artist: String, title: String) =
        "${artist.trim().lowercase()}|${title.trim().lowercase()}"

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try { block() } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
}
