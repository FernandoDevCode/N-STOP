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
        state.revision // draw-only invalidation: no per-frame layout or recomposition
        if (!enabled || !playing) return@Canvas
        val unit = 1.dp.toPx()
        val left = 17f * unit
        val playable = (size.width - 34f * unit).coerceAtLeast(1f)
        val tip = left + playable * progress.coerceIn(0f, 1f)
        // The sound never creates anything in front of the playhead. Keep the
        // optical native V2 thumb untouched; apply a nearly imperceptible
        // organic modulation across the ALREADY PLAYED material only.
        val end = tip - 9f * unit
        if (end - left < 3f * unit) return@Canvas
        val centerY = size.height * .5f
        val color = Color(localColor)
        val p = Path()
        val phase = state.phase
        val amplitude = (.23f + state.elastic * .26f + state.tense * .18f) * unit
        val slices = 50
        for (i in 0..slices) {
            val x = left + (end - left) * i / slices
            // Absolute track coordinates prevent the wave from stretching or
            // changing its spatial grammar as progress advances.
            val trackX = (x - left) / playable
            val edge = min(1f, min((x-left)/(12f*unit), (end-x)/(12f*unit))).coerceAtLeast(0f)
            val slow = sin(trackX * 19f + phase * .73f)
            val secondary = sin(trackX * 37f - phase * .49f + state.drift)
            val irregular = sin(trackX * 63f + phase * 1.11f)
            val wave = slow * state.ethereal * .60f +
                secondary * state.elastic * .40f +
                irregular * state.tense * .33f
            val y = centerY + wave * amplitude * edge
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color.copy(alpha=.065f), style=Stroke(width=3.1f*unit, cap=StrokeCap.Round))
        drawPath(p, color.copy(alpha=.11f), style=Stroke(width=.70f*unit, cap=StrokeCap.Round))
    }
}
