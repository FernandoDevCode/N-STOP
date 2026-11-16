package com.stash.feature.search.engine

import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.*

/** Provisional ordinal ordering convention, not measured relevance. */
fun YouTubeWebVideo.toUniversalSearchResult(): UniversalSearchResult = UniversalSearchResult(
    identity = CatalogIdentity(CatalogSource.YOUTUBE, videoId),
    type = SearchResultType.VIDEO,
    title = title,
    subtitle = channel,
    artworkUrl = videoArtwork(videoId, thumbnailUrl),
    durationMs = durationMs,
    versionLabel = versionFromTitle(title),
    providerRank = providerRank,
    rankingScore = ordinalSearchScore(providerRank),
    supportedActions = emptySet(),
)
