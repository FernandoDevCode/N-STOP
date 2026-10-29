package com.stash.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.data.ytmusic.model.SocialLink

/** Artist support always opens the artist's Bandcamp or own site, never a fabricated follow. */
internal fun supportUrl(socials: List<SocialLink>): String? =
    socials.firstOrNull {
        it.kind.equals("bandcamp", ignoreCase = true) ||
            it.url.contains("bandcamp.com", ignoreCase = true)
    }?.url ?: socials.firstOrNull { it.kind.equals("website", ignoreCase = true) }?.url

/**
 * NØNStop cinematic artist hero: full-bleed real artist imagery and large readable
 * title, Spotify-style functional content sections continue below this hero.
 * A wordmark asset may be introduced when a licensed transparent artist logo
 * exists; plain typographic text is the intentional fallback.
 */
@Composable
fun ArtistHero(
    hero: HeroState,
    @Suppress("UNUSED_PARAMETER") status: ArtistProfileStatus,
    socials: List<SocialLink>,
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    onStartRadio: () -> Unit,
    streamingEnabled: Boolean,
    onStreamingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = MaterialTheme.colorScheme.background
    val support = remember(socials) { supportUrl(socials) }
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(315.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF343944), Color(0xFF191D23), backdrop),
                    ),
                ),
        ) {
            if (hero.avatarUrl != null) {
                AsyncImage(
                    model = ArtUrlUpgrader.upgrade(hero.avatarUrl),
                    contentDescription = hero.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Keep the photograph full-bleed. The lower scrim blends into the
            // Sonora canvas without a solid rectangle or a brand-coloured wash.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .35f),
                        .35f to Color.Transparent,
                        .63f to backdrop.copy(alpha = .35f),
                        .84f to backdrop.copy(alpha = .82f),
                        1f to backdrop,
                    ),
                ),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
                    .statusBarsPadding().padding(12.dp)
                    .background(Color.Black.copy(alpha = .42f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            if (com.stash.core.common.constants.StashConstants.STREAMING_ENGINE_ENABLED) {
                com.stash.core.ui.components.streaming.StreamingModeChip(
                    streamingEnabled = streamingEnabled,
                    onClick = onStreamingClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .statusBarsPadding().padding(12.dp),
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth().padding(start = 24.dp, end = 22.dp, bottom = 22.dp),
            ) {
                Text(
                    text = hero.name,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-1.5).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF4F5F7),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                hero.subscribersText?.let { count ->
                    Spacer(Modifier.height(5.dp))
                    Text(
                        count,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD4D8E0),
                    )
                }
            }
        }
        // The existing play, radio and support callbacks remain unchanged.
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                onClick = onPlayArtist,
                shape = CircleShape,
                color = Color(0xFFF4F5F7),
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play ${hero.name}",
                        tint = Color(0xFF0E1115),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Surface(
                onClick = onStartRadio,
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF191D23),
                border = BorderStroke(1.dp, Color(0xFF343944)),
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color(0xFFD4D8E0),
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Rádio", color = Color(0xFFF4F5F7),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.weight(1f))
            if (support != null) {
                Surface(
                    onClick = { runCatching { uriHandler.openUri(support) } },
                    shape = CircleShape,
                    color = Color(0xFF191D23),
                    border = BorderStroke(1.dp, Color(0xFF343944)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Support artist",
                            tint = Color(0xFFD4D8E0),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        // All external artist links live in AboutSection; no redundant social
        // strip below the hero. Support action above stays functional.
        Spacer(Modifier.height(4.dp))
    }
}
