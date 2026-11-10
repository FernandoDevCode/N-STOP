package com.stash.feature.search.engine

import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.*

/** Provisional ordinal ordering convention, not measured relevance. */
fun YouTubeWebVideo.toUniversalSearchResult(): UniversalSearchResult = UniversalSearchResult(
    identity = CatalogIdentity(CatalogSource.YOUTUBE, videoId),
    type = SearchResultType.VIDEO,
    title = title,
    subtitle = channel,
    artworkUrl = thumbnailUrl,
    durationMs = durationMs,
    providerRank = providerRank,
    rankingScore = 1.0 / (providerRank + 1.0),
    supportedActions = emptySet(),
)
