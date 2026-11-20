package com.stash.feature.search.engine

import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.model.YouTubeVideoId
import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.ytmusic.YTMusicCatalogItem
import com.stash.feature.search.contract.AudioIdentity
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.UniversalSearchResult

internal val SpotifyCatalogItem.Kind.resultType: SearchResultType
    get() = when (this) {
        SpotifyCatalogItem.Kind.TRACK -> SearchResultType.TRACK
        SpotifyCatalogItem.Kind.ARTIST -> SearchResultType.ARTIST
        SpotifyCatalogItem.Kind.ALBUM -> SearchResultType.ALBUM
    }

internal fun SpotifyCatalogItem.toUniversalSearchResult() = UniversalSearchResult(
    identity = CatalogIdentity(CatalogSource.SPOTIFY, id, uri),
    type = kind.resultType,
    title = title,
    subtitle = artists,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isExplicit = isExplicit,
    versionLabel = if (kind == SpotifyCatalogItem.Kind.TRACK) versionFromTitle(title) else null,
    providerRank = rank,
    rankingScore = ordinalSearchScore(rank),
    supportedActions = when (kind) {
        SpotifyCatalogItem.Kind.ARTIST, SpotifyCatalogItem.Kind.ALBUM -> setOf(SearchResultAction.NAVIGATE)
        SpotifyCatalogItem.Kind.TRACK ->
            if (artists != null && durationMs != null && albumTitle != null) setOf(SearchResultAction.PLAY) else emptySet()
    },
    release = albumTitle?.let { SearchReleaseMetadata(it, releaseYear, releaseType) },
    artistNames = artistNames,
    artistIds = artistIds,
)

internal fun YTMusicCatalogItem.toUniversalSearchResult(): UniversalSearchResult {
    val type = when (kind) {
        YTMusicCatalogItem.Kind.TRACK -> SearchResultType.TRACK
        YTMusicCatalogItem.Kind.VIDEO -> SearchResultType.VIDEO
        YTMusicCatalogItem.Kind.ARTIST -> SearchResultType.ARTIST
        YTMusicCatalogItem.Kind.ALBUM -> SearchResultType.ALBUM
    }
    val canNavigate = type == SearchResultType.ARTIST || type == SearchResultType.ALBUM
    val identity = CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, id)
    val audio = identity.exactYouTubeAudio(type)
    val originalArtwork = artworkUrl?.takeIf { it.isNotBlank() }
    return UniversalSearchResult(
        identity = identity,
        audioIdentity = audio,
        type = type,
        title = title,
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        artworkUrl = if (type == SearchResultType.VIDEO) videoArtwork(id, originalArtwork) else originalArtwork,
        durationMs = durationMs,
        isExplicit = isExplicit,
        versionLabel = if (canNavigate) null else versionFromTitle(title),
        providerRank = rank,
        rankingScore = ordinalSearchScore(rank),
        supportedActions = when {
            canNavigate -> setOf(SearchResultAction.NAVIGATE)
            audio != null -> setOf(SearchResultAction.PLAY)
            else -> emptySet()
        },
        release = releaseTitle?.let { SearchReleaseMetadata(it, releaseYear, releaseType, artistBrowseId) },
    )
}

internal fun CatalogIdentity.exactYouTubeAudio(type: SearchResultType): AudioIdentity? {
    val playableType = when (source) {
        CatalogSource.YOUTUBE -> type == SearchResultType.VIDEO
        CatalogSource.YOUTUBE_MUSIC -> type == SearchResultType.TRACK || type == SearchResultType.VIDEO
        CatalogSource.SPOTIFY -> false
    }
    return if (playableType && YouTubeVideoId.isValid(catalogId)) AudioIdentity("youtube", catalogId) else null
}

internal fun UniversalSearchResult.exactPlaybackVideoId(): String? =
    audioIdentity?.takeIf {
        supports(SearchResultAction.PLAY) && it == identity.exactYouTubeAudio(type)
    }?.audioId

internal fun UniversalSearchResult.canPlayFromSearch(): Boolean =
    supports(SearchResultAction.PLAY) &&
        (exactPlaybackVideoId() != null || (identity.source == CatalogSource.SPOTIFY && type == SearchResultType.TRACK))

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
