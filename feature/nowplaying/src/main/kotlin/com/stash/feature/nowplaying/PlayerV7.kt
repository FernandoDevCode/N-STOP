package com.stash.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

import com.stash.core.model.RepeatMode
import com.stash.core.ui.ambient.rememberReducedMotion
import com.stash.feature.nowplaying.ui.PlayerArtDirection
import com.stash.feature.nowplaying.ui.PlayerColorMode
import com.stash.feature.nowplaying.ui.rememberNpArtRequest

internal val V7White = Color(0xFFF5F6F9)
internal val V7Muted = Color(0xFFA5ACBF)

/** Real artwork texture plus softly crossfaded art direction. Entire effect is window-clipped. */
@Composable
internal fun PlayerV7Backdrop(artModel: Any?, direction: PlayerArtDirection, animate: Boolean) {
    val reduced = rememberReducedMotion()
    // Reuse the existing lifecycle/reduced-motion-aware 50-second atmosphere clock.
    val drift = rememberMiniFlare(visible = animate)
    val dominant = animateColorAsState(direction.ambient.dom, tween(if (reduced) 0 else 900), label = "v7-ground")
    val reflected = animateColorAsState(direction.ambient.mut, tween(if (reduced) 0 else 900), label = "v7-reflection")
    val rim = animateColorAsState(direction.progress, tween(if (reduced) 0 else 900), label = "v7-rim")
    val canvas by animateColorAsState(direction.canvas, tween(if (reduced) 0 else 900), label = "v7-canvas")
    val artOpacity = animateFloatAsState(direction.artworkOpacity, tween(if (reduced) 0 else 900), label = "v7-art-opacity")
    val ambient = animateFloatAsState(direction.ambientStrength, tween(if (reduced) 0 else 900), label = "v7-ambient-strength")
    val lightX = animateFloatAsState(direction.lightX, tween(if (reduced) 0 else 900), label = "v7-light-x")
    val lightY = animateFloatAsState(direction.lightY, tween(if (reduced) 0 else 900), label = "v7-light-y")
    Box(Modifier.fillMaxSize().clipToBounds().background(canvas)) {
        AsyncImage(
            model = rememberNpArtRequest(artModel), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.2f; scaleY = 1.2f; alpha = artOpacity.value
            }.blur(32.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(Modifier.fillMaxSize().drawBehind {
            val driftX = (drift.value - .5f) * .12f
            val a = Offset(size.width * (.55f + lightX.value * .65f + driftX),
                size.height * (.05f + lightY.value * .30f))
            // Every gradient fades to the same hue at zero alpha; there is no
            // smaller rectangular blur surface or additive full-screen tint.
            // Width-based radii retain local light pools on tall phones instead
            // of averaging the whole screen into one translucent colour wash.
            drawRect(Brush.radialGradient(
                0f to dominant.value.copy(alpha = ambient.value),
                .35f to dominant.value.copy(alpha = ambient.value * .80f),
                1f to dominant.value.copy(alpha = 0f), center = a, radius = size.width * .78f))
            val b = Offset(size.width * (.02f + (1f - lightX.value) * .10f - driftX), size.height * .74f)
            drawRect(Brush.radialGradient(
                0f to reflected.value.copy(alpha = ambient.value * .86f),
                .35f to reflected.value.copy(alpha = ambient.value * .55f),
                1f to reflected.value.copy(alpha = 0f), center = b, radius = size.width * .75f))
            val c = Offset(size.width * .08f, size.height * .44f)
            drawRect(Brush.radialGradient(listOf(rim.value.copy(alpha = ambient.value * .20f),
                rim.value.copy(alpha = 0f)), c, size.width * .58f))
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .12f),
                .52f to Color.Black.copy(alpha = .27f), 1f to Color.Black.copy(alpha = .60f)))
        })
    }
}

/** Gestures are limited to the header/handle, never attached over the scrolling lyrics or seek. */
internal fun Modifier.playerV7VerticalGesture(onUp: () -> Unit = {}, onDown: () -> Unit = {}) =
    pointerInput(onUp, onDown) {
        var distance = 0f
        val threshold = 32.dp.toPx()
        detectVerticalDragGestures(
            onDragStart = { distance = 0f },
            onVerticalDrag = { change, amount -> distance += amount; change.consume() },
            onDragEnd = { if (distance > threshold) onDown() else if (distance < -threshold) onUp() },
            onDragCancel = { distance = 0f },
        )
    }

@Composable
internal fun PlayerV7TopBar(onClose: () -> Unit, menu: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)
        .playerV7VerticalGesture(onDown = onClose),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = .025f))
            .border(.6.dp, Color.White.copy(alpha = .12f), CircleShape)) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.v7_close), tint = V7White,
                modifier = Modifier.size(28.dp))
        }
        menu()
    }
}

/** Portrait artwork is 83% of the viewport width, as in the latest individual references. */
@Composable
internal fun PlayerV7MainLayout(
    cover: @Composable (Modifier) -> Unit,
    progress: @Composable () -> Unit,
    info: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    onLyrics: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val narrowHeight = maxHeight < 540.dp
            val artSide = minOf(maxWidth - 64.dp,
                if (narrowHeight) 300.dp else (maxHeight - 250.dp).coerceAtLeast(120.dp))
            Column(Modifier.fillMaxSize()
                .then(if (narrowHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(10.dp))
                cover(Modifier.size(artSide))
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    Spacer(Modifier.height(6.dp))
                    progress()
                    Spacer(Modifier.height(10.dp))
                    info()
                }
                if (!narrowHeight) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { controls() }
                if (!narrowHeight) Spacer(Modifier.weight(.45f)) else Spacer(Modifier.height(20.dp))
            }
        }
        val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(76.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .08f))))
            .playerV7VerticalGesture(onUp = onLyrics)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClick = onLyrics),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // No opaque panel: the same atmosphere continues under the handle.
            Box(Modifier.fillMaxWidth().height(.5.dp).background(Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .07f), Color.White.copy(alpha = 0f)))))
            Icon(Icons.Default.KeyboardArrowUp, null, tint = V7White,
                modifier = Modifier.padding(top = 5.dp).size(27.dp))
            Text(stringResource(R.string.v7_lyrics), color = V7Muted, fontSize = 10.sp,
                letterSpacing = 3.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun PlayerV7Transport(
    uiState: NowPlayingUiState,
    direction: PlayerArtDirection,
    compact: Boolean = false,
    canSkip: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    reaction: @Composable (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) 0 else 900
    val fill by animateColorAsState(direction.playFill, tween(duration), label = "v7-play-fill")
    val accent by animateColorAsState(direction.progress, tween(duration), label = "v7-control-accent")
    val glow by animateColorAsState(direction.playGlow, tween(duration), label = "v7-control-light")
    val strength = animateFloatAsState(direction.glowStrength, tween(duration), label = "v7-control-glow")
    val selective = direction.sparseHighlights && direction.mode == PlayerColorMode.SELECTIVE
    val circleSize = if (compact) 50.dp else 64.dp
    Row(Modifier.fillMaxWidth().height(if (compact) 64.dp else 100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (reaction != null) Spacer(Modifier.size(48.dp)) else IconButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, stringResource(R.string.nostop_np_shuffle),
                tint = if (uiState.shuffleEnabled) accent else V7Muted, modifier = Modifier.size(if (compact) 22.dp else 26.dp))
        }
        IconButton(onClick = onPrevious, enabled = canSkip) {
            Icon(Icons.Default.SkipPrevious, stringResource(R.string.nostop_np_previous),
                tint = V7White.copy(alpha = if (canSkip) 1f else .25f), modifier = Modifier.size(if (compact) 28.dp else 36.dp))
        }
        Box(Modifier.size(if (compact) 64.dp else 94.dp).drawBehind {
            val radius = size.minDimension / 2f
            // The halo peaks beside the solid disk, not underneath its opaque
            // centre; every edge reaches zero alpha on a real radial surface.
            val haloAlpha = if (selective) .10f else .14f + strength.value * .45f
            drawCircle(Brush.radialGradient(
                0f to glow.copy(alpha = 0f),
                .68f to glow.copy(alpha = haloAlpha),
                .85f to glow.copy(alpha = haloAlpha * .35f),
                1f to glow.copy(alpha = 0f), center = center, radius = radius), radius)
        }, contentAlignment = Alignment.Center) {
            val shape = CircleShape
            val colors = direction.accents.ifEmpty { listOf(accent, glow) }
            Box(Modifier.size(circleSize).clip(shape)
                // Material is painted inside the real circular clip. Reflection
                // changes value, not opacity/size, and cannot expose a blur box.
                .background(Brush.linearGradient(listOf(lerp(fill, Color.White, if (selective) .06f else .22f),
                    fill, lerp(fill, Color.Black, .12f))))
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (selective) .035f else .16f),
                        Color.White.copy(alpha = 0f)), Offset(size.width * .25f, size.height * .12f), size.width * .85f))
                }
                .border(if (selective) 1.dp else .45.dp,
                    if (selective) Brush.sweepGradient(colors + colors.first()) else
                        Brush.linearGradient(listOf(Color.White.copy(alpha = .40f), fill.copy(alpha = .12f), glow.copy(alpha = .38f))), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onPlayPause)
                .semantics { contentDescription = if (uiState.playWhenReady || uiState.isPlaying) "Pause" else "Play" },
                contentAlignment = Alignment.Center) {
                // Follow the interpolated material as well as the final palette.
                val ink = if (lerp(fill, Color.Black, .12f).luminance() > .18f) Color(0xFF080B10) else V7White
                if (uiState.isBuffering && (uiState.playWhenReady || uiState.isPlaying)) {
                    CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else Icon(if (uiState.playWhenReady || uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, tint = ink, modifier = Modifier.size(if (compact) 30.dp else 38.dp))
            }
        }
        IconButton(onClick = onNext, enabled = canSkip) {
            Icon(Icons.Default.SkipNext, stringResource(R.string.nostop_np_next),
                tint = V7White.copy(alpha = if (canSkip) 1f else .25f), modifier = Modifier.size(if (compact) 28.dp else 36.dp))
        }
        if (reaction != null) reaction()
        else IconButton(onClick = onRepeat) {
            Icon(if (uiState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                stringResource(R.string.nostop_np_repeat),
                tint = if (uiState.repeatMode == RepeatMode.OFF) V7Muted.copy(alpha = .65f) else accent,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp))
        }
    }
}

@Composable
internal fun PlayerV7Heart(isLiked: Boolean, onClick: () -> Unit, accent: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(Modifier.size(48.dp).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (isLiked) Icons.Default.Favorite else androidx.compose.material.icons.Icons.Outlined.FavoriteBorder,
            if (isLiked) "Unlike" else "Like", tint = if (isLiked) accent else V7White.copy(alpha = .8f),
            modifier = Modifier.size(size))
    }
}
