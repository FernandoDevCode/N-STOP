package com.stash.feature.search.engine

import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.model.YouTubeVideoId
import com.stash.data.ytmusic.YTMusicCatalogItem
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult

internal fun YTMusicCatalogItem.toUniversalSearchResult(): UniversalSearchResult {
    val type = when (kind) {
        YTMusicCatalogItem.Kind.TRACK -> SearchResultType.TRACK
        YTMusicCatalogItem.Kind.VIDEO -> SearchResultType.VIDEO
        YTMusicCatalogItem.Kind.ARTIST -> SearchResultType.ARTIST
        YTMusicCatalogItem.Kind.ALBUM -> SearchResultType.ALBUM
    }
    val canNavigate = type == SearchResultType.ARTIST || type == SearchResultType.ALBUM
    val originalArtwork = artworkUrl?.takeIf { it.isNotBlank() }
    return UniversalSearchResult(
        identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, id),
        type = type,
        title = title,
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        artworkUrl = if (type == SearchResultType.VIDEO) videoArtwork(id, originalArtwork) else originalArtwork,
        durationMs = durationMs,
        isExplicit = isExplicit,
        versionLabel = if (canNavigate) null else versionFromTitle(title),
        providerRank = rank,
        rankingScore = ordinalSearchScore(rank),
        supportedActions = if (canNavigate) setOf(SearchResultAction.NAVIGATE) else emptySet(),
        release = releaseTitle?.let { SearchReleaseMetadata(it, releaseYear, releaseType, artistBrowseId) },
    )
}

internal fun videoArtwork(id: String, original: String?): String? =
    original ?: id.takeIf(YouTubeVideoId::isValid)?.let(ArtUrlUpgrader::youTubeThumbnail)

/** Provisional ordinal score shared by both catalogs. */
internal fun ordinalSearchScore(rank: Int): Double = 1.0 / (rank + 1.0)

private const val VERSION_WORDS = "live|demo|cover|remix|remaster(?:ed)?|acoustic|edit|version|ao vivo"
private val versionSegments = Regex("""\(([^()]+)\)|\[([^\[\]]+)]""")
private val versionWords = Regex("""\b($VERSION_WORDS)\b""", RegexOption.IGNORE_CASE)
private val versionSuffix = Regex(
    """\s[-–—]\s((?:\d{4}\s+)?(?:$VERSION_WORDS)\b[^()\[\]]*)$""",
    RegexOption.IGNORE_CASE,
)

/** Copy only qualifiers already present in the source title, leaving the title intact. */
internal fun versionFromTitle(title: String): String? =
    versionSegments.findAll(title)
        .map { it.groupValues.drop(1).first(String::isNotEmpty).trim() }
        .filter(versionWords::containsMatchIn)
        .plus(listOfNotNull(versionSuffix.find(title)?.groupValues?.get(1)?.trim()))
        .distinct()
        .joinToString(" · ")
        .ifEmpty { null }
