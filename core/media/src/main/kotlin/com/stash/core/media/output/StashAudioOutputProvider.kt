package com.stash.core.media.output

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import com.stash.core.media.equalizer.dsp.FloatDspChain

/**
 * Wraps Media3's AudioTrack provider so every PCM stream goes through
 * [StashAudioOutput], opened in the format [HiResOutputController] planned.
 *
 * `DefaultAudioSink` keeps believing the AudioTrack takes the format *it*
 * produced (float, or 16-bit when a decoder ignores the float request): the
 * [OutputConfig] it holds is untouched, and only the config used to open the
 * real AudioTrack carries the planned encoding — with the buffer resized so it
 * holds the same number of frames. Offload, passthrough and tunneling are not
 * PCM and pass straight through.
 */
@OptIn(UnstableApi::class)
class StashAudioOutputProvider(
    delegate: AudioOutputProvider,
    private val controller: HiResOutputController,
    private val session: OutputSession,
    private val newDspChain: () -> FloatDspChain,
) : ForwardingAudioOutputProvider(delegate) {

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val inFormat = when (config.encoding) {
            C.ENCODING_PCM_FLOAT -> SampleFormat.FLOAT
            C.ENCODING_PCM_16BIT -> SampleFormat.INT16
            else -> null
        }
        val channels = Integer.bitCount(config.channelMask)
        if (inFormat == null || config.isOffload || config.isTunneling || channels <= 0) {
            return super.getAudioOutput(config)
        }
        val attributes = config.audioAttributes.platformAudioAttributes
        var plan = controller.prepare(config.sampleRate, channels, session.speed, attributes)
        val inner = try {
            super.getAudioOutput(configFor(config, inFormat, plan.trackFormat))
        } catch (e: AudioOutputProvider.InitializationException) {
            if (plan.trackFormat == SampleFormat.FLOAT) throw e
            // A device that lists a bit-perfect format but won't open an
            // AudioTrack in it: play, on the shared mixer, rather than not at all.
            Log.w(TAG, "AudioTrack in ${plan.trackFormat} refused; falling back to float", e)
            plan = OutputPlan(MixerDecision.Shared(MixerDecision.Reason.REJECTED), SampleFormat.FLOAT, plan.device, null)
            super.getAudioOutput(configFor(config, inFormat, SampleFormat.FLOAT))
        }
        return StashAudioOutput(
            inner = inner,
            inFormat = inFormat,
            outFormat = plan.trackFormat,
            channelCount = channels,
            streamSampleRate = config.sampleRate,
            plan = plan,
            dsp = newDspChain(),
            controller = controller,
            session = session,
            platformAttributes = attributes,
        )
    }

    private fun configFor(config: OutputConfig, inFormat: SampleFormat, trackFormat: SampleFormat): OutputConfig {
        if (trackFormat == inFormat) return config
        // Same number of samples (and so frames) in the new encoding.
        val samples = config.bufferSize / inFormat.bytesPerSample
        return config.buildUpon()
            .setEncoding(trackFormat.toMedia3Encoding())
            .setBufferSize(samples * trackFormat.bytesPerSample)
            .build()
    }

    private companion object {
        const val TAG = "StashAudioOutput"

        fun SampleFormat.toMedia3Encoding(): Int = when (this) {
            SampleFormat.INT16 -> C.ENCODING_PCM_16BIT
            SampleFormat.INT24 -> C.ENCODING_PCM_24BIT
            SampleFormat.INT32 -> C.ENCODING_PCM_32BIT
            SampleFormat.FLOAT -> C.ENCODING_PCM_FLOAT
        }
    }
}
