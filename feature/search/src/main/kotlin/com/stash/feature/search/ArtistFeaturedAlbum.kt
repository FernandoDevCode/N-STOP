package com.stash.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.data.ytmusic.model.AlbumSummary

/**
 * A single featured release, following actual catalog order. Prefer a real
 * album with art and more than one track; never hardcode U2/Pink Floyd/etc.
 */
internal fun selectArtistFeaturedAlbum(
    albums: List<AlbumSummary>,
    singles: List<AlbumSummary>,
): AlbumSummary? =
    albums.firstOrNull { !it.thumbnailUrl.isNullOrBlank() && (it.trackCount ?: 2) > 1 }
        ?: albums.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
        ?: albums.firstOrNull()
        ?: singles.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
        ?: singles.firstOrNull()

/**
 * The reference's translucent release card. Chevron means "open album":
 * Sonora does not expose a save-album action from the artist VM, so we must not
 * show a deceptive + that actually navigates.
 */
@Composable
internal fun ArtistFeaturedAlbum(
    album: AlbumSummary,
    onOpen: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onOpen(album) },
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = .065f),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(.7.dp, Color.White.copy(alpha = .085f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(64.dp)
                    .background(Color(0xFF232831), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (album.thumbnailUrl != null) {
                    AsyncImage(
                        model = album.thumbnailUrl,
                        contentDescription = "Capa de ${album.title}",
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFD4D8E0),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.year ?: "Álbum em destaque",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBCC4CF),
                    maxLines = 1,
                )
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF4F5F7),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                album.trackCount?.takeIf { it > 0 }?.let { count ->
                    Text(
                        text = if (count == 1) "1 música" else "$count músicas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBCC4CF),
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = .09f),
                shape = CircleShape,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Abrir álbum ${album.title}",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}
