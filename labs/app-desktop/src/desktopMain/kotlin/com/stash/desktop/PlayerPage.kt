package com.stash.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.model.*
import com.stash.feature.nowplaying.*
import com.stash.feature.nowplaying.ui.*

/** Host for the actual PlayerV7 renderer, with the Windows playback adapter. */
@Composable
internal fun PlayerPage(controller: AppController, player: PlayerState, library: List<Track>, onBack: () -> Unit,
    onQueue: () -> Unit, onLike: (Track) -> Unit, onMore: (Track) -> Unit) {
    val track = player.currentTrack
    var reading by rememberSaveable { mutableStateOf(false) }
    val lyrics by controller.lyrics.collectAsState()
    val lyricsBusy by controller.lyricsBusy.collectAsState()
    val direction by rememberDesktopArtDirection(track?.albumArtPath ?: track?.albumArtUrl)
    LaunchedEffect(track?.id, reading) { if (reading && track != null) controller.fetchLyrics(track) }
    val uiState = remember(player) { NowPlayingUiState(currentTrack = track, isPlaying = player.isPlaying,
        durationMs = player.durationMs, isSeekable = player.isSeekable, shuffleEnabled = player.isShuffleEnabled,
        repeatMode = player.repeatMode, isBuffering = player.isBuffering, playWhenReady = player.playWhenReady,
        queue = player.queue, queueSize = player.queue.size, currentIndex = player.currentIndex) }
    val progress: @Composable () -> Unit = {
        GlowingProgressBar(progress = { progressFraction(player.positionMs, player.durationMs) },
            accentColor = direction.progress, elapsedMs = { player.positionMs }, totalMs = player.durationMs,
            onSeek = controller.audio::seek, seekable = player.durationMs > 0,
            gradientColors = direction.accents.takeIf { it.size >= 3 }
                ?: listOf(lerp(direction.progress, Color.White, .18f), direction.progress))
    }
    val controls: @Composable (Boolean) -> Unit = { compact ->
        PlayerV7Transport(uiState, direction, compact = compact, canSkip = track != null,
            onPlayPause = controller.audio::toggle, onPrevious = controller.audio::previous,
            onNext = controller.audio::next, onShuffle = controller.audio::shuffle, onRepeat = controller.audio::repeat)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        PlayerV7Backdrop(track?.albumArtPath ?: track?.albumArtUrl, direction, animate = true)
        Column(Modifier.fillMaxSize()) {
            PlayerV7TopBar(onClose = { if (reading) reading = false else onBack() }) {
                Row {
                    IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, "Fila", tint = Color.White) }
                    IconButton(onClick = { track?.let(onMore) }, enabled = track != null) { Icon(Icons.Default.MoreVert, "Mais opções", tint = Color.White) }
                }
            }
            if (track == null) {
                Text("Escolha uma música para começar.", Modifier.padding(24.dp), color = Color.White)
            } else if (!reading) {
                PlayerV7MainLayout(
                    cover = { modifier -> Artwork(track.albumArtPath ?: track.albumArtUrl, modifier) },
                    progress = progress,
                    info = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-.6).sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(track.artist + if (track.album.isNotBlank()) " · ${track.album}" else "",
                                    fontSize = 14.sp, color = Color.White.copy(alpha = .73f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val liked = library.firstOrNull { it.id == track.id || track.youtubeId != null && it.youtubeId == track.youtubeId }?.stashLikedAt != null
                            PlayerV7Heart(liked, { onLike(track) }, direction.progress)
                        }
                    },
                    controls = { controls(false) }, onLyrics = { reading = true },
                )
            } else {
                Text(track.title, Modifier.padding(horizontal = 24.dp), color = Color.White,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(horizontal = 24.dp)) { progress() }
                if (lyricsBusy) CircularProgressIndicator(Modifier.padding(24.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lyrics?.let { text ->
                        item { Text(text.source, color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall) }
                        if (text.lines.isEmpty()) item { Text(text.plain, color = Color.White) }
                        else {
                            val active = text.lines.indexOfLast { it.timestampMs <= player.positionMs }
                            itemsIndexed(text.lines) { index, line ->
                                Text(line.text, Modifier.fillMaxWidth().clickable { controller.audio.seek(line.timestampMs) },
                                    color = if (index == active) Color.White else Color.White.copy(alpha = .5f),
                                    style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 18.dp)) { controls(true) }
            }
        }
    }
}
