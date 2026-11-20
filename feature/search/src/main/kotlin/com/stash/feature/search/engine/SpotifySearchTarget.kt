package com.stash.feature.search.engine

import com.stash.data.ytmusic.YTMusicCatalogItem
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult
import kotlin.math.abs

/**
 * The existing detail routes and player require YouTube Music identities. Resolve only on
 * selection and require a unique match with recording or edition metadata where applicable.
 */
internal fun spotifySearchTarget(
    result: UniversalSearchResult,
    candidates: List<YTMusicCatalogItem>,
): YTMusicCatalogItem? {
    if (result.identity.source != CatalogSource.SPOTIFY) return null
    val title = searchTextKey(result.title)
    val credits = result.subtitle?.let(::searchTextKey)
    val release = result.release
    return candidates.asSequence().filter { candidate ->
        if (searchTextKey(candidate.title) != title) return@filter false
        val candidateDuration = candidate.durationMs
        when (result.type) {
            SearchResultType.ARTIST -> candidate.kind == YTMusicCatalogItem.Kind.ARTIST
            SearchResultType.ALBUM -> candidate.kind == YTMusicCatalogItem.Kind.ALBUM &&
                credits != null && candidate.subtitle?.let(::searchTextKey) == credits &&
                release?.year != null && release.year == candidate.releaseYear &&
                compatibleReleaseType(release.type, candidate.releaseType)
            SearchResultType.TRACK -> candidate.kind == YTMusicCatalogItem.Kind.TRACK &&
                credits != null && candidate.subtitle?.let(::searchTextKey) == credits &&
                result.durationMs != null && candidateDuration != null &&
                abs(result.durationMs - candidateDuration) <= 2_000 &&
                result.isExplicit == candidate.isExplicit && release != null &&
                candidate.releaseTitle?.let(::searchTextKey) == searchTextKey(release.title)
            SearchResultType.VIDEO -> false
        }
    }.distinctBy { it.id }.toList().singleOrNull()
}

private fun compatibleReleaseType(spotify: String?, music: String?): Boolean {
    val spotifyType = spotify?.let(::searchTextKey) ?: return false
    val musicType = music?.let(::searchTextKey) ?: return false
    return spotifyType == musicType || (spotifyType == "single" && musicType == "ep")
}
