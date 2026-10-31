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
    showTrigger: Boolean = true,
    onClassicPlayer: (() -> Unit)? = null,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .72f).dp
    var moreActions by remember(expanded) { mutableStateOf(false) }
    Box {
        if (showTrigger) IconButton(onClick = { onExpanded(true) }, modifier = Modifier.size(40.dp).clip(CircleShape)
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
            onClassicPlayer?.let { item("Usar player compatível (V7)", Icons.Default.Settings, click = it) }
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


