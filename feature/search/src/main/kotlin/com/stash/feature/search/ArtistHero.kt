package com.stash.feature.search

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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

/** Bound the photo lead-in, not the text/card: large fonts can grow naturally. */
internal fun artistPhotoLeadInDp(widthDp: Float, viewportHeightDp: Float): Float =
    (widthDp * .55f).coerceIn(144f, (viewportHeightDp * .40f).coerceIn(144f, 300f))

/**
 * Artist landing composition:
 * 1. one uninterrupted band photograph;
 * 2. centred wordmark/name and one primary Play button;
 * 3. the featured release shares the photograph and scrim; Top Songs follows
 *    on precisely the same atmosphere, without a separate hero/card boundary.
 */
@Composable
fun ArtistHero(
    hero: HeroState,
    @Suppress("UNUSED_PARAMETER") status: ArtistProfileStatus,
    socials: List<SocialLink>,
    wordmarkUrl: String? = null,
    heroAtmosphere: Color = ArtistNeutralPalette.hero,
    bridgeAtmosphere: Color = ArtistNeutralPalette.bridge,
    onHeroArtworkLoadFailed: (String) -> Unit = {},
    onEditPhoto: () -> Unit = {},
    onPhotoFrameMeasured: (Int) -> Unit = {},
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    isFollowing: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onStartRadio: () -> Unit,
    streamingEnabled: Boolean,
    onStreamingClick: () -> Unit,
    onOpenAbout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    featuredContent: (@Composable () -> Unit)? = null,
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
    val viewportHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clipToBounds().background(if (hero.avatarUrl.isNullOrBlank()) bridgeAtmosphere else Color.Transparent),
    ) {
        val photoLeadIn = artistPhotoLeadInDp(maxWidth.value, viewportHeightDp).dp
        // Compute the frame from source dimensions. Fit guarantees that no
        // part of a source photograph is lost to a viewport Crop/FillWidth.
        val photo = hero.avatarUrl?.let(ArtUrlUpgrader::upgrade)
        var photoAspectRatio by remember(photo) { mutableFloatStateOf(1.5f) }
        var photoBitmapDimensions by remember(photo) { mutableStateOf("não carregada") }
        val photoHeightDp = artistPhotoFrameHeightDp(
            maxWidth.value, viewportHeightDp, photoAspectRatio,
        ).dp

        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Fotografia de ${hero.name}",
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter,
                onError = { hero.avatarUrl?.let(onHeroArtworkLoadFailed) },
                onSuccess = { result ->
                    val image = result.result.image
                    if (image.width > 0 && image.height > 0) {
                        photoAspectRatio = image.width.toFloat() / image.height
                        photoBitmapDimensions = image.width.toString() + "x" + image.height
                        android.util.Log.d(
                            "SonoraArtistPhoto",
                            "render artist=" + hero.name + " bitmap=" + image.width + "x" + image.height +
                                " ratio=" + photoAspectRatio + " url=" + photo,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(photoHeightDp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Wide, feathered vertical fade dissolves the photo into
                        // the real photographic blur before the frame ends.
                        // The mask always reaches zero alpha at its boundary.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                .52f to Color.White,
                                .70f to Color.White.copy(alpha = .94f),
                                .84f to Color.White.copy(alpha = .53f),
                                .95f to Color.White.copy(alpha = .12f),
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        // A broad soft U: sides fade earlier than the centre.
                        // Both masks reach transparency before the photo frame
                        // ends; no dark edge or stretched image is drawn.
                        drawRect(
                            brush = Brush.radialGradient(
                                0f to Color.White,
                                .60f to Color.White,
                                .74f to Color.White.copy(alpha = .96f),
                                .86f to Color.White.copy(alpha = .66f),
                                .96f to Color.White.copy(alpha = .14f),
                                1f to Color.Transparent,
                                center = Offset(size.width * .5f, -size.height * .08f),
                                radius = artistPhotoFadeRadius(size.width, size.height),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }

        // A transparent-ended scrim must not create a full-width seam at
        // photoHeightDp. The original bottom 12% black alpha stopped abruptly
        // at this Box boundary even though the photo itself faded to zero.
        Box(
            Modifier.fillMaxWidth().height(photoHeightDp).drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .18f),
                        .24f to Color.Transparent,
                        .76f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                )
            },
        )

        // Only the photograph itself handles a long press. Top action buttons
        // and playback controls are drawn above this gesture layer.
        Box(
            Modifier.fillMaxWidth().height(photoHeightDp)
                .onSizeChanged { onPhotoFrameMeasured(it.height) }
                .pointerInput(onEditPhoto) {
                    detectTapGestures(onLongPress = { onEditPhoto() })
                }
                .semantics {
                    onLongClick("Editar fotografia do artista") {
                        onEditPhoto()
                        true
                    }
                },
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
                        text = { Text("Editar fotografia") },
                        onClick = { moreOpen = false; onEditPhoto() },
                    )
                    if (hero.spotifyArtistUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Foto fornecida pelo Spotify") },
                            onClick = {
                                moreOpen = false
                                runCatching { uriHandler.openUri(hero.spotifyArtistUrl) }
                            },
                        )
                    }
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
                    if (photo != null) {
                        DropdownMenuItem(
                            text = { Text("Copiar dados da fotografia") },
                            onClick = {
                                moreOpen = false
                                val clipboard = ctx.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE,
                                ) as? android.content.ClipboardManager
                                val details = "Artista: " + hero.name +
                                    "\nURL: " + photo + "\nBitmap: " + photoBitmapDimensions +
                                    "\nProporção: " + photoAspectRatio
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("Fotografia Sonora", details),
                                )
                            },
                        )
                    }
                }
            }
        }

        // Editorial identity stays close to the photograph; the play action
        // shares the same baseline rather than floating in an empty row.
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Spacer(Modifier.height(photoLeadIn))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (wordmarkUrl != null && wordmarkState is AsyncImagePainter.State.Success) {
                        Image(
                            painter = painter,
                            contentDescription = hero.name,
                            modifier = Modifier.fillMaxWidth().height(76.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                        )
                    } else {
                        val size = when {
                            hero.name.length <= 3 -> 56.sp
                            hero.name.length <= 12 -> 37.sp
                            hero.name.length <= 19 -> 30.sp
                            else -> 24.sp
                        }
                        Text(
                            hero.name,
                            color = Color.White,
                            fontSize = size,
                            lineHeight = size * 1.07f,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.7).sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "Artista",
                        color = Color.White.copy(alpha = .76f),
                        fontSize = 13.sp,
                    )
                }
                Surface(
                    onClick = onToggleFollow,
                    color = Color.White.copy(alpha = if (isFollowing) .24f else .13f),
                    shape = CircleShape,
                    border = BorderStroke(.7.dp, Color.White.copy(alpha = .52f)),
                    modifier = Modifier.height(48.dp),
                ) {
                    Box(
                        Modifier.padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isFollowing) "Seguindo" else "Seguir",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Surface(
                    onClick = onPlayArtist,
                    color = Color(0xFFE9EAED),
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Reproduzir ${hero.name}",
                            tint = Color(0xFF151A20),
                            modifier = Modifier.size(29.dp),
                        )
                    }
                }
            }
            if (featuredContent != null) {
                Spacer(Modifier.height(24.dp))
                featuredContent()
            }
        }
    }
}

/**
 * Source-photo-derived smoked glass, independent of background overlay opacity.
 * A light tint tracks the real photo hue while the charcoal body stays neutral.
 * The 58% fill opacity lets the original photography show through the disc.
 */
internal fun artistSmokedGlassFill(sourcePhotoTone: Color): Color =
    lerp(Color(0xFF111418), sourcePhotoTone.copy(alpha = 1f), .16f)
        .copy(alpha = .58f)

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
