package com.stash.feature.nowplaying

import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.stash.core.ui.ambient.rememberReducedMotion
import com.stash.desktop.LocalDesktopWindowVisible

/** Desktop frame clock adapter for the existing player atmosphere, not an audio clock. */
@Composable
internal fun rememberMiniFlare(visible: Boolean): State<Float> {
    val reduced = rememberReducedMotion()
    val active = visible && LocalDesktopWindowVisible.current
    val light = remember { Animatable(.4f) }
    LaunchedEffect(active, reduced) {
        if (reduced) light.snapTo(.5f)
        else if (active) while (true) {
            light.animateTo(.6f, tween(25000, easing = FastOutSlowInEasing))
            light.animateTo(.4f, tween(25000, easing = FastOutSlowInEasing))
        }
    }
    return light.asState()
}
