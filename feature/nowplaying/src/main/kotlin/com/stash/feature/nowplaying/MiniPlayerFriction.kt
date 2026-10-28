package com.stash.feature.nowplaying

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** Fixed storage; emission is time based, deliberately independent of song progress speed. */
internal class MiniFrictionParticles(seed: Int = Random.nextInt()) {
    private val random = Random(seed)
    val x = FloatArray(CAPACITY)
    val y = FloatArray(CAPACITY)
    val vx = FloatArray(CAPACITY)
    val vy = FloatArray(CAPACITY)
    val life = FloatArray(CAPACITY)
    val initialLife = FloatArray(CAPACITY)
    val weight = FloatArray(CAPACITY)
    val trail = FloatArray(CAPACITY)
    private var time = 0f
    var energy = 0f
        private set
    var direction = 1f
        private set
    private var credit = 0f
    private var cursor = 0
    val alive: Int get() = life.count { it > 0f }

    fun reset() { life.fill(0f); energy = 0f; credit = 0f }

    fun step(dt: Float, contact: Offset, density: Float, playing: Boolean,
             buffering: Boolean, touching: Boolean, velocity: Float, tangent: Offset = Offset(1f, 0f)) {
        time += dt
        val speed = (abs(velocity) / 1.5f).coerceIn(0f, 1f)
        if (touching && abs(velocity) > .015f) direction = if (velocity < 0) -1f else 1f
        else if (!touching && energy < .05f) direction = 1f
        val target = if (touching) .45f + speed * 1.8f else 0f
        energy += (target - energy) * (1f - exp(-dt * if (touching) 20f else 13f))
        val drag = exp(-dt * 2.3f)
        for (i in 0 until CAPACITY) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            vy[i] += 40f * density * dt
            vx[i] *= drag
        }
        val base = when { buffering -> 11f; playing -> 64f; else -> 0f }
        // Pausing stops emission immediately; existing fragments dissipate in < 450ms.
        val rate = (if (touching) base + 60f + speed * 130f else base) *
            (1f + .13f * kotlin.math.sin(time * 9.7f) + .07f * kotlin.math.sin(time * 23.1f))
        credit += rate * dt
        while (credit >= 1f) {
            credit -= 1f
            val i = cursor
            cursor = (cursor + 1) % CAPACITY
            x[i] = contact.x
            y[i] = contact.y
            val scatter = random.nextFloat()
            val travel = (65f + random.nextFloat() * 125f + energy * 120f) * density
            // Scraped fragments mostly trail the contact. Some skim the rail;
            // others lift inward or fall outward into the existing navigation gap.
            val along = direction * travel * if (scatter < .82f) -1f else .45f
            val outward = scatter in .32f.. .68f
            val lift = when {
                outward -> -(12f + random.nextFloat() * 22f) * density
                scatter < .32f -> (18f + random.nextFloat() * 48f) * density
                else -> (random.nextFloat() - .5f) * 10f * density
            }
            trail[i] = (.012f + random.nextFloat() * .025f) * (1f + energy * .65f)
            vx[i] = tangent.x * along + tangent.y * lift
            vy[i] = tangent.y * along - tangent.x * lift
            life[i] = .16f + random.nextFloat() * .27f
            initialLife[i] = life[i]
            weight[i] = (.65f + random.nextFloat() * .85f) * density
        }
    }

    companion object { const val CAPACITY = 96 }
}

@Stable
internal class MiniFrictionState {
    val particles = MiniFrictionParticles()
    var frame by mutableLongStateOf(0L)
    var playing = false
    var buffering = false
    var motion = false
    var lastFrame = 0L
}

@Composable
internal fun rememberMiniPlayerFriction(
    seek: MiniPlayerSeekState,
    playing: Boolean,
    buffering: Boolean,
    visible: Boolean,
): MiniFrictionState {
    val resolver = LocalContext.current.contentResolver
    fun readReduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(readReduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = readReduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val state = remember(seek) { MiniFrictionState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val touching = seek.dragging
    val currentPlaying by rememberUpdatedState(playing)
    val currentBuffering by rememberUpdatedState(buffering)
    val currentTouching by rememberUpdatedState(touching)
    SideEffect { state.playing = playing; state.buffering = buffering }
    LaunchedEffect(state, lifecycle, reduced, visible) {
        state.motion = !reduced && visible
        if (!state.motion) {
            state.particles.reset()
            state.lastFrame = 0L
            state.frame++
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.lastFrame = 0L
            try {
                snapshotFlow { Triple(currentPlaying, currentBuffering, currentTouching) }.collectLatest { (play, buffer, touch) ->
                    var start = 0L
                    state.lastFrame = 0L
                    do {
                        val now = withFrameNanos { it }
                        if (start == 0L) start = now
                        state.frame = now
                    } while (play || buffer || touch || now - start < 450_000_000L)
                    // A missed draw must never freeze a remnant after the clock stops.
                    state.particles.reset()
                    state.lastFrame = 0L
                    state.frame++
                }
            } finally {
                state.particles.reset()
                state.lastFrame = 0L
                state.frame++
            }
        }
    }
    return state
}

/** Frame state is read only by drawing: transport and screen composition never tick. */
internal fun Modifier.miniPlayerFriction(
    position: State<Long>, durationMs: Long, seek: MiniPlayerSeekState, state: MiniFrictionState,
    controlsBounds: () -> Rect = { Rect.Zero },
) = drawWithCache {
    val radius = MiniProgressCorner.toPx().coerceAtMost(size.width / 2f)
    val rail = MiniPlayerRail(size.width, size.height, radius)
    val path = Path().also(rail::writePath)
    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    val cardClip = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
    // The centreline is the card outline. The card clip leaves 2.5dp of stroke
    // inside the edge, without moving either endpoint away from its side.
    val railStroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
    val trailStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
    val haloStroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
    val silver = Color(0xFFEAF4FF)
    val cool = Color(0xFF9BB8CF)
    onDrawWithContent {
        drawContent()
        val fraction = seek.displayFraction(position.value, durationMs)
        val distance = measure.length * fraction
        val contact = measure.getPosition(distance)
        val tangent = measure.getTangent(distance).let {
            if (it == Offset.Zero || !it.x.isFinite() || !it.y.isFinite()) rail.tangent(fraction) else it
        }
        val frame = state.frame
        if (state.motion && frame != state.lastFrame) {
            val dt = if (state.lastFrame == 0L) 0f else ((frame - state.lastFrame) / 1_000_000_000f).coerceIn(0f, .04f)
            state.lastFrame = frame
            state.particles.step(dt, contact, density, state.playing, state.buffering, seek.dragging,
                seek.gestureVelocity(android.os.SystemClock.uptimeMillis()), tangent)
        }
        val particles = state.particles
        clipPath(cardClip) {
        drawPath(path, Color(0xFF4C5260), style = railStroke)
        segment.reset()
        if (measure.getSegment(0f, distance, segment, true)) {
            drawPath(segment, Color(0xFF98A8B9), style = railStroke)
        }
        // Layered segments form a directional, tapered skid on the actual curved rail.
        val tail = (28f + particles.energy * 34f) * density
        for (j in 6 downTo 1) {
            val end = (distance - particles.direction * tail * j / 6f).coerceIn(0f, measure.length)
            segment.reset()
            if (measure.getSegment(minOf(distance, end), maxOf(distance, end), segment, true)) {
                drawPath(segment, cool.copy(alpha = .035f), style = haloStroke)
                drawPath(segment, silver.copy(alpha = .065f), style = trailStroke)
            }
        }
        }
        // The modifier sits OUTSIDE the card clip. Light fades within the existing
        // 10dp gap, while this visual-only layer never participates in hit testing.
        val protected = controlsBounds()
        if (state.motion) clipRect(top = size.height - radius - 4.dp.toPx(), bottom = size.height + 8.dp.toPx()) {
          clipRect(protected.left, protected.top, protected.right, protected.bottom, ClipOp.Difference) {
          for (i in 0 until MiniFrictionParticles.CAPACITY) {
            if (particles.life[i] <= 0f) continue
            // Hot metal stays white for most of its short flight, then cools rapidly.
            // Fade before the top edge of the dedicated strip; never obscure metadata.
            val upperReach = if (particles.x[i] < 6f * density || particles.x[i] > size.width - 6f * density)
                radius + 4f * density else 10f * density
            val alpha = (2.4f * particles.life[i] / particles.initialLife[i]).coerceIn(0f, 1f) *
                ((particles.y[i] - size.height + upperReach) / (4f * density)).coerceIn(0f, 1f) *
                ((size.height + 8f * density - particles.y[i]) / (3f * density)).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val head = Offset(particles.x[i], particles.y[i])
            val streak = particles.trail[i]
            val back = Offset(head.x - particles.vx[i] * streak, head.y - particles.vy[i] * streak)
            val tint = if (i % 7 == 0) Color(0xFFCBC3EF) else silver
            drawLine(cool.copy(alpha = alpha * .13f), back, head, particles.weight[i] * 3.5f, StrokeCap.Round)
            drawLine(tint.copy(alpha = alpha * .95f), back, head, particles.weight[i], StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = alpha), particles.weight[i] * .65f, head)
            // Fleeting reflection directly underneath each airborne chip.
            drawLine(cool.copy(alpha = alpha * .12f), Offset(head.x - 2 * density, rail.cy + rail.r - density),
                Offset(head.x + 2 * density, rail.cy + rail.r - density), density)
          }
        }
        }
        // A compact white-silver blade, not an album-coloured circular thumb.
        val tip = contact + Offset(tangent.y, -tangent.x) * 7.dp.toPx()
        drawLine(cool.copy(alpha = .15f + particles.energy * .05f), contact, tip, (8f + particles.energy).dp.toPx(), StrokeCap.Round)
        drawLine(silver.copy(alpha = .45f), contact, tip, 3.5.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, contact, tip, 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, contact - tangent * 3.dp.toPx(),
            contact + tangent * 3.dp.toPx(), 1.5.dp.toPx(), StrokeCap.Round)
    }
}
