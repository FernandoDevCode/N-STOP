package com.stash.core.media.output

import com.stash.core.data.prefs.HiResOutputMode

/**
 * One entry of `AudioManager.getSupportedMixerAttributes(device)`, stripped of
 * platform types so the choice below is testable on the JVM.
 *
 * @property format null when the platform encoding is one we can't produce.
 * @property index position in the platform list, to map the choice back.
 */
data class MixerOption(
    val format: SampleFormat?,
    val sampleRate: Int,
    val channelCount: Int,
    val bitPerfect: Boolean,
    val index: Int,
)

/** What the output stage should do with the USB mixer for one stream. */
sealed interface MixerDecision {
    /** Leave the mixer alone; [reason] is shown on the signal path when it matters. */
    data class Shared(val reason: Reason) : MixerDecision

    /**
     * Configure the mixer as [option] and write [trackFormat] to the AudioTrack.
     * [bitPerfectUnavailable] marks a bit-perfect request served as rate-match.
     */
    data class Configure(
        val option: MixerOption,
        val trackFormat: SampleFormat,
        val bitPerfectUnavailable: Boolean = false,
    ) : MixerDecision {
        val bitPerfect: Boolean get() = option.bitPerfect
    }

    enum class Reason {
        MODE_STANDARD,
        UNSUPPORTED_ANDROID,
        NOT_USB,
        RATE_UNSUPPORTED_BY_DAC,
        NOT_MEDIA_USAGE,

        /** Android (or the AudioTrack) refused the configuration it advertised. */
        REJECTED,
    }
}

object MixerSelection {

    /**
     * Picks the mixer configuration for a [sampleRate] × [channelCount] stream.
     *
     * - The sample rate must match exactly — the whole point. A DAC without
     *   88.2 kHz gets no override for an 88.2 kHz song, and Android resamples it
     *   as before (we never pick a "nearby" rate: that is resampling too).
     * - Bit-perfect also needs an exact channel count (the track's format must
     *   equal the mixer's) and normal speed (time-stretching alters samples).
     *   When it isn't available, fall back to a rate-matched shared mixer.
     * - Among equals, the widest format wins: 32-bit > 24-bit > float > 16-bit.
     *   For ≤24-bit sources all but 16-bit carry every bit; wider leaves more
     *   room for the mixer's own math in the shared case.
     */
    fun choose(
        options: List<MixerOption>,
        mode: HiResOutputMode,
        sampleRate: Int,
        channelCount: Int,
        speed: Float,
    ): MixerDecision {
        if (mode == HiResOutputMode.STANDARD) return MixerDecision.Shared(MixerDecision.Reason.MODE_STANDARD)
        val atRate = options.filter { it.sampleRate == sampleRate && it.format != null }
        if (atRate.isEmpty()) return MixerDecision.Shared(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)

        val wantBitPerfect = mode == HiResOutputMode.BIT_PERFECT
        if (wantBitPerfect && speed == 1f) {
            val bp = atRate
                .filter { it.bitPerfect && it.channelCount == channelCount }
                .maxByOrNull { rank(it.format!!) }
            if (bp != null) return MixerDecision.Configure(bp, trackFormat = bp.format!!)
        }

        val shared = atRate
            .filter { !it.bitPerfect }
            .sortedWith(
                compareByDescending<MixerOption> { it.channelCount == channelCount }
                    .thenByDescending { rank(it.format!!) },
            )
            .firstOrNull()
            ?: return MixerDecision.Shared(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)
        // Shared mixer: hand it float — it mixes in float and converts to the
        // DAC format itself, so nothing is lost before that last step.
        return MixerDecision.Configure(shared, trackFormat = SampleFormat.FLOAT, bitPerfectUnavailable = wantBitPerfect)
    }

    private fun rank(f: SampleFormat): Int = when (f) {
        SampleFormat.INT32 -> 4
        SampleFormat.INT24 -> 3
        SampleFormat.FLOAT -> 2
        SampleFormat.INT16 -> 1
    }
}
