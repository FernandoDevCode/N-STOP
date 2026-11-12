package nonstop.artlab.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Continuous, measured musical tendencies; NOT song names or hard genre categories. */
data class MotionInfluence(
    val ethereal: Float,
    val elastic: Float,
    val tense: Float,
    val attack: Float = 0f,
    val evolution: Float = 0f,
    val valid: Boolean = false,
) {
    companion object { val CALM = MotionInfluence(.72f, .20f, .08f) }
}

fun interface MusicalMotionSource {
    fun read(): MotionInfluence
}

/** Frame-only integration: inertial shape, short musical changes, long-form memory. */
@Stable
class OrganicMotionState {
    var revision by mutableIntStateOf(0)
        private set
    var phase = 0f
        private set
    var ethereal = .72f
        private set
    var elastic = .20f
        private set
    var tense = .08f
        private set
    var agitation = 0f
        private set
    var drift = 0f
        private set

    fun step(milliseconds: Float, input: MotionInfluence) {
        val dt = if (milliseconds.isFinite()) milliseconds.coerceIn(0f, 48f) / 1000f else 0f
        if (dt <= 0f) return
        val target = if (input.valid) input else MotionInfluence.CALM
        // Each family has a distinct response time, no resetting a pulse on each onset.
        fun follow(current: Float, wanted: Float, seconds: Float): Float =
            current + (wanted.coerceIn(0f, 1f) - current) * (1f - exp(-dt / seconds))
        ethereal = follow(ethereal, target.ethereal, 2.8f)
        elastic = follow(elastic, target.elastic, 1.9f)
        tense = follow(tense, target.tense, 1.45f)
        agitation = follow(agitation,
            if (input.valid) (input.attack * .55f + input.evolution * .45f) else 0f, .42f)
        val rate = .47f + elastic * .24f + tense * .31f
        phase += dt * rate
        // Slowly wandering offset; continuous and bounded rather than random resets.
        drift = sin(phase * .43f + sin(phase * .17f) * .9f) * .58f
        revision++
    }
}

/**
 * One material, three continuously mixed behaviors. Does not replace the V2
 * progress line, playhead or touch target. Only draws nearby translucent
 * deformations; no beam, particles, full-screen waveform or generic flashing.
 */
@Composable
fun OrganicMotionField(
    modifier: Modifier,
    progress: Float,
    localColor: Long,
    playing: Boolean,
    enabled: Boolean,
    source: MusicalMotionSource,
) {
    val state = remember(source) { OrganicMotionState() }
    LaunchedEffect(source, playing, enabled) {
        if (!playing || !enabled) return@LaunchedEffect
        var previous = 0L
        while (coroutineContext.isActive) withFrameNanos { frame ->
            if (previous != 0L) state.step((frame - previous) / 1_000_000f, source.read())
            previous = frame
        }
    }
    Canvas(modifier) {
        state.revision // draw-phase observation, no layout/recomposition dependency
        val density = 1.dp.toPx()
        val fraction = progress.coerceIn(0f, 1f)
        val centerX = 17f * density + (size.width - 34f * density).coerceAtLeast(1f) * fraction
        val centerY = size.height / 2f
        val color = Color(localColor)
        val spread = (19f + 7f * state.ethereal + 4f * state.elastic) * density
        val t = state.phase
        val lowWave = sin(t * 1.17f) * .55f + sin(t * .49f + 1.4f) * .45f
        val irregular = sin(t * 2.31f + sin(t * .65f)) * sin(t * .77f)
        val deflection = (1.1f + 2.7f * state.ethereal + 1.5f * state.elastic +
            1.2f * state.tense) * density
        val tilt = (lowWave * state.ethereal + irregular * state.tense * 1.4f +
            state.drift * .30f) * deflection
        val pinch = (1f + state.elastic * .60f + state.agitation * .24f)
        val y0 = centerY - tilt * .25f
        val left = centerX - spread
        val right = centerX + spread * .85f
        val upper = Path().apply {
            moveTo(left, centerY)
            cubicTo(centerX - spread * .62f, centerY - deflection * (.12f + state.elastic * .18f),
                centerX - spread * .18f, centerY - (deflection * pinch + tilt) * .82f,
                centerX, y0 - deflection * .22f)
            cubicTo(centerX + spread * .20f, centerY - (deflection + tilt) * .64f,
                centerX + spread * .57f, centerY - deflection * state.tense * .22f,
                right, centerY)
        }
        val lower = Path().apply {
            moveTo(left, centerY)
            cubicTo(centerX - spread * .53f, centerY + deflection * .13f,
                centerX - spread * .24f, centerY + (deflection * (.55f + state.elastic) - tilt) * .58f,
                centerX, centerY + deflection * .30f)
            cubicTo(centerX + spread * .21f, centerY + deflection * (.47f + state.tense * .18f),
                centerX + spread * .63f, centerY + deflection * .12f,
                right, centerY)
        }
        drawPath(upper, color.copy(alpha=.11f), style=Stroke(width=3.2f*density,cap=StrokeCap.Round))
        drawPath(lower, color.copy(alpha=.08f), style=Stroke(width=3.1f*density,cap=StrokeCap.Round))
        drawPath(upper, color.copy(alpha=.40f), style=Stroke(width=.85f*density,cap=StrokeCap.Round))
        drawPath(lower, color.copy(alpha=.29f), style=Stroke(width=.65f*density,cap=StrokeCap.Round))
        // Irregularity conveys tension without changing the user's seek indicator.
        val small = state.tense * state.agitation
        if (small > .10f) {
            val sx = centerX + spread * .33f
            val sy = centerY + irregular * deflection * .43f
            drawLine(color.copy(alpha=(small*.26f).coerceIn(0f,.20f)),
                Offset(sx - 3f*density, sy), Offset(sx + 2f*density, sy - 1.2f*density),
                strokeWidth=.65f*density,cap=StrokeCap.Round)
        }
    }
}
