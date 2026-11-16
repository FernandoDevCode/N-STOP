package com.stash.feature.search.engine

import android.os.SystemClock
import com.stash.core.data.artistphotos.SelectedArtistPhotoRepository
import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.spotify.SpotifyCatalogItem.Kind
import com.stash.data.spotify.SpotifyRateLimitException
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
internal class SearchArtworkLookup(
    private val search: suspend (String, Kind) -> List<SpotifyCatalogItem>,
    private val spotifyArtistId: (String) -> String? = { null },
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    @Inject
    constructor(spotify: SpotifyApiClient, artistPhotos: SelectedArtistPhotoRepository) : this(
        search = { query, kind -> spotify.searchCatalog(query, limit = 8, kind = kind) },
        spotifyArtistId = { browseId ->
            val photo = artistPhotos.get(browseId) ?: artistPhotos.get(browseId.removePrefix("MPLA"))
            photo?.spotifyArtistUrl?.removePrefix("https://open.spotify.com/artist/")
                ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{22}")) }
        },
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<CacheKey, CachedArtwork>(32, .75f, true)
    private var blockedUntilMs = 0L

    suspend fun find(result: UniversalSearchResult): String? {
        val kind = result.artworkKind() ?: return null
        val release = checkNotNull(result.release)
        val pinned = release.artistBrowseId?.let(spotifyArtistId)
        val key = CacheKey(result.stableKey, result.title, result.subtitle, result.durationMs, result.isExplicit, release, pinned)
        return mutex.withLock {
            cache[key]?.takeIf { now() < it.expiresAt }?.let { return@withLock it.url }
            if (now() < blockedUntilMs) return@withLock null
            val query = listOfNotNull(result.title, result.subtitle, release.title.takeIf { kind == Kind.TRACK })
                .joinToString(" ")
            val candidates = try {
                withTimeoutOrNull(4_000) { search(query, kind) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (rateLimit: SpotifyRateLimitException) {
                blockedUntilMs = now() + (rateLimit.retryAfterSeconds ?: 60).coerceIn(1, Long.MAX_VALUE / 2_000) * 1_000
                return@withLock null
            } catch (error: SpotifyApiException) {
                if (error.httpCode == 0 || error.httpCode == 401 || error.httpCode == 403) {
                    blockedUntilMs = now() + TRANSIENT_TTL_MS
                    return@withLock null
                }
                null
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            val url = candidates?.let { matchingSpotifyArtwork(result, kind, it, pinned) }
            val ttl = when {
                url != null -> 24 * 60 * 60_000L
                candidates != null -> 30 * 60_000L
                else -> TRANSIENT_TTL_MS
            }
            cache[key] = CachedArtwork(url, now() + ttl)
            if (cache.size > 256) cache.remove(cache.keys.first())
            url
        }
    }

    private data class CacheKey(
        val stableKey: String,
        val title: String,
        val artist: String?,
        val durationMs: Long?,
        val explicit: Boolean,
        val release: SearchReleaseMetadata,
        val spotifyArtistId: String?,
    )

    private data class CachedArtwork(val url: String?, val expiresAt: Long)
}

private const val TRANSIENT_TTL_MS = 60_000L

internal fun UniversalSearchResult.artworkKind(): Kind? {
    if (identity.source != CatalogSource.YOUTUBE_MUSIC || subtitle.isNullOrBlank() || release == null) return null
    return when (type) {
        SearchResultType.TRACK -> Kind.TRACK.takeIf { (durationMs ?: 0) > 0 }
        SearchResultType.ALBUM -> Kind.ALBUM.takeIf {
            release.year?.toIntOrNull() != null && release.type?.artworkKey() in setOf("album", "single", "compilation")
        }
        else -> null
    }
}

internal fun matchingSpotifyArtwork(
    result: UniversalSearchResult,
    kind: Kind,
    candidates: List<SpotifyCatalogItem>,
    pinnedArtistId: String?,
): String? {
    val release = result.release ?: return null
    val sameCredit = candidates.filter { it.kind == kind && it.artists?.artworkKey() == result.subtitle?.artworkKey() }
    val artistGroups = sameCredit.map { it.artistIds.toSet() }
    if (pinnedArtistId == null && (artistGroups.any { it.isEmpty() } || artistGroups.distinct().size != 1)) return null

    val candidate = sameCredit.filter {
        (pinnedArtistId == null || pinnedArtistId in it.artistIds) && it.title.artworkKey() == result.title.artworkKey() &&
            when (kind) {
                Kind.TRACK -> {
                    val duration = it.durationMs
                    val original = result.durationMs
                    it.albumTitle?.artworkKey() == release.title.artworkKey() &&
                        it.isExplicit == result.isExplicit && duration != null && original != null &&
                        abs(duration - original) <= 2_000
                }
                Kind.ALBUM -> it.releaseYear == release.year && it.releaseType?.artworkKey() == release.type?.artworkKey()
                Kind.ARTIST -> false
            }
    }.distinctBy { it.id }.singleOrNull() ?: return null
    return candidate.artworkUrl?.takeIf {
        val url = it.toHttpUrlOrNull()
        url?.isHttps == true && url.host == "i.scdn.co" && url.username.isEmpty() && url.password.isEmpty()
    }
}

private val coverWhitespace = Regex("""\s+""")
private fun String.artworkKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFKC).trim().replace(coverWhitespace, " ").lowercase(Locale.ROOT)
