package com.stash.feature.search

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.common.constants.StashConstants
import com.stash.core.ui.util.formatTotalDuration

/**
 * Cover-first layout measured against the approved three-screen Sonora visual
 * target. The artwork and metadata meet without a large empty spacer; controls
 * are circular/compact instead of taking over the whole width.
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
    downloadSupported: Boolean = true,
    onSave: () -> Unit = {},
    isSaved: Boolean = false,
    actionsEnabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(275.dp).background(
                Brush.verticalGradient(
                    listOf(Color(0xFF303740), Color(0xFF191D23), ink),
                ),
            ),
        ) {
            if (hero.thumbnailUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = hero.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(30.dp).alpha(.48f),
                )
            }
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x660E1115),
                        .40f to Color(0x66191D23),
                        .76f to ink.copy(alpha = .82f),
                        1f to ink,
                    ),
                ),
            )
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .padding(top = 12.dp)
                    .size(224.dp)
                    .shadow(
                        elevation = 17.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.Black.copy(alpha = .45f),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF232831)),
                contentAlignment = Alignment.Center,
            ) {
                if (hero.thumbnailUrl != null) {
                    AsyncImage(
                        model = hero.thumbnailUrl,
                        contentDescription = "${hero.title} — capa",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = Color(0xFFD4D8E0),
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(start = 12.dp, top = 9.dp)
                    .background(Color(0xAA0E1115), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color(0xFFF4F5F7),
                )
            }
            AlbumOverflow(
                onAddToQueue = onAddToQueue,
                onDownloadAll = onDownloadAll,
                onShuffle = onShuffle,
                onStreamingClick = onStreamingClick,
                hasDownloaded = hasDownloaded,
                downloadSupported = downloadSupported,
                streamingEnabled = streamingEnabled,
                enabled = actionsEnabled,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                    .padding(end = 14.dp, top = 9.dp),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = hero.title,
                fontSize = 30.sp,
                lineHeight = 33.sp,
                letterSpacing = (-.7).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4F5F7),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(hero.artist)
                    if (hero.year != null) {
                        append("  •  ")
                        append(hero.year.toString().take(4))
                    }
                },
                color = Color(0xFFB4B8C4),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hero.trackCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(hero.trackCount)
                        append(if (hero.trackCount == 1) " faixa" else " faixas")
                        if (hero.totalDurationMs > 0) {
                            append("  •  ")
                            append(formatTotalDuration(hero.totalDurationMs))
                        }
                    },
                    color = Color(0xFFB4B8C4),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPlayAlbum,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.nonstop_album_play),
                        modifier = Modifier.size(29.dp),
                    )
                }
                // The underlying action can shuffle only downloaded tracks.
                // Keep the editorial button visible but correctly disabled otherwise.
                Surface(
                    onClick = onShuffle,
                    enabled = actionsEnabled && hasDownloaded,
                    color = Color(0x99232831),
                    border = BorderStroke(1.dp, Color(0x553D4654)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (hasDownloaded) Color(0xFFF4F5F7) else Color(0xFF9297A1),
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = "Aleatório",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hasDownloaded) Color(0xFFF4F5F7) else Color(0xFF9297A1),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onSave,
                    enabled = actionsEnabled,
                    color = Color(0x99232831),
                    border = BorderStroke(1.dp, Color(0x553D4654)),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isSaved) "Álbum salvo" else "Salvar álbum",
                            tint = Color(0xFFF4F5F7),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                AlbumOverflow(
                    onAddToQueue = onAddToQueue,
                    onDownloadAll = onDownloadAll,
                    onShuffle = onShuffle,
                    onStreamingClick = onStreamingClick,
                    hasDownloaded = hasDownloaded,
                    downloadSupported = downloadSupported,
                    streamingEnabled = streamingEnabled,
                    enabled = actionsEnabled,
                )
            }
            Spacer(Modifier.height(15.dp))
        }
    }
}

@Composable
private fun AlbumOverflow(
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    onShuffle: () -> Unit,
    onStreamingClick: () -> Unit,
    hasDownloaded: Boolean,
    downloadSupported: Boolean,
    streamingEnabled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = Color(0x99232831),
            border = BorderStroke(1.dp, Color(0x553D4654)),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "Mais ações do álbum",
                    tint = Color(0xFFF4F5F7),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nonstop_album_queue)) },
                enabled = enabled,
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                },
                onClick = { open = false; onAddToQueue() },
            )
            if (hasDownloaded) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nonstop_album_shuffle)) },
                    enabled = enabled,
                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                    onClick = { open = false; onShuffle() },
                )
            }
            if (downloadSupported) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nonstop_album_download_all)) },
                    enabled = enabled,
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = { open = false; onDownloadAll() },
                )
            }
            if (StashConstants.STREAMING_ENGINE_ENABLED) {
                DropdownMenuItem(
                    text = {
                        Text(if (streamingEnabled) "Streaming: ativado" else "Modo download")
                    },
                    onClick = { open = false; onStreamingClick() },
                )
            }
        }
    }
}
