package com.stash.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.ui.components.ListenTogetherRole
import com.stash.core.ui.components.LocalListenTogetherRole
import com.stash.core.ui.components.SheetOptionRow
import com.stash.core.ui.components.playNextLabel

/** No blank catalog ID may be mistaken for a playing video. */
fun isRowPlaying(rowVideoId: String, currentPlayingYoutubeId: String?): Boolean =
    rowVideoId.isNotBlank() && rowVideoId == currentPlayingYoutubeId

/**
 * Sonora's density-first track row. Search, artist Popular and online albums
 * use the SAME row and the SAME long-press actions sheet.
 *
 * Tap plays; long press opens actions. Download is an action, not a permanent
 * trailing button. Only passive status (downloaded/downloading/resolving)
 * is shown inline. All existing playback and download callbacks are preserved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    item: SearchResultItem,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    isPreviewLoading: Boolean,
    isPreviewPlaying: Boolean,
    onPlay: () -> Unit,
    onStopPreview: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    isWaitingForLossless: Boolean = false,
    isResolving: Boolean = false,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    downloadSupported: Boolean = true,
    editorial: Boolean = false,
    editorialIndex: Int? = null,
    editorialAlbum: Boolean = false,
    editorialSubtitle: String? = null,
    artistEditorial: Boolean = false,
) {
    var optionsOpen by remember(item.videoId, item.title, item.artist) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val actionLabel = stringResource(R.string.sonora_track_actions)
    val action: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        optionsOpen = true
    }
    val artSize = if (artistEditorial) 44.dp else if (editorial) 46.dp else 52.dp
    val activeColor = MaterialTheme.colorScheme.primary
    val subtitle = editorialSubtitle ?: item.artist
    val rowBackground = if (isPlaying) activeColor.copy(alpha = 0.055f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("SongRow")
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .combinedClickable(
                onClick = { if (isPreviewPlaying) onStopPreview() else onPlay() },
                onLongClickLabel = actionLabel,
                onLongClick = action,
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(actionLabel) {
                    optionsOpen = true
                    true
                })
            }
            .heightIn(min = if (editorial) 54.dp else 68.dp)
            .padding(
                horizontal = if (editorial) 4.dp else 6.dp,
                vertical = if (editorial) 5.dp else 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editorial && editorialIndex != null) {
            Text(
                text = editorialIndex.toString(),
                modifier = Modifier.width(24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }

        // Albums show numbers and duration instead of repeating the same cover.
        if (!editorialAlbum) {
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isPreviewLoading || isResolving || isPreviewPlaying) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .54f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPreviewLoading || isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Stop, stringResource(R.string.sonora_track_stop),
                                tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    Icon(
                        Icons.Default.GraphicEq, null,
                        modifier = Modifier.size(15.dp),
                        tint = activeColor,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) activeColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!editorialAlbum && subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        // The status is information, not a second action target.
        when {
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp,
            )
            isDownloaded -> Icon(Icons.Default.CheckCircle,
                stringResource(R.string.sonora_track_downloaded),
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            isWaitingForLossless -> Icon(Icons.Outlined.Schedule,
                stringResource(R.string.sonora_track_pending),
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            item.durationSeconds > 0 -> Text(
                text = formatDuration(item.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (optionsOpen) {
        SearchTrackActionsSheet(
            item = item,
            isDownloading = isDownloading,
            isDownloaded = isDownloaded,
            isWaiting = isWaitingForLossless,
            downloadSupported = downloadSupported,
            onDownload = onDownload,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onStartRadio = onStartRadio,
            onDismiss = { optionsOpen = false },
        )
    }
}

/** All actions appear in one consistent, contextual Sonora bottom sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SearchTrackActionsSheet(
    item: SearchResultItem,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    isWaiting: Boolean,
    downloadSupported: Boolean,
    onDownload: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onStartRadio: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val role = LocalListenTogetherRole.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.thumbnailUrl != null) {
                        AsyncImage(item.thumbnailUrl, null, Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            if (downloadSupported && !isDownloaded && !isDownloading && !isWaiting) {
                SheetOptionRow(
                    icon = Icons.Default.Download,
                    label = stringResource(R.string.sonora_track_download),
                    onClick = { onDismiss(); onDownload() },
                )
            } else if (downloadSupported) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(when {
                            isDownloaded -> R.string.sonora_track_downloaded
                            isDownloading -> R.string.sonora_track_downloading
                            else -> R.string.sonora_track_pending
                        }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            onAddToPlaylist?.let { callback ->
                SheetOptionRow(Icons.Default.PlaylistAddCheck,
                    stringResource(R.string.sonora_track_playlist)) {
                    onDismiss(); callback()
                }
            }
            onPlayNext?.let { callback ->
                SheetOptionRow(Icons.Default.PlaylistPlay,
                    playNextLabel(role, stringResource(R.string.sonora_track_play_next))) {
                    onDismiss(); callback()
                }
            }
            if (role == ListenTogetherRole.NONE) {
                onAddToQueue?.let { callback ->
                    SheetOptionRow(Icons.Default.PlaylistAdd,
                        stringResource(R.string.sonora_track_queue)) {
                        onDismiss(); callback()
                    }
                }
                onStartRadio?.let { callback ->
                    SheetOptionRow(Icons.Default.Radio,
                        stringResource(R.string.sonora_track_radio)) {
                        onDismiss(); callback()
                    }
                }
            }
        }
    }
}

/** Keep the formatter shared by Search and album rows. */
internal fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%d:%02d".format(minutes, secs)
}
