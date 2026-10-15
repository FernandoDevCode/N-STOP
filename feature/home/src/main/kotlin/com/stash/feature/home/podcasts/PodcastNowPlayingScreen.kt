package com.stash.feature.home.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stash.feature.home.R

/** Same activity-scoped podcast controller, no new queue or MediaSession. */
@Composable
fun PodcastNowPlayingScreen(
    onDismiss: () -> Unit,
    viewModel: PodcastViewModel = LocalPodcastViewModel.current ?: hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.17f), background, background)),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.podcast_collapse_player),
                        tint = ink)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.podcast_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = ink)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            if (state.playingId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.podcast_no_active_episode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            } else {
                Spacer(Modifier.weight(0.5f))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val artSize = maxWidth.coerceAtMost(320.dp)
                    Box(
                        modifier = Modifier.size(artSize).clip(RoundedCornerShape(20.dp))
                            .background(surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Headphones, contentDescription = null,
                            modifier = Modifier.size(80.dp), tint = accent)
                        state.playingArtworkUrl?.let { artwork ->
                            AsyncImage(
                                model = artwork,
                                contentDescription = stringResource(R.string.podcast_cover_art),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(0.75f))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(state.playingTitle, modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = ink,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(state.playingPodcast.ifBlank { stringResource(R.string.podcast_title) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(24.dp))

                // Avoid fighting the finger with the one-second position refresh.
                var dragging by remember(state.playingId) { mutableFloatStateOf(-1f) }
                val actualProgress = if (state.durationMs > 0) {
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else 0f
                Slider(
                    value = if (dragging >= 0f) dragging else actualProgress,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        if (dragging >= 0f) viewModel.seek(dragging)
                        dragging = -1f
                    },
                    enabled = state.durationMs > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPodcastTime(state.positionMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text(formatPodcastTime(state.durationMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.jump(-15_000) }) {
                        Text("−15s", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(
                        onClick = viewModel::togglePause,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp))
                            .background(accent),
                    ) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.podcast_pause else R.string.podcast_play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp))
                    }
                    TextButton(onClick = { viewModel.jump(30_000) }) {
                        Text("+30s", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.podcast_speed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    listOf(1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        TextButton(onClick = { viewModel.setSpeed(speed) }) {
                            Text(speed.toString() + "×",
                                fontWeight = if (state.playbackSpeed == speed)
                                    FontWeight.Bold else FontWeight.Normal,
                                color = if (state.playbackSpeed == speed) accent else ink)
                        }
                    }
                }
                Spacer(Modifier.weight(0.6f))
            }
        }
    }
}

private fun formatPodcastTime(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%d:%02d".format(m, sec)
}
