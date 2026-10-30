package com.stash.feature.search

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.draw.blur
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.common.constants.StashConstants
import com.stash.data.ytmusic.model.SocialLink

internal fun supportUrl(socials: List<SocialLink>): String? =
    socials.firstOrNull {
        it.kind.equals("bandcamp", ignoreCase = true) ||
            it.url.contains("bandcamp.com", ignoreCase = true)
    }?.url ?: socials.firstOrNull {
        it.kind.equals("website", ignoreCase = true)
    }?.url

/**
 * Artist landing composition:
 * 1. one uninterrupted band photograph;
 * 2. centred wordmark/name and one primary Play button;
 * 3. the featured release and Top Songs follow immediately in the same tinted
 *    canvas (in ArtistProfileScreen), without a tab strip at the top.
 */
@Composable
fun ArtistHero(
    hero: HeroState,
    @Suppress("UNUSED_PARAMETER") status: ArtistProfileStatus,
    socials: List<SocialLink>,
    wordmarkUrl: String? = null,
    atmosphere: Color = ArtistNeutralAtmosphere,
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    onStartRadio: () -> Unit,
    streamingEnabled: Boolean,
    onStreamingClick: () -> Unit,
    onOpenAbout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val support = remember(socials) { supportUrl(socials) }
    val favorites = remember(ctx) {
        ctx.getSharedPreferences("sonora_artist_bookmarks_v1", 0)
    }
    val bookmarkKey = remember(hero.name) { "artist:" + hero.name.trim().lowercase() }
    var bookmarked by remember(bookmarkKey) {
        mutableStateOf(favorites.getBoolean(bookmarkKey, false))
    }
    var moreOpen by remember { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(wordmarkUrl)
    val wordmarkState by painter.state.collectAsState()

    Box(
        modifier = modifier.fillMaxWidth().height(382.dp)
            .background(atmosphere),
    ) {
        if (hero.avatarUrl != null) {
            val photo = ArtUrlUpgrader.upgrade(hero.avatarUrl)
            // A muted backdrop fills spare vertical space of wide band photos.
            // The foreground preserves *all horizontal members* without distortion.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                        .blur(22.dp)
                        .background(Color.Black.copy(alpha = .25f)),
                    alpha = .55f,
                )
            }
            AsyncImage(
                model = photo,
                contentDescription = "Fotografia de ${hero.name}",
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.matchParentSize(),
            )
        }
        // The photograph is already almost gone before the boundary.
        // Last pixel and the next LazyColumn item use the *same* atmosphere:
        // no horizontal seam when the featured card begins.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0x29000000),
                    .35f to Color.Transparent,
                    .49f to atmosphere.copy(alpha = .08f),
                    .62f to atmosphere.copy(alpha = .30f),
                    .76f to atmosphere.copy(alpha = .69f),
                    .90f to atmosphere.copy(alpha = .96f),
                    1f to atmosphere,
                ),
            ),
        )

        // Match the reference's quieter 38dp translucent circles while
        // retaining a full 48dp touch target for Android accessibility.
        ArtistTopAction(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding().padding(top = 4.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd)
                .statusBarsPadding().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistTopAction(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, hero.name)
                    }
                    runCatching {
                        ctx.startActivity(Intent.createChooser(intent, "Compartilhar artista"))
                    }
                },
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Compartilhar artista",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                ArtistTopAction(onClick = { moreOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Mais opções",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (bookmarked) "Desmarcar artista neste aparelho"
                                else "Marcar artista neste aparelho")
                        },
                        leadingIcon = {
                            Icon(
                                if (bookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            moreOpen = false
                            bookmarked = !bookmarked
                            favorites.edit().putBoolean(bookmarkKey, bookmarked).apply()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rádio do artista") },
                        leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null) },
                        onClick = { moreOpen = false; onStartRadio() },
                    )
                    if (onOpenAbout != null) {
                        DropdownMenuItem(
                            text = { Text("Sobre o artista") },
                            onClick = { moreOpen = false; onOpenAbout() },
                        )
                    }
                    if (StashConstants.STREAMING_ENGINE_ENABLED) {
                        DropdownMenuItem(
                            text = {
                                Text(if (streamingEnabled) "Streaming: ativado" else "Modo download")
                            },
                            onClick = { moreOpen = false; onStreamingClick() },
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

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 29.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wordmarkUrl != null && wordmarkState is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = hero.name,
                    modifier = Modifier.fillMaxWidth(.94f).height(68.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                val size = when {
                    hero.name.length <= 3 -> 63.sp
                    hero.name.length <= 12 -> 38.sp
                    hero.name.length <= 19 -> 31.sp
                    else -> 25.sp
                }
                Text(
                    hero.name,
                    color = Color.White,
                    fontSize = size,
                    lineHeight = size * 1.06f,
                    fontWeight = if (hero.name.length <= 3) FontWeight.Bold else FontWeight.Light,
                    letterSpacing = (-.7).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onPlayArtist,
                color = Color(0xFFF4F5F7),
                shape = CircleShape,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reproduzir ${hero.name}",
                        tint = Color(0xFF0E1115),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

/**
 * Reference-sized top action: 38dp visual surface in an accessible 48dp slot.
 * The old 48dp solid circles were much heavier and more widely spaced.
 */
@Composable
private fun ArtistTopAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp)
                .background(Color(0x700E1115), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = .10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
