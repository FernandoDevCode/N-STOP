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
import androidx.compose.ui.graphics.Brush
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

/** Measured PCM tendencies: deliberately no artist/track/genre switches. */
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

/**
 * Music changes the physical HEIGHT of a continuous optical ridge.
 * 192 small cells retain the character of previously visited track positions;
 * their coordinates are absolute, not rescaled to the currently played segment.
 * No full audio/beat history, allocation or DSP on the playback thread.
 */
@Stable
class OrganicMotionState {
    private val relief = FloatArray(192)
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

    fun reliefAt(position: Float): Float {
        val scaled = position.coerceIn(0f, 1f) * (relief.size - 1)
        val index = scaled.toInt().coerceIn(0, relief.lastIndex)
        val next = min(index + 1, relief.lastIndex)
        val blend = scaled - index
        return relief[index] * (1f - blend) + relief[next] * blend
    }

    fun step(milliseconds: Float, input: MotionInfluence, progress: Float = .5f) {
        val dt = if (milliseconds.isFinite()) milliseconds.coerceIn(0f, 48f) / 1000f else 0f
        if (dt <= 0f) return
        val target = if (input.valid) input else MotionInfluence.CALM
        fun follow(current: Float, wanted: Float, seconds: Float): Float =
            current + (wanted.coerceIn(0f, 1f) - current) * (1f - exp(-dt / seconds))
        ethereal = follow(ethereal, target.ethereal, 2.8f)
        elastic = follow(elastic, target.elastic, 1.9f)
        tense = follow(tense, target.tense, 1.45f)
        agitation = follow(agitation,
            if (input.valid) (input.attack * .58f + input.evolution * .42f) else 0f, .36f)
        phase += dt * (.47f + elastic * .24f + tense * .31f)
        drift = sin(phase * .43f + sin(phase * .17f) * .9f) * .58f

        // Store measured novelty near the current position. This gives the
        // already-played ridge its own contours, rather than a glow that merely
        // stretches as the playhead moves. Avoid creating marks without PCM.
        if (input.valid && progress.isFinite()) {
            val index = (progress.coerceIn(0f, 1f) * relief.lastIndex).toInt()
            val height = (input.attack.coerceIn(0f, 1f) * .75f +
                input.evolution.coerceIn(0f, 1f) * .35f).coerceIn(0f, 1f)
            val retention = 1f - exp(-dt / .24f)
            for (j in max(0, index - 1)..min(relief.lastIndex, index + 1)) {
                val influence = if (j == index) 1f else .40f
                relief[j] += (height * influence - relief[j]) * retention
            }
        }
        revision++
    }
}

/**
 * A continuous raised filament only from the start to the current playhead.
 * Top & bottom contours form an actual embossed section (not a wide blur).
 * End tapers INSIDE the native thumb; nothing is drawn in front of it.
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
    // A fresh state is associated with each track-specific source.
    val latestProgress = androidx.compose.runtime.rememberUpdatedState(progress)
    LaunchedEffect(source, playing, enabled) {
        if (!playing || !enabled) return@LaunchedEffect
        var previous = 0L
        while (coroutineContext.isActive) withFrameNanos { frame ->
            if (previous != 0L) {
                state.step((frame - previous) / 1_000_000f,
                    source.read(), latestProgress.value)
            }
            previous = frame
        }
    }

    Canvas(modifier) {
        state.revision // Draw-phase observation only; avoids recomposition per frame.
        if (!enabled) return@Canvas
        val unit = 1.dp.toPx()
        val x0 = 17f * unit
        val span = (size.width - 34f * unit).coerceAtLeast(1f)
        val fraction = progress.coerceIn(0f, 1f)
        val tip = x0 + fraction * span
        if (tip - x0 < 2f * unit) return@Canvas
        val y = size.height * .5f
        val color = Color(localColor)
        // The ridge always uses the same density, even at 95% progress.
        // Sampling interval remains bounded and is independent of frame count.
        val segments = max(2, min(192, ((tip - x0) / (2.5f * unit)).toInt()))
        val upper = Path()
        val lower = Path()
        val fill = Path()
        val phase = state.phase
        for (i in 0..segments) {
            val x = x0 + (tip - x0) * i / segments
            val t = ((x - x0) / span).coerceIn(0f, 1f)
            val wave = sin(t * 42f + phase * .40f) * .41f +
                sin(t * 91f - phase * .63f + state.drift) * .20f
            val local = state.reliefAt(t)
            val envelope = (1.15f + local * 2.2f + state.agitation * .27f +
                state.elastic * .27f + state.tense * .25f).coerceIn(.8f, 4.25f)
            val leading = ((tip - x) / (4.0f * unit)).coerceIn(0f, 1f)
            val opening = ((x - x0) / (3.0f * unit)).coerceIn(0f, 1f)
            val edge = min(leading, opening)
            // Two related but non-identical contours resemble a raised optical
            // fiber, not an equalizer, fire, particles or a tracking appendage.
            val lift = (envelope + wave * (.55f + local * .60f)) * unit * edge
            val drop = (envelope * .72f - wave * .38f) * unit * edge
            val top = y - lift
            val bottom = y + drop
            if (i == 0) {
                upper.moveTo(x, top)
                lower.moveTo(x, bottom)
                fill.moveTo(x, top)
            } else {
                upper.lineTo(x, top)
                lower.lineTo(x, bottom)
                fill.lineTo(x, top)
            }
        }
        for (i in segments downTo 0) {
            val x = x0 + (tip - x0) * i / segments
            val t = ((x - x0) / span).coerceIn(0f, 1f)
            val wave = sin(t * 42f + phase * .40f) * .41f +
                sin(t * 91f - phase * .63f + state.drift) * .20f
            val local = state.reliefAt(t)
            val envelope = (1.15f + local * 2.2f + state.agitation * .27f +
                state.elastic * .27f + state.tense * .25f).coerceIn(.8f, 4.25f)
            val edge = min(((tip - x) / (4f * unit)).coerceIn(0f, 1f),
                ((x - x0) / (3f * unit)).coerceIn(0f, 1f))
            fill.lineTo(x, y + (envelope * .72f - wave * .38f) * unit * edge)
        }
        fill.close()

        // Fine shadow + translucent core + specular top edge create the
        // characteristic U-shaped relief from the approved references.
        drawPath(fill, Brush.verticalGradient(
            0f to color.copy(alpha=.06f),
            .45f to color.copy(alpha=.24f),
            .72f to color.copy(alpha=.13f),
            1f to color.copy(alpha=.025f),
            startY=y-5f*unit, endY=y+5f*unit))
        drawPath(lower, color.copy(alpha=.13f),
            style=Stroke(width=2.5f*unit, cap=StrokeCap.Round))
        drawPath(upper, color.copy(alpha=.30f),
            style=Stroke(width=1.45f*unit, cap=StrokeCap.Round))
        drawPath(upper, Color.White.copy(alpha=.23f),
            style=Stroke(width=.47f*unit, cap=StrokeCap.Round))
        drawPath(lower, color.copy(alpha=.46f),
            style=Stroke(width=.66f*unit, cap=StrokeCap.Round))
    }
}
