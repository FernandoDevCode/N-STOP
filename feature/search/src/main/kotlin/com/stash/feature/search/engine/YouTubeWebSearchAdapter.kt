package com.stash.feature.search.engine

import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult

/** Provisional ordinal ordering convention, not measured relevance. */
fun YouTubeWebVideo.toUniversalSearchResult(): UniversalSearchResult {
    val identity = CatalogIdentity(CatalogSource.YOUTUBE, videoId)
    val audio = identity.exactYouTubeAudio(SearchResultType.VIDEO)
    return UniversalSearchResult(
        identity = identity,
        audioIdentity = audio,
        type = SearchResultType.VIDEO,
        title = title,
        subtitle = channel,
        artworkUrl = videoArtwork(videoId, thumbnailUrl),
        durationMs = durationMs,
        versionLabel = versionFromTitle(title),
        providerRank = providerRank,
        rankingScore = ordinalSearchScore(providerRank),
        supportedActions = if (audio != null) setOf(SearchResultAction.PLAY) else emptySet(),
    )
}
