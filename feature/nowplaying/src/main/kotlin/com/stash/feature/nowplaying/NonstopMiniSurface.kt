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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** 50 seconds per round trip, only 20% of width. No pulse or link to progress. */
@Composable
internal fun rememberMiniFlare(): State<Float> {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(false) }
    DisposableEffect(resolver) {
        fun refresh() { reduced = Settings.Global.getFloat(resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        refresh()
        resolver.registerContentObserver(Settings.Global.getUriFor(
            Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val light = remember { Animatable(.4f) }
    LaunchedEffect(lifecycle, reduced) {
        if (reduced) light.snapTo(.5f)
        else lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                while (true) {
                    light.animateTo(.6f, tween(25_000, easing = FastOutSlowInEasing))
                    light.animateTo(.4f, tween(25_000, easing = FastOutSlowInEasing))
                }
            }
        }
    }
    return light.asState()
}

internal fun Modifier.nonstopMiniSurface(flare: State<Float>) = drawBehind {
    val radius = CornerRadius(10.dp.toPx())
    val violetLight = Offset(18.dp.toPx(), size.height - 3.dp.toPx())
    drawCircle(Brush.radialGradient(listOf(Color(0xFF8B35FF).copy(alpha = .16f),
        Color.Transparent), violetLight, 28.dp.toPx()), 28.dp.toPx(), violetLight)
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF141526), Color(0xFF080D15),
        Color(0xFF101A27))), cornerRadius = radius)
    // Restrained top light: read animation in drawing, never recompose the transport per frame.
    drawRoundRect(Brush.radialGradient(listOf(Color(0xFF2477ED).copy(alpha = .18f),
        Color.Transparent), Offset(size.width * flare.value, 0f), size.width * .22f), cornerRadius = radius)
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFB6A4EE).copy(alpha = .8f),
        Color(0xFF788DA9).copy(alpha = .45f), Color(0xFF2861A1).copy(alpha = .4f))),
        cornerRadius = radius, style = Stroke(.6.dp.toPx()))
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF638DFF).copy(alpha = .55f), Color.Transparent)),
        Offset(size.width * (flare.value - .09f), .6.dp.toPx()),
        Offset(size.width * (flare.value + .09f), .6.dp.toPx()), .6.dp.toPx())
}
