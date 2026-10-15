// core/ui/src/main/kotlin/com/stash/core/ui/components/AlbumSquareCard.kt
package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.ui.components.motion.pressScale

/**
 * 152dp square editorial album card with rounded thumbnail, title, and "year • artist" subtitle.
 *
 * Used in search results and on Artist Profile discography rails.
 *
 * @param title Album title shown on the first text row.
 * @param artist Artist name for the subtitle.
 * @param thumbnailUrl Optional cover art URL; passed through [ArtUrlUpgrader] which
 *   asks the CDN for 1024x1024. Coil downsamples to the 140dp render target — the
 *   high-res request just guarantees crispness on high-DPI screens.
 * @param year Optional release year. When present the subtitle is `"$year • $artist"`,
 *   otherwise just the artist name.
 * @param modifier Optional layout modifier applied to the root column.
 * @param isLossless When true, overlays a small FLAC badge on the cover art
 *   (Premium Crisp quality signal). Defaults to false so existing callers are unaffected.
 * @param onClick Invoked on tap — typically opens the album detail screen.
 */
@Composable
fun AlbumSquareCard(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    year: String?,
    modifier: Modifier = Modifier,
    isLossless: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(152.dp)
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4B3479), Color(0xFF133A4D)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Placeholders are visible before loading and also if the CDN has
            // no artwork. No empty dark squares or abrupt fallback layout shifts.
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(48.dp),
            )
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ArtUrlUpgrader.upgrade(thumbnailUrl),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isLossless) {
                // Bottom-right, translucent: the industry-standard corner for a
                // quality mark, and see-through enough to not block the art.
                FlacBadge(
                    fileFormat = "flac",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .alpha(0.78f),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (year != null) "$year • $artist" else artist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
