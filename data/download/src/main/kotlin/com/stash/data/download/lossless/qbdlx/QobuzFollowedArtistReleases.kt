package com.stash.data.download.lossless.qbdlx

import com.stash.core.data.artists.FollowedArtist
import com.stash.core.data.artists.FollowedArtistReleasesRepository
import com.stash.data.download.lossless.qobuz.QobuzCandidateMatcher
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.data.ytmusic.model.AlbumSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Conservative Qobuz-backed recent-release feed. YT IDs are not Qobuz IDs:
 * only a UNIQUE exact artist-name match is accepted; ambiguous homonyms are
 * intentionally hidden instead of crediting a stranger's releases. */
@Singleton
class QobuzFollowedArtistReleases @Inject constructor(
    private val client: QbdlxApiClient,
) : FollowedArtistReleasesRepository {
    private data class Entry(val at: Long, val albums: List<AlbumSummary>)
    private val cache = ConcurrentHashMap<String, Entry>()

    override suspend fun recentReleases(artists: List<FollowedArtist>): List<AlbumSummary> {
        val candidates = artists.filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }.take(MAX_ARTISTS)
        if (candidates.isEmpty()) return emptyList()
        val key = candidates.map { "${it.id}:${it.name}" }.sorted().joinToString("|")
        val now = System.currentTimeMillis()
        cache[key]?.takeIf {
            now - it.at < if (it.albums.isEmpty()) EMPTY_CACHE_MS else CACHE_MS
        }?.let { return it.albums }

        val today = LocalDate.now(ZoneOffset.UTC)
        val gate = Semaphore(3)
        val releases = coroutineScope {
            candidates.map { artist ->
                async {
                    gate.withPermit {
                        try {
                            withTimeout(9_000L) { fetchArtist(artist, today) }
                        } catch (e: TimeoutCancellationException) {
                            emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        }.distinctBy { it.first.id }
            .sortedByDescending { it.second }
            .take(MAX_RELEASES)
            .map { it.first }
        cache[key] = Entry(System.currentTimeMillis(), releases)
        return releases
    }

    private suspend fun fetchArtist(
        followed: FollowedArtist, today: LocalDate,
    ): List<Pair<AlbumSummary, LocalDate>> {
        val expected = QobuzCandidateMatcher.normalize(followed.name)
        if (expected.isBlank() || expected in setOf("various artists", "various", "va")) return emptyList()
        val matches = client.searchArtists(followed.name)
            .filter { it.id > 0 && QobuzCandidateMatcher.normalize(it.name) == expected }
            .distinctBy { it.id }
        // No cross-service mapping exists yet. A duplicate exact-name artist
        // cannot be identified safely; fail closed.
        val id = matches.singleOrNull()?.id ?: return emptyList()
        return client.getArtistAlbums(id).mapNotNull { release ->
            if (release.id.isBlank() ||
                QobuzCandidateMatcher.normalize(release.artist?.name.orEmpty()) != expected) {
                return@mapNotNull null
            }
            val date = recentDate(release.release_date_original, today) ?: return@mapNotNull null
            AlbumSummary(
                id = release.id, title = release.title, artist = followed.name,
                thumbnailUrl = release.image?.large ?: release.image?.small,
                year = date.year.toString(), source = AlbumSource.QOBUZ,
                trackCount = release.tracks_count.takeIf { it > 0 },
            ) to date
        }
    }

    internal companion object {
        const val MAX_ARTISTS = 12
        const val MAX_RELEASES = 16
        const val CACHE_MS = 3 * 60 * 60 * 1000L
        const val EMPTY_CACHE_MS = 20 * 60 * 1000L

        fun recentDate(raw: String?, today: LocalDate): LocalDate? {
            if (raw == null || !Regex("""\d{4}-\d{2}-\d{2}""").matches(raw)) return null
            val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return null
            return date.takeIf { !it.isBefore(today.minusDays(90)) && !it.isAfter(today) }
        }
    }
}
