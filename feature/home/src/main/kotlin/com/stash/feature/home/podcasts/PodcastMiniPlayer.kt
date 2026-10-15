package com.stash.feature.home.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stash.core.ui.components.chromeGlass

/**
 * A compact alternative in the SAME global mini-player slot occupied by
 * music. No fake song like/queue controls; tapping the body opens Podcasts.
 */
@Composable
fun PodcastMiniPlayer(
    state: PodcastUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
) {
    if (state.playingId == null || !state.foreground) return
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxWidth().height(60.dp)
            .chromeGlass(RoundedCornerShape(12.dp), ring = false)
            .clickable(onClick = onExpand),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).drawBehind {
            drawRect(accent, size = Size(size.width * progress, size.height))
        })
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Headphones, contentDescription = null,
                tint = accent, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.playingTitle.ifBlank { "Podcast" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Podcast", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar podcast" else "Reproduzir podcast",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
