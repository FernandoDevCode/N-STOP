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
            val rareFragment = random.nextFloat() < .07f
            // Normal playback stays close to the contact: common fragments travel
            // roughly 12-18dp before expiring. Gesture speed opens the fan without
            // lengthening every lifetime, so a quick scrub remains tied to the thumb.
            val expansion = if (touching) 1f + speed * 1.45f else 1f
            val rareExpansion = if (rareFragment) 1.55f + speed * .35f else 1f
            val travel = (52f + random.nextFloat() * 28f) * expansion * rareExpansion * density
            // Scraped fragments mostly trail the contact. Some skim the rail;
            // others lift inward or fall outward into the existing navigation gap.
            val along = direction * travel * if (scatter < .82f) -1f else .45f
            val rawLift = when {
                scatter < .34f -> (16f + random.nextFloat() * 16f)
                scatter < .66f -> -(12f + random.nextFloat() * 12f)
                else -> (random.nextFloat() - .5f) * 8f
            }
            // Positive normal points into the left curve. Compress only the
            // outward component there, avoiding chips detached beyond the card.
            val edgeFactor = abs(tangent.y).coerceIn(0f, 1f)
            val lift = rawLift * (if (rawLift < 0f) 1f - edgeFactor * .72f else 1f) * density
            trail[i] = (.009f + random.nextFloat() * .012f) * (1f + speed * .7f)
            vx[i] = tangent.x * along + tangent.y * lift
            vy[i] = tangent.y * along - tangent.x * lift
            life[i] = .15f + random.nextFloat() * .09f + if (rareFragment) .06f else 0f
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
    val segment = Path()
    val cardClip = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
    // The inset centreline and straight cap keep the right ending inside the rounded card.
    val railStroke = Stroke(rail.strokeWidth, cap = StrokeCap.Butt)
    val trailStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
    val haloStroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
    val silver = Color(0xFFEAF4FF)
    val cool = Color(0xFF9BB8CF)
    onDrawWithContent {
        drawContent()
        val fraction = seek.displayFraction(position.value, durationMs)
        val distance = rail.length * fraction
        val contact = rail.point(fraction)
        val tangent = rail.tangent(fraction)
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
        if (distance > 0f) {
            rail.writePath(segment, until = fraction)
            drawPath(segment, Color(0xFF98A8B9), style = railStroke)
        }
        // Quiet organic detail only within 16dp behind the moving needle:
        // both sides expand and contract together, never deforming the actual
        // seek rail, touch projection or its straight right cap.
        if (state.motion && distance > 1f) {
            val start = (distance - 16.dp.toPx()).coerceAtLeast(0f)
            if (distance - start > 1f) {
                val liquid = Path()
                fun edge(step: Int, side: Float): Offset {
                    val t = step / 12f
                    val fractionAt = ((start + (distance - start) * t) / rail.length).coerceIn(0f, 1f)
                    val point = rail.point(fractionAt)
                    val tangentAt = rail.tangent(fractionAt)
                    val normal = Offset(-tangentAt.y, tangentAt.x)
                    val envelope = kotlin.math.sin(Math.PI.toFloat() * t).coerceAtLeast(0f)
                    val phase = frame / 1_000_000_000f
                    val breathing = .82f + .18f *
                        kotlin.math.sin(phase * 3.7f + t * 9f)
                    val halfWidth = rail.strokeWidth * .42f +
                        .45.dp.toPx() * envelope * breathing
                    return point + normal * halfWidth * side
                }
                for (step in 0..12) {
                    val p = edge(step, 1f)
                    if (step == 0) liquid.moveTo(p.x, p.y)
                    else liquid.lineTo(p.x, p.y)
                }
                for (step in 12 downTo 0) {
                    val p = edge(step, -1f)
                    liquid.lineTo(p.x, p.y)
                }
                liquid.close()
                drawPath(liquid, silver.copy(alpha = .60f))
            }
        }
        // Layered segments form a directional, tapered skid on the actual curved rail.
        val tail = (12f + particles.energy * 16f) * density
        for (j in 6 downTo 1) {
            val end = (distance - particles.direction * tail * j / 6f).coerceIn(0f, rail.length)
            segment.reset()
            if (end != distance) {
                rail.writePath(segment, minOf(distance, end) / rail.length, maxOf(distance, end) / rail.length)
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
        // The physical contact is always a circular white-silver needle. It is
        // intentionally last in the draw order so sparks can never swallow it.
        drawCircle(cool.copy(alpha = .12f), 7.dp.toPx(), contact)
        drawCircle(silver.copy(alpha = .24f), 5.dp.toPx(), contact)
        drawCircle(silver, 3.5.dp.toPx(), contact)
        drawCircle(Color.White.copy(alpha = .92f), 2.35.dp.toPx(), contact)
        drawCircle(Color.White, .9.dp.toPx(), contact - Offset(tangent.y, -tangent.x) * .65.dp.toPx())
    }
}
