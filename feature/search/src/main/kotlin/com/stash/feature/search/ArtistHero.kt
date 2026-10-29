package com.stash.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.common.constants.StashConstants
import com.stash.data.ytmusic.model.SocialLink

/** Support is a real artist site, never an invented follow/like action. */
internal fun supportUrl(socials: List<SocialLink>): String? =
    socials.firstOrNull {
        it.kind.equals("bandcamp", ignoreCase = true) ||
            it.url.contains("bandcamp.com", ignoreCase = true)
    }?.url ?: socials.firstOrNull {
        it.kind.equals("website", ignoreCase = true)
    }?.url

/**
 * Sonora editorial hero. The image, name, audience and controls share a single
 * cinematic canvas. The preceding layout spent an entire extra row on buttons,
 * pushing Popular significantly below the approved reference.
 */
@Composable
fun ArtistHero(
    hero: HeroState,
    @Suppress("UNUSED_PARAMETER") status: ArtistProfileStatus,
    socials: List<SocialLink>,
    wordmarkUrl: String? = null,
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    onStartRadio: () -> Unit,
    streamingEnabled: Boolean,
    onStreamingClick: () -> Unit,
    onOpenAbout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backdrop = MaterialTheme.colorScheme.background
    val support = remember(socials) { supportUrl(socials) }
    val uriHandler = LocalUriHandler.current
    var moreOpen by remember { mutableStateOf(false) }
    val wordmarkPainter = rememberAsyncImagePainter(model = wordmarkUrl)
    val wordmarkState by wordmarkPainter.state.collectAsState()

    Box(
        modifier = modifier.fillMaxWidth().height(348.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF232831), backdrop))),
    ) {
        if (hero.avatarUrl != null) {
            AsyncImage(
                model = ArtUrlUpgrader.upgrade(hero.avatarUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .28f),
                    .36f to Color.Transparent,
                    .57f to backdrop.copy(alpha = .22f),
                    .79f to backdrop.copy(alpha = .77f),
                    1f to backdrop,
                ),
            ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 9.dp)
                .background(Color(0xAA0E1115), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = Color(0xFFF4F5F7),
            )
        }
        // Upper-right overflow mirrors the reference. The same actions are
        // available from the lower row; both share the menu state.
        IconButton(
            onClick = { moreOpen = true },
            modifier = Modifier.align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 9.dp)
                .background(Color(0xAA0E1115), CircleShape),
        ) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = "Mais ações do artista",
                tint = Color(0xFFF4F5F7),
            )
        }
        // The controls sit inside the image, as in the approved reference.
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
        ) {
            if (wordmarkUrl != null && wordmarkState is AsyncImagePainter.State.Success) {
                Image(
                    painter = wordmarkPainter,
                    contentDescription = hero.name,
                    modifier = Modifier.fillMaxWidth(.74f).height(62.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = hero.name,
                    fontSize = 43.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1.4).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F5F7),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            hero.subscribersText?.let { count ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = count,
                    color = Color(0xFFD4D8E0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            contentDescription = "Reproduzir ${hero.name}",
                            tint = Color(0xFF0E1115),
                            modifier = Modifier.size(29.dp),
                        )
                    }
                }
                Surface(
                    onClick = onStartRadio,
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0x99232831),
                    border = BorderStroke(1.dp, Color(0x553D4654)),
                    modifier = Modifier.height(45.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null,
                            tint = Color(0xFFF4F5F7), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Rádio",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFF4F5F7),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (support != null) {
                    Surface(
                        onClick = { runCatching { uriHandler.openUri(support) } },
                        shape = CircleShape,
                        color = Color(0x99232831),
                        border = BorderStroke(1.dp, Color(0x553D4654)),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = "Apoiar artista",
                                tint = Color(0xFFF4F5F7),
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
                Box {
                    Surface(
                        onClick = { moreOpen = true },
                        shape = CircleShape,
                        color = Color(0x99232831),
                        border = BorderStroke(1.dp, Color(0x553D4654)),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = "Mais ações do artista",
                                tint = Color(0xFFF4F5F7),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = moreOpen,
                        onDismissRequest = { moreOpen = false },
                    ) {
                        if (onOpenAbout != null) {
                            DropdownMenuItem(
                                text = { Text("Sobre o artista") },
                                onClick = {
                                    moreOpen = false
                                    onOpenAbout()
                                },
                            )
                        }
                        if (StashConstants.STREAMING_ENGINE_ENABLED) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (streamingEnabled) "Streaming: ativado" else "Modo download")
                                },
                                onClick = {
                                    moreOpen = false
                                    onStreamingClick()
                                },
                            )
                        }
                        if (support != null) {
                            DropdownMenuItem(
                                text = { Text("Site do artista") },
                                onClick = {
                                    moreOpen = false
                                    runCatching { uriHandler.openUri(support) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
