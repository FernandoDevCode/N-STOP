// core/ui/src/main/kotlin/com/stash/core/ui/components/ArtistAvatarCard.kt
package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.ui.components.motion.pressScale

/**
 * Circular 96dp artist avatar tile with a centered name label.
 *
 * Used in search results and on Artist Profile screens. Tapping the card
 * invokes [onClick] — typically a navigation to the artist profile.
 *
 * @param name The artist's display name. Shown beneath the avatar, single-line ellipsized.
 * @param avatarUrl Optional avatar URL; passed through [ArtUrlUpgrader] which asks the
 *   CDN for 1024x1024. Coil downsamples in-memory to the 96dp render target — the
 *   high-res source guarantees crispness on 3x displays where 96dp = 288px.
 * @param modifier Optional layout modifier applied to the root column.
 * @param onClick Invoked on tap.
 */
@Composable
fun ArtistAvatarCard(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(104.dp)
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF513476), Color(0xFF18505A)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(43.dp),
            )
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ArtUrlUpgrader.upgrade(avatarUrl),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
