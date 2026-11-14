package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyCatalogKind
import com.stash.data.ytmusic.YouTubeWebSearchClient
import com.stash.feature.search.contract.AudioIdentity
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import javax.inject.Inject

data class LiveSearchPage(
    val items: List<UniversalSearchResult>,
    val continuation: String?,
    val nextRank: Int,
)

/**
 * Catalog-only source adapters. IDs are NEVER promoted across providers.
 * Only the YouTube WEB adapter is used for the conventional YouTube tab.
 */
class LiveSearchSources @Inject constructor(
    private val spotify: SpotifyApiClient,
    private val youtube: YouTubeWebSearchClient,
) {
    suspend fun search(
        query: String,
        source: CatalogSource,
        continuation: String? = null,
        rankOffset: Int = 0,
    ): LiveSearchPage = when (source) {
        CatalogSource.SPOTIFY -> {
            val offset = continuation?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val page = spotify.searchCatalogPage(query, offset = offset)
            val results = page.items.mapIndexed { index, item ->
                val type = when (item.kind) {
                    SpotifyCatalogKind.TRACK -> SearchResultType.TRACK
                    SpotifyCatalogKind.ALBUM -> SearchResultType.ALBUM
                    SpotifyCatalogKind.ARTIST -> SearchResultType.ARTIST
                }
                val rank = rankOffset + index
                UniversalSearchResult(
                    identity = CatalogIdentity(
                        CatalogSource.SPOTIFY, item.id,
                        "spotify:${item.kind.name.lowercase()}:${item.id}",
                    ),
                    type = type,
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.artworkUrl,
                    durationMs = item.durationMs,
                    isExplicit = item.explicit,
                    providerRank = rank,
                    rankingScore = ordinalScore(rank),
                    // Spotify catalog has no stream URL. PLAY resolves strictly on tap.
                    supportedActions = when (type) {
                        SearchResultType.TRACK -> setOf(SearchResultAction.PLAY)
                        SearchResultType.ARTIST, SearchResultType.ALBUM ->
                            setOf(SearchResultAction.NAVIGATE)
                        else -> emptySet()
                    },
                )
            }
            LiveSearchPage(
                items = results,
                continuation = if (page.hasMore && results.isNotEmpty()) (offset + 10).toString() else null,
                nextRank = rankOffset + results.size,
            )
        }
        CatalogSource.YOUTUBE -> {
            val page = youtube.search(query, continuation, rankOffset)
            LiveSearchPage(
                items = page.videos.map { video ->
                    video.toUniversalSearchResult().copy(
                        audioIdentity = AudioIdentity("YOUTUBE", video.videoId),
                        supportedActions = setOf(
                            SearchResultAction.PLAY,
                            SearchResultAction.PLAY_NEXT,
                            SearchResultAction.ADD_TO_QUEUE,
                            SearchResultAction.DOWNLOAD,
                        ),
                    )
                },
                continuation = page.continuation,
                nextRank = page.nextRank,
            )
        }
        // Keep the shared contract enum for legacy/internal callers, but no
        // YouTube Music catalog request is allowed from the Sonora Search.
        CatalogSource.YOUTUBE_MUSIC ->
            throw IllegalArgumentException("YouTube Music is not a Search tab")

    }

    /** In-app Spotify details use Spotify IDs, not remapped YouTube browse IDs. */
    suspend fun browseSpotify(
        entry: UniversalSearchResult,
        continuation: String? = null,
    ): LiveSearchPage {
        require(entry.identity.source == CatalogSource.SPOTIFY)
        val offset = continuation?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val page = when (entry.type) {
            SearchResultType.ARTIST ->
                spotify.searchArtistReleasesPage(entry.identity.catalogId, offset)
            SearchResultType.ALBUM ->
                spotify.searchAlbumTracksPage(entry.identity.catalogId, offset)
            else -> return LiveSearchPage(emptyList(), null, offset)
        }
        val items = page.items.mapIndexed { index, item ->
            val kind = when (item.kind) {
                SpotifyCatalogKind.TRACK -> SearchResultType.TRACK
                SpotifyCatalogKind.ALBUM -> SearchResultType.ALBUM
                SpotifyCatalogKind.ARTIST -> SearchResultType.ARTIST
            }
            val rank = offset + index
            UniversalSearchResult(
                identity = CatalogIdentity(
                    CatalogSource.SPOTIFY, item.id,
                    "spotify:${item.kind.name.lowercase()}:${item.id}",
                ),
                type = kind,
                title = item.title,
                subtitle = if (kind == SearchResultType.ALBUM)
                    listOfNotNull(item.subtitle, item.releaseDate?.take(4), item.releaseGroup)
                        .filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { null }
                    else item.subtitle,
                artworkUrl = item.artworkUrl ?: if (kind == SearchResultType.TRACK)
                    entry.artworkUrl else null,
                durationMs = item.durationMs,
                isExplicit = item.explicit,
                providerRank = rank,
                rankingScore = ordinalScore(rank),
                supportedActions = if (kind == SearchResultType.TRACK)
                    setOf(SearchResultAction.PLAY)
                    else setOf(SearchResultAction.NAVIGATE),
            )
        }
        return LiveSearchPage(
            items = items,
            continuation = if (page.hasMore && items.isNotEmpty()) (offset + 10).toString() else null,
            nextRank = offset + items.size,
        )
    }

    private fun ordinalScore(rank: Int): Double = 1.0 / (rank + 1.0)
}
