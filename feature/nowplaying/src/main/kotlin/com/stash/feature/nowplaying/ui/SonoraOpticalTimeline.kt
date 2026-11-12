package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnSeek = rememberUpdatedState(onSeek)
    val safeProgress = progress.coerceIn(0f, 1f)
    val palette = recipe.progress.fill
    AndroidView(
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
            }
        },
        update = { view ->
            view.configure(
                palette.map { it.color.toInt() }.toIntArray(),
                palette.map { it.position }.toFloatArray(),
                recipe.progress.track.toInt(),
            )
            view.isEnabled = enabled
            view.setFraction(safeProgress)
        },
    )
}