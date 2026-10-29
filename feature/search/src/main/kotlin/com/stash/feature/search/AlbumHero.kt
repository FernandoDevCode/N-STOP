package com.stash.feature.search

import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.ui.theme.StashTheme
import com.stash.core.ui.util.formatTotalDuration

/**
 * Album Discovery hero card.
 *
 * Renders a square cover image (with gradient-placeholder fallback), a
 * bottom-edge scrim for text readability, a glass-background back button,
 * and a metadata block (title / artist+year / track-count+duration) followed
 * by an action-chip row.
 *
 * The Shuffle chip only renders when [hasDownloaded] is `true` (i.e. at
 * least one track in the album has a local file on disk) — shuffling an
 * album that has no downloaded tracks yet is a no-op, so we hide the
 * affordance until it would do something useful. Download-All is always
 * rendered; the dialog + confirm flow lives in [AlbumDiscoveryScreen] /
 * [AlbumDiscoveryViewModel].
 *
 * Visual/structure cribbed from `feature/library`'s private
 * `AlbumDetailHeader` so the two album pages feel like siblings. Parallax /
 * scroll-collapse is explicitly deferred (see spec §12).
 *
 * @param hero          Hero state (title/artist/thumbnail/year/track-count/
 *                      totalDuration). See [AlbumHeroState].
 * @param hasDownloaded Whether >= 1 of the album's tracks has already been
 *                      downloaded. Controls visibility of the Shuffle chip.
 * @param onBack        Invoked when the back arrow is tapped.
 * @param onShuffle     Invoked when the Shuffle chip is tapped. Only
 *                      reachable when [hasDownloaded] is `true`.
 * @param onDownloadAll Invoked when the Download-All chip is tapped. The
 *                      screen owns dialog + confirm wiring.
 * @param onPlayAlbum   Invoked when the Play chip is tapped. Plays the
 *                      album from index 0 — streaming-mode (Kennyy) or
 *                      downloaded-only depending on user preference,
 *                      routed through `PlayerRepository.setQueue`.
 * @param onAddToQueue  Invoked when the "Queue" chip is tapped. Appends the
 *                      album's tracks to the end of the current playback
 *                      queue without interrupting playback, routed through
 *                      `PlayerRepository.addToQueue(List)`.
 */
@Composable
fun AlbumHero(
    hero: AlbumHeroState,
    hasDownloaded: Boolean,
    onBack: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
    onPlayAlbum: () -> Unit,
    onAddToQueue: () -> Unit,
    streamingEnabled: Boolean,
    onStreamingClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Qobuz albums have no per-track videoId, so download-by-id isn't available
    // (Phase 1) — the chip is hidden for them. Defaults true for YT callers.
    downloadSupported: Boolean = true,
    /** #304: "Save" keeps the album in the library without downloading it. */
    onSave: () -> Unit = {},
    isSaved: Boolean = false,
    // False while there are no tracks (still loading, or the album couldn't be
    // loaded): Play, Queue, Save and Download all would do nothing, so they say so.
    actionsEnabled: Boolean = true,
) {
    var actionsOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Cover-first presentation, matching offline albums and playlists.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(298.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF303740), Color(0xFF191D23),
                            MaterialTheme.colorScheme.background),
                    ),
                ),
        ) {
            // Derived atmospheric wash from the actual cover, never a fixed
            // purple tint. On pre-Android 12 the neutral gradient remains.
            if (hero.thumbnailUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = hero.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp).alpha(.50f),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0xAA0E1115),
                        .34f to Color(0x44191D23),
                        .73f to Color(0xCC0E1115),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 20.dp)
                    .size(226.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Color.Black.copy(alpha = 0.55f),
                        spotColor = Color.Black.copy(alpha = 0.36f),
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF343944), Color(0xFF191D23)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (hero.thumbnailUrl != null) {
                    AsyncImage(
                        model = hero.thumbnailUrl,
                        contentDescription = "${hero.title} artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
                    .align(Alignment.TopStart)
                    .background(Color(0xAA0E1115), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            if (com.stash.core.common.constants.StashConstants.STREAMING_ENGINE_ENABLED) {
                com.stash.core.ui.components.streaming.StreamingModeChip(
                    streamingEnabled = streamingEnabled,
                    onClick = onStreamingClick,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                )
            }
        }

        // -- Metadata + action chips --
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = hero.title,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.8).sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = buildString {
                    append(hero.artist)
                    if (hero.year != null) {
                        append(" \u2022 ")
                        append(hero.year)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (hero.trackCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(hero.trackCount)
                        append(" track")
                        if (hero.trackCount != 1) append("s")
                        if (hero.totalDurationMs > 0) {
                            append(" \u2022 ")
                            append(formatTotalDuration(hero.totalDurationMs))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(13.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPlayAlbum,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.nonstop_album_play),
                        modifier = Modifier.size(29.dp),
                    )
                }
                OutlinedButton(
                    onClick = onAddToQueue,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.nonstop_album_queue),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                // Save is immediately reachable; optional operations live in the
                // menu instead of forcing Download all into a second row.
                IconButton(
                    onClick = onSave,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (isSaved) Icons.Default.LibraryAddCheck else Icons.Default.LibraryAdd,
                        contentDescription = if (isSaved) stringResource(R.string.nonstop_album_saved)
                            else stringResource(R.string.nonstop_album_save),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (downloadSupported || hasDownloaded) {
                    Box {
                        IconButton(
                            onClick = { actionsOpen = true },
                            enabled = actionsEnabled,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More album actions",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = actionsOpen,
                            onDismissRequest = { actionsOpen = false },
                        ) {
                            if (hasDownloaded) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.nonstop_album_shuffle)) },
                                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                                    onClick = { actionsOpen = false; onShuffle() },
                                )
                            }
                            if (downloadSupported) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.nonstop_album_download_all)) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = { actionsOpen = false; onDownloadAll() },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
