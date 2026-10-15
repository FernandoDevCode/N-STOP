package com.stash.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
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
        Box(Modifier.fillMaxWidth().height(350.dp)) {
            if (hero.avatarUrl != null) {
                AsyncImage(
                    model = ArtUrlUpgrader.upgrade(hero.avatarUrl),
                    contentDescription = hero.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF48296D), Color(0xFF142A3E), backdrop),
                        ),
                    ),
                )
            }
            // Soft dark wash keeps title legible for light and dark artist photos.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.29f),
                        0.32f to Color.Transparent,
                        0.67f to Color(0xFF090B18).copy(alpha = 0.65f),
                        1.0f to Color(0xFF090B18).copy(alpha = 0.94f),
                    ),
                ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 19.dp),
            ) {
                Text(
                    text = hero.name,
                    fontSize = 39.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hero.subscribersText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        hero.subscribersText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.28f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            if (com.stash.core.common.constants.StashConstants.STREAMING_ENGINE_ENABLED) {
                com.stash.core.ui.components.streaming.StreamingModeChip(
                    streamingEnabled = streamingEnabled,
                    onClick = onStreamingClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onPlayArtist,
                shape = CircleShape,
                color = Color(0xFFAF70F1),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play ${hero.name}",
                        tint = Color(0xFF100C18),
                        modifier = Modifier.size(29.dp),
                    )
                }
            }
            Surface(
                onClick = onStartRadio,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = "Start ${hero.name} radio",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (support != null) {
                Surface(
                    onClick = { runCatching { uriHandler.openUri(support) } },
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Support", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (socials.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                socials.forEach { social ->
                    Surface(
                        onClick = { runCatching { uriHandler.openUri(social.url) } },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val brand = socialIconFor(social.kind)
                            if (brand != null) {
                                Icon(
                                    painter = painterResource(brand),
                                    contentDescription = social.kind,
                                    modifier = Modifier.size(17.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = social.kind,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
