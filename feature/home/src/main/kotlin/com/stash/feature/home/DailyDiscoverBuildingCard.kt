package com.stash.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stash.core.ui.ambient.rememberReducedMotion
import com.stash.core.ui.components.ShimmerPlaceholder
import com.stash.core.ui.theme.StashBackground
import com.stash.core.ui.theme.StashElevation
import com.stash.core.ui.theme.StashPurpleDark

/**
 * Holds the Discover hero's slot while Daily Discover is still being built
 * ([HomeUiState.heroBuilding]), so a fresh install's Home says what is coming
 * instead of showing nothing where the hero will be. Same size, shape and
 * layout as the hero (`DiscoverHeroCard`) without art: a shimmer where the
 * cover will go, then the eyebrow, title and a line on what to expect. The
 * real hero replaces it in place when the first tracks land.
 *
 * Not clickable: there is nothing to open or play yet. TalkBack reads it as
 * one item. The shimmer holds still with animations turned off.
 */
@Composable
internal fun DailyDiscoverBuildingCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val coverShape = RoundedCornerShape(12.dp)
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .shadow(StashElevation.Hero, shape, clip = false)
            .clip(shape)
            // The hero's no-art backdrop, so the swap to the real card is quiet.
            .background(Brush.linearGradient(listOf(StashPurpleDark, StashBackground)))
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (reducedMotion) {
                Box(
                    Modifier
                        .size(118.dp)
                        .clip(coverShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            } else {
                ShimmerPlaceholder(modifier = Modifier.size(118.dp), shape = coverShape)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UPDATED DAILY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "Building your Daily Discover…",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Fresh finds from what you listen to. It appears here when it's ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
