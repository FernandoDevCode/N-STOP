package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.model.Track
import com.stash.core.ui.ambient.LocalAlbumChrome

/**
 * The current REAL music track anchors the personalized Home even when
 * playlists/rediscovery mixes have not been generated yet. No fake history.
 * Selecting the card opens the already-running music Now Playing session.
 */
@Composable
internal fun NonstopListeningCard(
    track: Track,
    onOpenPlayer: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val artwork = track.albumArtPath ?: track.albumArtUrl
    val darkCanvas = Color(0xFF14181C)
    val accent = LocalAlbumChrome.current.selectedTab
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.nostop_home_continue_current),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(134.dp)
                .clip(shape)
                .background(darkCanvas, shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.17f), Color.Transparent, accent.copy(alpha = 0.055f)),
                    ),
                    shape,
                )
                .border(1.dp, accent.copy(alpha = 0.22f), shape)
                .clickable(onClick = onOpenPlayer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262A30)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null,
                    modifier = Modifier.size(36.dp), tint = accent)
                if (artwork != null) {
                    AsyncImage(
                        model = artwork, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.nostop_home_resume_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
                Text(
                    track.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = Color.White,
                )
                Text(
                    track.artist, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.70f),
                )
            }
            Icon(
                Icons.Filled.NorthEast,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
