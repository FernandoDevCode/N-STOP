package com.stash.feature.search

import com.stash.core.model.TrackItem
import com.stash.data.ytmusic.model.TrackSummary

/** Track metadata for the shared artist and album rows. */
data class SearchResultItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Double,
    val thumbnailUrl: String? = null,
)

internal fun TrackSummary.toSearchResultItem() = SearchResultItem(
    videoId = videoId,
    title = title,
    artist = artist,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
)

/**
 * Adapter: [TrackSummary] → [com.stash.core.model.TrackItem].
 *
 * Used at download call sites in the screen — the delegate's `downloadTrack`
 * takes a [com.stash.core.model.TrackItem] (the minimal identity
 * needed to kick off a yt-dlp download). Kept next to [toSearchResultItem]
 * so the two adapters are easy to compare.
 */
internal fun TrackSummary.toTrackItem() = TrackItem(
    videoId = videoId,
    title = title,
    artist = artist,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    album = album,
)

/**
 * Adapter: [SearchResultItem] → [com.stash.core.model.TrackItem].
 *
 * [PopularTracksSection]'s `onDownload` callback surfaces the row as a
 * [SearchResultItem] (since the composable converts internally via
 * [toSearchResultItem] to feed [PreviewDownloadRow]). Screens that route
 * that callback into `delegate.downloadTrack` use this one-liner.
 */
internal fun SearchResultItem.toTrackItem() = TrackItem(
    videoId = videoId,
    title = title,
    artist = artist,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
)
