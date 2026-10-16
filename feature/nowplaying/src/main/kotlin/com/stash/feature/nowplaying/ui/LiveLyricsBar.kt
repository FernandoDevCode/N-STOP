package com.stash.feature.nowplaying.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.TtmlLyrics
import com.stash.feature.nowplaying.R

/**
 * The v7 cover deliberately uses a compact LETRAS handle instead of always
 * exposing an extra song-line preview. A full upward swipe or a tap opens the
 * existing real synced/plain lyrics route; absence of lyrics hides the handle.
 */
internal sealed interface LiveBarMode {
    data class Live(val lines: List<LrcLine>, val syllables: TtmlLyrics? = null) : LiveBarMode
    data object Static : LiveBarMode
    data object Hidden : LiveBarMode
}

internal fun liveBarModeFor(state: LyricsViewState, liveEnabled: Boolean): LiveBarMode = when (state) {
    is LyricsViewState.Synced ->
        if (liveEnabled) LiveBarMode.Live(linesWithBreaks(state.lines), state.syllables) else LiveBarMode.Static
    is LyricsViewState.Plain -> LiveBarMode.Static
    else -> LiveBarMode.Hidden
}

/** Kept for compatibility with the existing lyric view and unit tests. */
internal val BarNoteSpacing = 10.dp

@Composable
fun LiveLyricsBar(
    state: LyricsViewState,
    currentPositionMs: () -> Long,
    accentColor: Color,
    liveEnabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    playbackSpeed: Float = 1f,
) {
    // Retain the same existing state gate: no invented content or empty panel.
    val visible = remember(state, liveEnabled) { liveBarModeFor(state, liveEnabled) != LiveBarMode.Hidden }
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val ink = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF241C36)
    val bg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF06060C) else Color(0xFFF4F1FA)
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val description = stringResource(R.string.nostop_np_open_lyrics)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (reducedMotion) 0 else 220)),
        exit = fadeOut(tween(if (reducedMotion) 0 else 170)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(bg.copy(alpha = 0.42f), bg.copy(alpha = 0.77f)),
                    ),
                )
                .border(1.dp, accentColor.copy(alpha = 0.19f), shape)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onTap)
                .pointerInput(onTap) {
                    var dragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onVerticalDrag = { change, amount ->
                            dragY += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragY < -25f) onTap()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = ink.copy(alpha = 0.95f),
                )
                Text(
                    text = stringResource(R.string.nostop_np_lyrics).uppercase(),
                    color = ink.copy(alpha = 0.66f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
