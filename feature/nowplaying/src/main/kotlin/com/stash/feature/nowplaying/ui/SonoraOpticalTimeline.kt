package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import nonstop.artlab.renderer.MusicalMotionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.viewinterop.AndroidView
import nonstop.artlab.direction.ArtRecipe

/**
 * Android-only adapter. Reuses the V8 artwork recipe and actual Media3 seek callback.
 * The artwork classifier remains the single source of all chromatic decisions.
 */
@Composable
internal fun SonoraOpticalTimeline(
    progress: Float,
    recipe: ArtRecipe,
    enabled: Boolean,
    playing: Boolean,
    reducedMotion: Boolean,
    motionSource: MusicalMotionSource,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnSeek = rememberUpdatedState(onSeek)
    val safeProgress = progress.coerceIn(0f, 1f)
    val palette = recipe.progress.fill
    var timelineView by remember(motionSource) { mutableStateOf<SonoraOpticalTimelineView?>(null) }
    val latestProgress = rememberUpdatedState(safeProgress)
    LaunchedEffect(timelineView, motionSource, playing, reducedMotion) {
        val view = timelineView ?: return@LaunchedEffect
        view.setMotionEnabled(!reducedMotion)
        if (!playing || reducedMotion) return@LaunchedEffect
        var previous = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { frame ->
                if (previous != 0L) {
                    val dt = (frame - previous) / 1_000_000_000f
                    val values = motionSource.read()
                    view.setFraction(latestProgress.value)
                    view.updateMotion(
                        values.ethereal, values.elastic, values.tense,
                        values.attack, values.evolution, values.valid, dt,
                    )
                }
                previous = frame
            }
        }
    }
    key(motionSource) { AndroidView(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
            if (!enabled) disabled()
            setProgress { fraction ->
                if (enabled && fraction.isFinite()) {
                    latestOnSeek.value(fraction.coerceIn(0f, 1f))
                    true
                } else false
            }
        },
        factory = { context ->
            SonoraOpticalTimelineView(context).apply {
                setOnSeek { fraction -> latestOnSeek.value(fraction) }
                timelineView = this
            }
        },
        update = { view ->
            view.configure(
                palette.map { it.color.toInt() }.toIntArray(),
                palette.map { it.position }.toFloatArray(),
                recipe.progress.track.toInt(),
            )
            view.isEnabled = enabled
            view.setMotionEnabled(!reducedMotion)
            view.setFraction(safeProgress)
        },
    ) }
}