package com.stash.data.ytmusic

import com.stash.core.model.YouTubeVideoId
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.ArtistSummary
import com.stash.data.ytmusic.model.MusicVideoType
import com.stash.data.ytmusic.model.TopResultItem
import com.stash.data.ytmusic.model.TrackSummary
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToLong

data class YTMusicCatalogItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isExplicit: Boolean,
    val rank: Int,
) {
    enum class Kind { TRACK, VIDEO, ARTIST, ALBUM }
}

internal fun parseYTMusicCatalog(response: JsonObject): List<YTMusicCatalogItem> {
    val shelves = response.navigatePath("contents", "tabbedSearchResultsRenderer", "tabs")
        ?.asArray()?.firstOrNull()?.asObject()
        ?.navigatePath("tabRenderer", "content", "sectionListRenderer", "contents")?.asArray()
        ?: throw IOException("Unrecognized YouTube Music search response")
    var rank = 0
    return buildList {
        for (section in shelves) {
            val wrapper = section.asObject() ?: continue
            wrapper["musicCardShelfRenderer"]?.asObject()?.let { card ->
                parseTop(card, rank++)?.let { add(it) }
                // A top card can also carry a list of more results.
                card["contents"]?.asArray().orEmpty().forEach { row ->
                    row.asObject()?.let { parseRow(it, null, rank++) }?.let { add(it) }
                }
            }

            val shelf = (wrapper["musicShelfRenderer"] ?: wrapper["itemSectionRenderer"])
                ?.asObject() ?: continue
            val category = shelf.navigatePath("title", "runs")?.asArray()
                ?.firstOrNull()?.asObject()?.get("text")?.asString()
            for (entry in shelf["contents"]?.asArray().orEmpty()) {
                entry.asObject()?.let { parseRow(it, category, rank++) }?.let { add(it) }
            }
        }
    }
}

private fun parseTop(card: JsonObject, rank: Int): YTMusicCatalogItem? {
    val label = card.navigatePath("subtitle", "runs")?.asArray()
        ?.firstOrNull()?.asObject()?.get("text")?.asString()
    val watch = card.navigatePath("title", "runs")?.asArray()?.firstOrNull()?.asObject()
        ?.navigatePath("navigationEndpoint", "watchEndpoint")?.asObject()
    return when (val top = parseTopResultCard(card)) {
        is TopResultItem.ArtistTop -> top.artist.catalogItem(rank)
        is TopResultItem.TrackTop -> mediaKind(watch, label)?.let {
            top.track.catalogItem(it, rank, card.hasExplicitBadge())
        }
        null -> parseAlbumTopCard(card)?.catalogItem(rank)
    }
}

private fun parseRow(entry: JsonObject, category: String?, rank: Int): YTMusicCatalogItem? {
    val twoRow = entry["musicTwoRowItemRenderer"]?.asObject()
    if (twoRow != null) {
        if (twoRow.pageType() == "MUSIC_PAGE_TYPE_ALBUM" ||
            (twoRow.pageType() == null && category == "Albums")
        ) {
            return parseAlbumFromTwoRowItem(twoRow)?.catalogItem(rank)
        }
        return null
    }

    val row = entry["musicResponsiveListItemRenderer"]?.asObject() ?: return null
    when (row.pageType()) {
        "MUSIC_PAGE_TYPE_ARTIST" -> return parseArtistFromListItem(row)?.catalogItem(rank)
        "MUSIC_PAGE_TYPE_ALBUM" -> return parseAlbumFromListItem(row)?.catalogItem(rank)
        null -> if (category == "Artists") return parseArtistFromListItem(row)?.catalogItem(rank)
        else -> return null
    }
    val columns = row["flexColumns"]?.asArray()
    val label = columns?.getOrNull(1)?.asObject()
        ?.navigatePath("musicResponsiveListItemFlexColumnRenderer", "text", "runs")?.asArray()
        ?.takeIf { it.getOrNull(1)?.asObject()?.get("text")?.asString() == " • " }
        ?.firstOrNull()?.asObject()?.get("text")?.asString()
    val endpoints = listOfNotNull(
        row.navigatePath("navigationEndpoint", "watchEndpoint")?.asObject(),
        columns?.firstOrNull()?.asObject()
            ?.navigatePath("musicResponsiveListItemFlexColumnRenderer", "text", "runs")?.asArray()
            ?.firstOrNull()?.asObject()?.navigatePath("navigationEndpoint", "watchEndpoint")?.asObject(),
        row.navigatePath(
            "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
            "playNavigationEndpoint", "watchEndpoint",
        )?.asObject(),
    )
    val track = parseTrackSummaryFromListItem(
        row,
        fallbackVideoId = endpoints.firstNotNullOfOrNull { it["videoId"]?.asString() },
    ) ?: return null
    val watch = endpoints.firstOrNull {
        it["videoId"]?.asString() == track.videoId && it.rawMediaType() != null
    }
    val fallback = when (category) {
        "Songs" -> "Song"
        "Videos" -> "Video"
        else -> label
    }
    val kind = mediaKind(watch, fallback) ?: return null
    return track.catalogItem(kind, rank, row.hasExplicitBadge())
}

private fun JsonObject.pageType(): String? = navigatePath(
    "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig", "pageType",
)?.asString()

private fun JsonObject.rawMediaType(): String? = navigatePath(
    "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType",
)?.asString()

private fun mediaKind(watch: JsonObject?, label: String?): YTMusicCatalogItem.Kind? =
    when (MusicVideoType.fromInnerTube(watch?.rawMediaType())) {
        MusicVideoType.ATV -> YTMusicCatalogItem.Kind.TRACK
        MusicVideoType.OMV, MusicVideoType.UGC, MusicVideoType.OFFICIAL_SOURCE_MUSIC ->
            YTMusicCatalogItem.Kind.VIDEO
        MusicVideoType.PODCAST_EPISODE -> null
        null -> when (label) {
            "Song" -> YTMusicCatalogItem.Kind.TRACK
            "Video", "Music video" -> YTMusicCatalogItem.Kind.VIDEO
            else -> null
        }
    }

private fun JsonObject.hasExplicitBadge(): Boolean =
    this["badges"]?.asArray().orEmpty().any {
        it.asObject()?.navigatePath("musicInlineBadgeRenderer", "icon", "iconType")
            ?.asString() == "MUSIC_EXPLICIT_BADGE"
    }

private fun TrackSummary.catalogItem(
    kind: YTMusicCatalogItem.Kind,
    rank: Int,
    explicit: Boolean,
): YTMusicCatalogItem? {
    if (!YouTubeVideoId.isValid(videoId) || title.isBlank()) return null
    return YTMusicCatalogItem(
        kind, videoId, title, artist.takeIf { it.isNotBlank() }, thumbnailUrl,
        durationSeconds.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).roundToLong() },
        explicit, rank,
    )
}

private fun ArtistSummary.catalogItem(rank: Int): YTMusicCatalogItem? {
    if (id.isBlank() || name.isBlank()) return null
    return YTMusicCatalogItem(YTMusicCatalogItem.Kind.ARTIST, id, name, null, avatarUrl, null, false, rank)
}

private fun AlbumSummary.catalogItem(rank: Int): YTMusicCatalogItem? {
    if (id.isBlank() || title.isBlank()) return null
    return YTMusicCatalogItem(
        YTMusicCatalogItem.Kind.ALBUM, id, title, artist.takeIf { it.isNotBlank() },
        thumbnailUrl, null, false, rank,
    )
}
