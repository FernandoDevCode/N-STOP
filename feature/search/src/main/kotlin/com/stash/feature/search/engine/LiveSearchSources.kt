package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiClient
import com.stash.data.spotify.SpotifyCatalogKind
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.YouTubeWebSearchClient
import com.stash.data.ytmusic.model.SearchResultSection
import com.stash.data.ytmusic.model.TopResultItem
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
    private val ytm: YTMusicApiClient,
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
                    supportedActions = if (type == SearchResultType.TRACK)
                        setOf(SearchResultAction.PLAY) else emptySet(),
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
        CatalogSource.YOUTUBE_MUSIC -> {
            // YTM is a separate, user-selected music catalog; never WEB YouTube.
            val sections = ytm.searchAll(query).sections
            val results = buildList {
                fun addSong(id: String, title: String, artist: String, cover: String?, ms: Long?) {
                    if (id.isBlank() || title.isBlank()) return
                    val rank = rankOffset + size
                    add(UniversalSearchResult(
                        identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, id),
                        audioIdentity = AudioIdentity("YOUTUBE_MUSIC", id),
                        type = SearchResultType.TRACK,
                        title = title,
                        subtitle = artist.ifBlank { null },
                        artworkUrl = cover,
                        durationMs = ms,
                        providerRank = rank,
                        rankingScore = ordinalScore(rank),
                        supportedActions = setOf(
                            SearchResultAction.PLAY,
                            SearchResultAction.PLAY_NEXT,
                            SearchResultAction.ADD_TO_QUEUE,
                            SearchResultAction.DOWNLOAD,
                        ),
                    ))
                }
                sections.forEach { section ->
                    when (section) {
                        is SearchResultSection.Top -> when (val top = section.item) {
                            is TopResultItem.TrackTop -> addSong(
                                top.track.videoId, top.track.title, top.track.artist,
                                top.track.thumbnailUrl, (top.track.durationSeconds * 1000).toLong().coerceAtLeast(0),
                            )
                            is TopResultItem.ArtistTop -> {
                                val rank = rankOffset + size
                                add(UniversalSearchResult(
                                    identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, top.artist.id),
                                    type = SearchResultType.ARTIST, title = top.artist.name,
                                    artworkUrl = top.artist.avatarUrl,
                                    providerRank = rank, rankingScore = ordinalScore(rank),
                                    supportedActions = setOf(SearchResultAction.NAVIGATE),
                                ))
                            }
                        }
                        is SearchResultSection.Songs -> section.tracks.forEach { t ->
                            addSong(t.videoId, t.title, t.artist, t.thumbnailUrl,
                                (t.durationSeconds * 1000).toLong().coerceAtLeast(0))
                        }
                        is SearchResultSection.Artists -> section.artists.forEach { a ->
                            if (a.id.isNotBlank() && a.name.isNotBlank()) {
                                val rank = rankOffset + size
                                add(UniversalSearchResult(
                                    identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, a.id),
                                    type = SearchResultType.ARTIST, title = a.name,
                                    artworkUrl = a.avatarUrl,
                                    providerRank = rank, rankingScore = ordinalScore(rank),
                                    supportedActions = setOf(SearchResultAction.NAVIGATE),
                                ))
                            }
                        }
                        is SearchResultSection.Albums -> section.albums.forEach { a ->
                            if (a.id.isNotBlank() && a.title.isNotBlank()) {
                                val rank = rankOffset + size
                                add(UniversalSearchResult(
                                    identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, a.id),
                                    type = SearchResultType.ALBUM, title = a.title,
                                    subtitle = a.artist.ifBlank { null },
                                    artworkUrl = a.thumbnailUrl,
                                    providerRank = rank, rankingScore = ordinalScore(rank),
                                    supportedActions = setOf(SearchResultAction.NAVIGATE),
                                ))
                            }
                        }
                    }
                }
            }.distinctBy { it.stableKey }
            // The existing YTM searchAll API has no surfaced continuation contract.
            LiveSearchPage(results, continuation = null, nextRank = rankOffset + results.size)
        }
    }

    private fun ordinalScore(rank: Int): Double = 1.0 / (rank + 1.0)
}
