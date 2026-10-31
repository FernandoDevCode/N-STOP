package com.stash.feature.nowplaying

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.lerp
import com.stash.core.ui.ambient.AlbumChrome

/** 50 seconds per round trip, only 20% of width. No pulse or link to progress. */
@Composable
internal fun rememberMiniFlare(visible: Boolean): State<Float> {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun reducedMotion() = Settings.Global.getFloat(resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(reducedMotion()) }
    DisposableEffect(resolver) {
        fun refresh() { reduced = reducedMotion() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        refresh()
        resolver.registerContentObserver(Settings.Global.getUriFor(
            Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val light = remember { Animatable(.4f) }
    LaunchedEffect(lifecycle, reduced, visible) {
        if (reduced) light.snapTo(.5f)
        else if (visible) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                light.animateTo(.6f, tween(25_000, easing = FastOutSlowInEasing))
                light.animateTo(.4f, tween(25_000, easing = FastOutSlowInEasing))
            }
        }
    }
    return light.asState()
}

/**
 * Painted after the caller's shape clip: no external light, blur layer or animated outline.
 * AlbumChrome already crossfades the existing cover extractor's vibrant color. A single
 * selected hue keeps multicolor covers balanced; these mixes only darken its presentation.
 * Read both states during drawing so movement/palette updates never recompose the controls.
 */
internal fun Modifier.nonstopMiniSurface(flare: State<Float>, chrome: AlbumChrome) = drawBehind {
    val accent = chrome.vib
    val graphite = Color(0xFF080B10)
    drawRect(Brush.linearGradient(listOf(
        lerp(graphite, accent, .08f),
        lerp(graphite, accent, .13f),
        lerp(graphite, accent, .07f),
    )))

    // Broad fields with their centers outside the card: no point light or hard hotspot.
    // Only their horizontal position moves; intensity, radius and border stay constant.
    val upperLight = Offset(size.width * flare.value, -size.height * .9f)
    drawRect(Brush.radialGradient(
        0f to accent.copy(alpha = .14f),
        .5f to accent.copy(alpha = .09f),
        1f to accent.copy(alpha = 0f),
        center = upperLight, radius = size.width * .72f,
    ))
    val lowerLight = Offset(size.width * (1f - flare.value), size.height * 2.8f)
    drawRect(Brush.radialGradient(
        0f to accent.copy(alpha = .07f),
        1f to accent.copy(alpha = 0f),
        center = lowerLight, radius = size.width * .8f,
    ))
}
