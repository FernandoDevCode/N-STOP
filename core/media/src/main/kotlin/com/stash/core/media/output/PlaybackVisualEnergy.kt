package com.stash.core.media.output

import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Non-invasive, bounded capture of the actual decoded PCM from StashAudioOutput.
 *
 * The audio callback does at most one 384-sample read every ~32 ms, never advances
 * the playback buffer, and does no FFT/analysis, persistence or UI work.
 * A single daemon worker consumes the latest packet (backpressure = one).
 * Silence or unavailable PCM never masquerades as a detected beat.
 */
object PlaybackVisualEnergy {
    private const val WINDOW = 384
    private const val INTERVAL_MS = 32L
    private val subscribers = AtomicInteger(0)
    private val queued = AtomicReference<Packet?>(null)
    private val draining = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val executor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "Sonora-Motion-Analysis").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }
    private val analyzer = MusicFeatureAnalyzer()
    @Volatile private var latestLevel = 0f
    @Volatile private var latestAt = 0L
    @Volatile private var nextSampleAt = 0L
    @Volatile private var latestMotion = MusicMotionFeatures.EMPTY

    private data class Packet(val mono: FloatArray, val sampleRate: Int, val epoch: Int, val at: Long)

    fun subscribe() { subscribers.incrementAndGet() }

    fun unsubscribe() {
        if (subscribers.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) reset()
    }

    /** Call on track identity changes; old packets cannot affect the next track. */
    fun reset() {
        generation.incrementAndGet()
        queued.set(null)
        synchronized(analyzer) { analyzer.reset() }
        latestLevel = 0f
        latestMotion = MusicMotionFeatures.EMPTY
        latestAt = 0L
        nextSampleAt = 0L
    }

    fun level(): Float =
        if (subscribers.get() == 0 || SystemClock.elapsedRealtime() - latestAt > 250L) 0f
        else latestLevel

    /** Immutable read; stale/suspended output returns an explicitly unavailable profile. */
    fun motion(): MusicMotionFeatures =
        if (subscribers.get() == 0 || SystemClock.elapsedRealtime() - latestAt > 400L)
            MusicMotionFeatures.EMPTY else latestMotion

    /** Restore prior analysis as a *prior*, never as a fake live audio event. */
    fun seed(profile: MusicMotionFeatures) {
        if (!profile.valid) return
        synchronized(analyzer) { analyzer.seed(profile) }
    }

    fun sample(buffer: ByteBuffer, format: SampleFormat, channels: Int, sampleRate: Int = 48000) {
        if (subscribers.get() == 0 || channels !in 1..8 || sampleRate !in 8000..384000) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextSampleAt) return
        nextSampleAt = now + INTERVAL_MS
        val frameSize = channels * format.bytesPerSample
        val frames = buffer.remaining() / frameSize
        if (frames <= 0) return

        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        val start = view.position()
        val count = min(WINDOW, frames)
        val mono = FloatArray(count)
        var power = 0.0
        // Read only one channel in each frame; other channels are left untouched.
        // This is not downmix DSP and cannot affect samples sent to AudioTrack.
        for (i in 0 until count) {
            val offset = start + i * frameSize
            val raw = when (format) {
                SampleFormat.FLOAT -> view.getFloat(offset)
                SampleFormat.INT16 -> view.getShort(offset) / 32768f
                SampleFormat.INT24 -> {
                    val x = (view.get(offset).toInt() and 255) or
                        ((view.get(offset + 1).toInt() and 255) shl 8) or
                        (view.get(offset + 2).toInt() shl 16)
                    x / 8388608f
                }
                SampleFormat.INT32 -> (view.getInt(offset).toDouble() / 2147483648.0).toFloat()
            }
            val v = if (raw.isFinite()) raw.coerceIn(-1f, 1f) else 0f
            mono[i] = v
            power += v.toDouble() * v
        }
        val rms = sqrt(power / count).toFloat()
        latestLevel = (sqrt(rms) * 2.1f).coerceIn(0f, 1f)
        latestAt = now

        queued.set(Packet(mono, sampleRate, generation.get(), now))
        if (draining.compareAndSet(false, true)) {
            executor.execute { drainPending() }
        }
    }

    private fun drainPending() {
        do {
            try {
                while (true) {
                    val packet = queued.getAndSet(null) ?: break
                    if (packet.epoch != generation.get() || subscribers.get() == 0) continue
                    val computed = synchronized(analyzer) {
                        analyzer.analyze(packet.mono, packet.sampleRate)
                    }
                    if (packet.epoch == generation.get()) latestMotion = computed
                }
            } finally {
                draining.set(false)
            }
            // A write racing with the last queue read must not become stranded.
        } while (queued.get() != null && draining.compareAndSet(false, true))
    }
}

/** Values are normalized, continuous descriptors; no genre labels or album exceptions. */
data class MusicMotionFeatures(
    val flow: Float,
    val elasticity: Float,
    val tension: Float,
    val regularity: Float,
    val attack: Float,
    val evolution: Float,
    val valid: Boolean,
    val analyzedWindows: Int,
) {
    companion object {
        val EMPTY = MusicMotionFeatures(.65f, .22f, .13f, 0f, 0f, 0f, false, 0)
    }
}

/**
 * Causal feature extractor, fed on the analysis worker.
 * Estimates dynamics, spectral novelty, rhythmic recurrence and long-horizon
 * changes. No genre/BPM claim. The short sample rate limits bass resolution.
 */
internal class MusicFeatureAnalyzer {
    private val attacks = FloatArray(96)
    private var cursor = 0
    private var windows = 0
    private var previousEnergy = 0f
    private var previousSpectrum = FloatArray(6)
    private var slowEnergy = 0f
    private var slowNovelty = 0f
    private var flow = .65f
    private var elastic = .22f
    private var tension = .13f

    fun reset() {
        attacks.fill(0f)
        cursor = 0
        windows = 0
        previousEnergy = 0f
        previousSpectrum.fill(0f)
        slowEnergy = 0f
        slowNovelty = 0f
        flow = .65f
        elastic = .22f
        tension = .13f
    }

    fun seed(p: MusicMotionFeatures) {
        flow = p.flow.coerceIn(0f, 1f)
        elastic = p.elasticity.coerceIn(0f, 1f)
        tension = p.tension.coerceIn(0f, 1f)
        slowNovelty = p.evolution.coerceIn(0f, 1f)
    }

    fun analyze(pcm: FloatArray, sampleRate: Int): MusicMotionFeatures {
        if (pcm.size < 16) return MusicMotionFeatures.EMPTY
        val n = pcm.size
        var sum = 0.0
        for (v in pcm) sum += v.toDouble() * v
        val rms = sqrt(sum / n).toFloat().coerceIn(0f, 1f)
        val energy = (sqrt(rms) * 1.9f).coerceIn(0f, 1f)
        // Six coarse log-spaced spectral probes, Hann window, no per-frame FFT.
        val frequencies = floatArrayOf(180f, 350f, 700f, 1400f, 2800f, 5600f)
        val bands = FloatArray(frequencies.size)
        for (k in frequencies.indices) {
            val frequency = min(frequencies[k], sampleRate * .43f)
            val omega = 2.0 * PI * frequency / sampleRate
            val coeff = 2.0 * cos(omega)
            var a = 0.0
            var b = 0.0
            for (j in pcm.indices) {
                val window = .5 - .5 * cos(2.0 * PI * j / (n - 1))
                val next = pcm[j] * window + coeff * a - b
                b = a
                a = next
            }
            val power = max(0.0, a * a + b * b - coeff * a * b)
            bands[k] = ln(1.0 + power).toFloat()
        }
        val peak = bands.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
        val normalized = FloatArray(bands.size) { bands[it] / peak }
        var flux = 0f
        for (i in normalized.indices) {
            flux += (normalized[i] - previousSpectrum[i]).coerceAtLeast(0f)
        }
        flux = (flux / normalized.size).coerceIn(0f, 1f)
        previousSpectrum = normalized
        val energyRise = (energy - previousEnergy).coerceAtLeast(0f)
        previousEnergy = energy
        val onset = (energyRise * 1.8f + flux * .7f).coerceIn(0f, 1f)

        attacks[cursor] = onset
        cursor = (cursor + 1) % attacks.size
        windows++
        // Recurrence across ~0.25–1.0 seconds. Stable repetition means high correlation.
        var strongest = 0f
        if (windows >= 24) {
            for (lag in 7..30) {
                var same = 0f
                var total = .0001f
                for (j in 0 until 48) {
                    val a = attacks[(cursor - 1 - j + 192) % 96]
                    val b = attacks[(cursor - 1 - j - lag + 192) % 96]
                    same += a * b
                    total += (a * a + b * b) * .5f
                }
                strongest = max(strongest, (same / total).coerceIn(0f, 1f))
            }
        }
        slowEnergy += .035f * (energy - slowEnergy)
        slowNovelty += .035f * (flux - slowNovelty)
        val evolution = abs(energy - slowEnergy).coerceIn(0f, 1f)
        val irregularity = (1f - strongest).coerceIn(0f, 1f)
        val sustained = (1f - onset).coerceIn(0f, 1f)
        val targetTension = (energy * .45f + onset * .28f + evolution * .20f +
            irregularity * .07f).coerceIn(0f, 1f)
        val targetElastic = (strongest * .48f + energy * .20f +
            sustained * .32f).coerceIn(0f, 1f)
        val targetFlow = (sustained * .54f + (1f - evolution) * .28f +
            (1f - targetTension) * .18f).coerceIn(0f, 1f)
        // Long-horizon damping; neither a single snare nor a hammer sets the style.
        flow += .045f * (targetFlow - flow)
        elastic += .045f * (targetElastic - elastic)
        tension += .045f * (targetTension - tension)
        val total = (flow + elastic + tension).coerceAtLeast(.001f)
        val enough = windows >= 20
        return MusicMotionFeatures(flow / total, elastic / total, tension / total,
            strongest, onset, evolution, enough, windows)
    }
}
