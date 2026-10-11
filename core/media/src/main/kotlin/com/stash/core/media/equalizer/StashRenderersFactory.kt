// StashRenderersFactory.kt
package com.stash.core.media.equalizer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.AudioTrackBufferSizeProvider
import com.stash.core.media.equalizer.dsp.FloatDspChain
import com.stash.core.media.output.HiResOutputController
import com.stash.core.media.output.OutputSession
import com.stash.core.media.output.StashAudioOutputProvider

/**
 * RenderersFactory that builds Stash's audio sink: Media3's [DefaultAudioSink]
 * in float, feeding Stash's own output stage
 * ([com.stash.core.media.output.StashAudioOutput]).
 *
 * Why not Media3 AudioProcessors any more: `DefaultAudioSink` only runs
 * user-supplied processors on 16-bit audio — with float output on it routes
 * high-resolution PCM down a branch that skips them. So keeping the EQ meant
 * keeping float off, and every 24-bit FLAC was truncated to 16 bits before
 * the EQ (or the DAC) ever saw it. Now:
 *
 * - **Float output on.** Decoders deliver float (Media3 asks MediaCodec for it
 *   once the sink takes float directly), 24-bit content keeps all 24 bits,
 *   and the AudioTrack is opened at the song's own sample rate.
 * - **The EQ, bass, loudness and limiter run in the output stage**, in float,
 *   only while one of them is actually doing something ([FloatDspChain]).
 *   Off, the samples pass through untouched.
 * - **Speed goes to the AudioTrack** (`setEnableAudioOutputPlaybackParameters`):
 *   Media3's Sonic time-stretcher lives in the processor chain the float
 *   branch skips. AudioFlinger's time-stretch is the same algorithm, applied
 *   instantly (good for Listen Together's drift correction too).
 * - **Hi-res USB**: the output stage asks [HiResOutputController] how to open
 *   each stream — rate-matched or bit-perfect on a USB DAC (Android 14+).
 *
 * One factory per player; [outputSession] carries that player's source format
 * and speed to its output stage.
 */
@OptIn(UnstableApi::class)
class StashRenderersFactory(
    context: Context,
    private val eqController: EqController,
    private val loudnessController: LoudnessController,
    private val hiResOutputController: HiResOutputController,
) : DefaultRenderersFactory(context) {

    /** This factory's player's facts for its output stage (fed by the service's analytics listener). */
    val outputSession = OutputSession()

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val audioTrackProvider = AudioTrackAudioOutputProvider.Builder(context)
            .setAudioTrackBufferSizeProvider(SpeedCappedBufferSizeProvider)
            .build()
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioOutputPlaybackParameters(true)
            .setAudioOutputProvider(
                StashAudioOutputProvider(audioTrackProvider, hiResOutputController, outputSession) {
                    FloatDspChain({ eqController.state.value }) {
                        // This player's own song gain (see OutputSession.trackGainDb).
                        val shared = loudnessController.state.value
                        val own = outputSession.trackGainDb
                        if (own == null || own == shared.currentTrackGainDb) shared
                        else shared.copy(currentTrackGainDb = own)
                    }
                },
            )
            .build()
    }

    /**
     * Media3 sizes an AudioTrack that does its own time-stretching for 8x
     * speed: 8x the usual buffer, ~2 s of audio. The DSP runs *before* that
     * buffer, so an EQ slider would take seconds to be heard. Stash's speeds
     * are 0.1–4x and almost always ≤ 2x; sizing for 2x keeps EQ changes and
     * loudness switches within ~0.5 s while leaving AudioFlinger enough frames
     * to time-stretch.
     */
    private object SpeedCappedBufferSizeProvider : AudioTrackBufferSizeProvider {
        private const val MAX_SIZED_SPEED = 2.0

        override fun getBufferSizeInBytes(
            minBufferSizeInBytes: Int,
            encoding: Int,
            outputMode: Int,
            pcmFrameSize: Int,
            sampleRate: Int,
            bitrate: Int,
            maxAudioTrackPlaybackSpeed: Double,
        ): Int = AudioTrackBufferSizeProvider.DEFAULT.getBufferSizeInBytes(
            minBufferSizeInBytes,
            encoding,
            outputMode,
            pcmFrameSize,
            sampleRate,
            bitrate,
            minOf(maxAudioTrackPlaybackSpeed, MAX_SIZED_SPEED),
        )
    }
}
