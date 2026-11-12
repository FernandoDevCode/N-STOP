package com.stash.core.media.output

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Test

/** Real DSP descriptors; these tests must not depend on particular artists. */
class MusicFeatureAnalyzerTest {
    private fun tone(amplitude: Float, frequency: Float = 440f): FloatArray =
        FloatArray(384) { i -> (sin(2.0 * PI * frequency * i / 48000) * amplitude).toFloat() }

    @Test fun `profiles stay finite bounded and normalized with silent input`() {
        val analyzer = MusicFeatureAnalyzer()
        repeat(60) { analyzer.analyze(FloatArray(384), 48000) }
        val x = analyzer.analyze(FloatArray(384), 48000)
        assertThat(x.valid).isTrue()
        assertThat(x.flow).isAtLeast(0f)
        assertThat(x.elasticity).isAtLeast(0f)
        assertThat(x.tension).isAtLeast(0f)
        assertThat(x.flow + x.elasticity + x.tension).isWithin(.001f).of(1f)
        assertThat(x.attack).isAtLeast(0f)
        assertThat(x.attack).isAtMost(1f)
    }

    @Test fun `real transient changes attack but no permanent genre is inferred`() {
        val analyzer = MusicFeatureAnalyzer()
        repeat(28) { analyzer.analyze(tone(.02f),48000) }
        val hit = analyzer.analyze(tone(.9f,1100f),48000)
        assertThat(hit.attack).isGreaterThan(0f)
        assertThat(hit.valid).isTrue()
        val after = analyzer.analyze(tone(.02f),48000)
        assertThat(after.analyzedWindows).isGreaterThan(hit.analyzedWindows)
    }

    @Test fun `reset and short buffers return safe behavior`() {
        val analyzer = MusicFeatureAnalyzer()
        repeat(25) { analyzer.analyze(tone(.4f),48000) }
        analyzer.reset()
        val fresh = analyzer.analyze(tone(.3f),48000)
        assertThat(fresh.valid).isFalse()
        assertThat(fresh.analyzedWindows).isEqualTo(1)
        assertThat(analyzer.analyze(FloatArray(8),48000).valid).isFalse()
    }
}
