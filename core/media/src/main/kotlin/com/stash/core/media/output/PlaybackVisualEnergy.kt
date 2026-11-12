package com.stash.core.media.output

import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Read-only, bounded PCM sampling for Now Playing visuals.
 *
 * Never changes buffer position/limit or DSP output, never requests microphone
 * access, and is entirely disabled if no visible player subscribed.
 * Energy represents decoded programme audio, not a fabricated beat clock.
 */
object PlaybackVisualEnergy {
    private val subscribers = AtomicInteger(0)
    @Volatile private var latest: Float = 0f
    @Volatile private var sampledAt: Long = 0L
    @Volatile private var nextSampleAt: Long = 0L

    fun subscribe() { subscribers.incrementAndGet() }

    fun unsubscribe() {
        if (subscribers.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) {
            latest = 0f
            sampledAt = 0L
        }
    }

    /** Safe to read on the UI frame thread. Stale data returns silence. */
    fun level(): Float =
        if (subscribers.get() == 0 || SystemClock.elapsedRealtime() - sampledAt > 180L) 0f
        else latest

    fun sample(buffer: ByteBuffer, format: SampleFormat, channels: Int) {
        if (subscribers.get() == 0 || channels < 1) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextSampleAt) return
        nextSampleAt = now + 32L // <= 32 small measurements per second
        val bytesPerSample = when (format) {
            SampleFormat.FLOAT, SampleFormat.INT32 -> 4
            SampleFormat.INT24 -> 3
            SampleFormat.INT16 -> 2
        }
        val frameBytes = bytesPerSample * channels
        val frames = buffer.remaining() / frameBytes
        if (frames < 1) return
        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        val start = view.position()
        val count = minOf(frames, 96)
        var power = 0.0
        for (i in 0 until count) {
            val frame = (i.toLong() * frames / count).toInt()
            val p = start + frame * frameBytes
            val raw = when (format) {
                SampleFormat.FLOAT -> view.getFloat(p)
                SampleFormat.INT16 -> view.getShort(p) / 32768f
                SampleFormat.INT24 -> {
                    val v = (view.get(p).toInt() and 255) or
                        ((view.get(p + 1).toInt() and 255) shl 8) or
                        (view.get(p + 2).toInt() shl 16)
                    v / 8388608f
                }
                SampleFormat.INT32 -> (view.getInt(p).toDouble() / 2147483648.0).toFloat()
            }
            val x = if (raw.isFinite()) abs(raw).coerceAtMost(1.0f) else 0f
            power += x.toDouble() * x
        }
        val rms = sqrt(power / count).toFloat()
        latest = (sqrt(rms) * 2.1f).coerceIn(0f, 1f)
        sampledAt = now
    }
}
