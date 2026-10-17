package com.stash.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.stash.core.data.prefs.NowPlayingAction
import com.stash.core.model.RepeatMode
import com.stash.core.ui.ambient.rememberReducedMotion
import com.stash.feature.nowplaying.ui.PlayerArtDirection
import com.stash.feature.nowplaying.ui.PlayerColorMode
import com.stash.feature.nowplaying.ui.rememberNpArtRequest

private val V7White = Color(0xFFF5F6F9)
private val V7Muted = Color(0xFFA5ACBF)
private val V7Ground = Color(0xFF05080D)

/** Real artwork texture plus softly crossfaded art direction. Entire effect is window-clipped. */
@Composable
internal fun PlayerV7Backdrop(artModel: Any?, direction: PlayerArtDirection, animate: Boolean) {
    val reduced = rememberReducedMotion()
    // Reuse the existing lifecycle/reduced-motion-aware 50-second atmosphere clock.
    val drift = rememberMiniFlare(visible = animate)
    val dominant = animateColorAsState(direction.ambient.dom, tween(if (reduced) 0 else 900), label = "v7-ground")
    val reflected = animateColorAsState(direction.ambient.mut, tween(if (reduced) 0 else 900), label = "v7-reflection")
    Box(Modifier.fillMaxSize().clipToBounds().background(V7Ground)) {
        AsyncImage(
            model = rememberNpArtRequest(artModel), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.15f; scaleY = 1.15f; alpha = .42f
            }.blur(38.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(Modifier.fillMaxSize().drawBehind {
            val a = Offset(size.width * drift.value, size.height * .18f)
            drawRect(Brush.radialGradient(listOf(dominant.value.copy(alpha = .30f),
                dominant.value.copy(alpha = 0f)), a, size.maxDimension * .75f))
            val b = Offset(size.width * (1f - drift.value), size.height * .76f)
            drawRect(Brush.radialGradient(listOf(reflected.value.copy(alpha = .24f),
                reflected.value.copy(alpha = 0f)), b, size.maxDimension * .55f))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f),
                V7Ground.copy(alpha = .34f), Color.Black.copy(alpha = .76f))))
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
            .clip(shape).background(Color(0x320C111A))
            .playerV7VerticalGesture(onUp = onLyrics)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClick = onLyrics),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Static top seam, clipped with the same rounded panel; never a glow layer.
            Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .10f)))
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
    onReact: ((String) -> Unit)? = null,
) {
    val fill by animateColorAsState(direction.playFill, tween(800), label = "v7-play-fill")
    val accent by animateColorAsState(direction.progress, tween(800), label = "v7-control-accent")
    val glow by animateColorAsState(direction.playGlow, tween(800), label = "v7-control-light")
    val selective = direction.mode == PlayerColorMode.SELECTIVE
    val circleSize = if (compact) 50.dp else 64.dp
    Row(Modifier.fillMaxWidth().height(if (compact) 64.dp else 100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (onReact != null) Spacer(Modifier.size(48.dp)) else IconButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, stringResource(R.string.nostop_np_shuffle),
                tint = if (uiState.shuffleEnabled) accent else V7Muted, modifier = Modifier.size(if (compact) 22.dp else 26.dp))
        }
        IconButton(onClick = onPrevious, enabled = canSkip) {
            Icon(Icons.Default.SkipPrevious, stringResource(R.string.nostop_np_previous),
                tint = V7White.copy(alpha = if (canSkip) 1f else .25f), modifier = Modifier.size(if (compact) 28.dp else 36.dp))
        }
        Box(Modifier.size(if (compact) 64.dp else 94.dp).drawBehind {
            val radius = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .22f),
                glow.copy(alpha = 0f)), center, radius), radius)
        }, contentAlignment = Alignment.Center) {
            val shape = CircleShape
            val colors = direction.accents.ifEmpty { listOf(accent, glow) }
            Box(Modifier.size(circleSize).clip(shape)
                .background(if (selective) Color(0xAD0B1018) else fill, shape)
                .then(if (selective) Modifier.border(1.dp, Brush.sweepGradient(colors + colors.first()), shape) else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onPlayPause)
                .semantics { contentDescription = if (uiState.playWhenReady || uiState.isPlaying) "Pause" else "Play" },
                contentAlignment = Alignment.Center) {
                val ink = if (selective) V7White else direction.playInk
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
        if (onReact != null) com.stash.feature.nowplaying.listen.ReactionButton(onReact = onReact)
        else IconButton(onClick = onRepeat) {
            Icon(if (uiState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                stringResource(R.string.nostop_np_repeat),
                tint = if (uiState.repeatMode == RepeatMode.OFF) V7Muted.copy(alpha = .65f) else accent,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp))
        }
    }
}

/** One anchored, scrollable menu includes ALL available actions, including previously pinned ones. */
@Composable
internal fun PlayerV7Menu(
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    actions: List<NowPlayingAction>,
    visualState: ActionVisualState,
    accent: Color,
    onAction: (NowPlayingAction) -> Unit,
    onTimer: () -> Unit,
    onAudio: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .72f).dp
    var moreActions by remember(expanded) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onExpanded(true) }, modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = .025f)).border(.6.dp, Color.White.copy(alpha = .12f), CircleShape)) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.v7_actions), tint = V7White, modifier = Modifier.size(25.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) },
            modifier = Modifier.widthIn(min = 242.dp, max = 280.dp).heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp), containerColor = Color(0xFA091019),
            tonalElevation = 0.dp, shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(.5.dp, Color(0xFF323A48))) {
            fun closeThen(action: () -> Unit) { onExpanded(false); action() }
            @Composable fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean = false, click: () -> Unit) {
                DropdownMenuItem(text = { Text(label, fontSize = 13.sp, color = V7White) },
                    leadingIcon = { Icon(icon, null, tint = if (active) accent else V7White, modifier = Modifier.size(22.dp)) },
                    onClick = { closeThen(click) }, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp))
            }
            if (NowPlayingAction.QUEUE in actions) item(stringResource(R.string.v7_queue), actionVisual(NowPlayingAction.QUEUE, visualState).icon) { onAction(NowPlayingAction.QUEUE) }
            item(stringResource(R.string.v7_timer), Icons.Default.Timer, click = onTimer)
            item(stringResource(R.string.v7_audio), Icons.Outlined.GraphicEq, click = onAudio)
            val primary = listOf(NowPlayingAction.LISTEN_TOGETHER, NowPlayingAction.SHARE)
            primary.filter { it in actions }.forEach { action ->
                val visual = actionVisual(action, visualState)
                item(stringResource(if (action == NowPlayingAction.LISTEN_TOGETHER) R.string.v7_together else R.string.v7_share),
                    visual.icon, visual.active) { onAction(action) }
            }
            val secondary = actions.filter { it != NowPlayingAction.QUEUE && it !in primary }
            if (secondary.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.v7_more), fontSize = 13.sp, color = V7Muted) },
                    trailingIcon = { Icon(if (moreActions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, tint = V7Muted) }, onClick = { moreActions = !moreActions })
                if (moreActions) {
                    HorizontalDivider(color = Color.White.copy(alpha = .08f))
                    secondary.forEach { action ->
                        val visual = actionVisual(action, visualState)
                        item(visual.label, visual.icon, visual.active) { onAction(action) }
                    }
                }
            }
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
