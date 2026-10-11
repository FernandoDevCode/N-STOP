package com.stash.core.media.output

import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import com.stash.core.media.equalizer.dsp.FloatDspChain
import com.stash.core.media.equalizer.dsp.GainRamp
import com.stash.core.media.output.HiResOutputController.Companion.toOutputDevice
import java.nio.ByteBuffer

/**
 * Stash's own output stage: the last thing the audio passes through before
 * Android's `AudioTrack`.
 *
 * Media3's `DefaultAudioSink` hands us the decoded audio — float for almost
 * everything, since float output is on — and this stage:
 *
 * 1. runs Stash's EQ / bass / loudness / limiter on it in float
 *    ([FloatDspChain]), only while a stage is actually doing something;
 * 2. converts to whatever the AudioTrack was opened with: float for the
 *    shared or rate-matched mixer, the DAC's exact integer format for
 *    bit-perfect ([ConvertingWriter] — exact for ≤24-bit sources, TPDF
 *    dither only where a sample genuinely doesn't fit);
 * 3. in bit-perfect mode, applies the player's volume itself (Android applies
 *    none there), so a duck or a sleep-timer fade still works and unity volume
 *    stays bit-perfect.
 *
 * With nothing to do (the default: EQ off, normalization unmeasured, float in
 * and out) the buffer goes straight to the AudioTrack, untouched and uncopied.
 *
 * Frame accounting is exact (see [ConvertingWriter]); no stage adds latency,
 * so position, gapless trimming and end-of-stream behave as without us.
 */
@OptIn(UnstableApi::class)
class StashAudioOutput(
    private val inner: AudioOutput,
    private val inFormat: SampleFormat,
    private val outFormat: SampleFormat,
    val channelCount: Int,
    val sampleRate: Int,
    plan: OutputPlan,
    private val dsp: FloatDspChain,
    private val controller: HiResOutputController,
    val session: OutputSession,
    val platformAttributes: android.media.AudioAttributes,
) : ForwardingAudioOutput(inner) {

    /** The plan this stream was built for; degraded to shared if Android rejects the mixer request. */
    @Volatile var plan: OutputPlan = plan
        private set

    private val writer = ConvertingWriter(inFormat, outFormat, channelCount)
    private val inFrameSize = inFormat.frameSize(channelCount)
    private val volume = GainRamp().apply { configure(sampleRate) }
    private var playerVolume = 1f
    private var framesWritten = 0L

    @Volatile private var replanRequested = false

    private val audioTrack: AudioTrack? = (inner as? AudioTrackAudioOutput)?.audioTrack
    private val routingListener = AudioRouting.OnRoutingChangedListener { controller.onRouted(this) }

    init {
        dsp.configure(sampleRate, channelCount)
        controller.register(this)
        Looper.myLooper()?.let { audioTrack?.addOnRoutingChangedListener(routingListener, Handler(it)) }
    }

    private val bitPerfect: Boolean get() = plan.bitPerfect

    /**
     * Asks for this stream to be rebuilt with a fresh plan (route or mode
     * changed under it). Honoured on the next write by failing it as
     * "recoverable": ExoPlayer then recreates the audio output in place
     * (a seek to the current position — the same path it uses when Android
     * kills an AudioTrack), and [StashAudioOutputProvider] plans the new one.
     */
    fun requestReplan() {
        replanRequested = true
    }

    /** Android refused the mixer preference: carry on, honestly, on the shared mixer. */
    internal fun onMixerRejected() {
        plan = OutputPlan(MixerDecision.Shared(MixerDecision.Reason.REJECTED), plan.trackFormat, plan.device, null)
        inner.setVolume(playerVolume)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        // DefaultAudioSink only treats the error as recoverable once audio has
        // been written; before that it would be fatal, so wait one buffer.
        if (replanRequested && framesWritten > 0) {
            replanRequested = false
            throw AudioOutput.WriteException(AudioTrack.ERROR_DEAD_OBJECT, /* isRecoverable= */ true)
        }
        val dspOn = !bitPerfect && dsp.isActive()
        val softVolume = bitPerfect && (playerVolume != 1f || volume.isActive)
        val before = buffer.position()
        val handled = if (!writer.hasPending && !dspOn && !softVolume && inFormat == outFormat) {
            inner.write(buffer, encodedAccessUnitCount, presentationTimeUs)
        } else {
            writer.write(
                buffer,
                process = { samples, frames ->
                    if (dspOn) dsp.process(samples, frames)
                    if (softVolume) volume.apply(samples, frames, channelCount)
                },
                sink = { out -> inner.write(out, 1, presentationTimeUs) },
            )
        }
        framesWritten += (buffer.position() - before) / inFrameSize
        return handled
    }

    override fun play() {
        controller.activate(this)
        super.play()
    }

    override fun flush() {
        writer.flush()
        dsp.reset()
        volume.snapTo(playerVolume)
        super.flush()
    }

    override fun setVolume(volume: Float) {
        playerVolume = volume
        if (bitPerfect) {
            // Android applies no volume to a bit-perfect stream: do it here,
            // ramped, and keep the track itself at unity.
            this.volume.setTarget(volume)
            inner.setVolume(1f)
        } else {
            inner.setVolume(volume)
        }
    }

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) {
        super.setPlaybackParameters(playbackParams)
        if (session.speed != playbackParams.speed) {
            session.speed = playbackParams.speed
            controller.onSpeedChanged(this)
        }
    }

    override fun release() {
        audioTrack?.removeOnRoutingChangedListener(routingListener)
        controller.unregister(this)
        super.release()
    }

    /** What this stream looks like right now, for the signal path. */
    internal fun pathState(): OutputPathState {
        val p = plan
        val decision = p.decision
        val routed = audioTrack?.routedDevice ?: p.device
        return OutputPathState(
            sampleRate = sampleRate,
            channelCount = channelCount,
            decodedFormat = inFormat,
            trackFormat = outFormat,
            mixer = p.mixerPath,
            mixerSampleRate = when (decision) {
                is MixerDecision.Configure -> decision.option.sampleRate
                is MixerDecision.Shared -> controller.sharedMixerSampleRate()
            },
            mixerFormat = (decision as? MixerDecision.Configure)?.option?.format,
            device = routed?.toOutputDevice(),
            mode = controller.mode,
            bitPerfectUnavailable = (decision as? MixerDecision.Configure)?.bitPerfectUnavailable == true,
            sharedReason = (decision as? MixerDecision.Shared)?.reason,
        )
    }
}
