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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.core.common.BrazilianDisplayFormat

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
 * Subtle tinted glass from the featured release ARTWORK, not the artist photo.
 * The artist's atmospheric background remains visible through the card.
 */
internal fun artistFeaturedAlbumGlassFill(artworkTone: Color): Color =
    lerp(Color.White, artworkTone, .72f).copy(alpha = .18f)

internal fun artistFeaturedAlbumGlassBorder(artworkTone: Color): Color =
    lerp(Color.White, artworkTone, .38f).copy(alpha = .14f)

/**
 * The reference's translucent release card. Chevron means "open album":
 * Sonora does not expose a save-album action from the artist VM, so we must not
 * show a deceptive + that actually navigates.
 */
@Composable
internal fun ArtistFeaturedAlbum(
    album: AlbumSummary,
    artworkTone: Color,
    onOpen: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onOpen(album) },
        modifier = modifier.fillMaxWidth(),
        color = artistFeaturedAlbumGlassFill(artworkTone),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.6.dp, artistFeaturedAlbumGlassBorder(artworkTone)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF232831), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (album.thumbnailUrl != null) {
                    AsyncImage(
                        model = album.thumbnailUrl,
                        contentDescription = "Capa de ${album.title}",
                        modifier = Modifier.size(62.dp),
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
                    text = "Lançamento em destaque",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBCC4CF),
                    maxLines = 1,
                )
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFF4F5F7),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
                )
                val details = listOfNotNull(
                    BrazilianDisplayFormat.releaseDate(album.year),
                    album.trackCount?.takeIf { it > 0 }?.let { if (it == 1) "1 música" else "$it músicas" },
                ).joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBCC4CF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = .05f),
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
