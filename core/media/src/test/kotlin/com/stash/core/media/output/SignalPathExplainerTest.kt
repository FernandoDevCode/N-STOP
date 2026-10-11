package com.stash.core.media.output

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.prefs.HiResOutputMode
import org.junit.Test

class SignalPathExplainerTest {

    private val flac2496 = SourceFormat("FLAC", lossless = true, sampleRate = 96_000, bitsPerSample = 24, channelCount = 2)
    private val flac1644 = SourceFormat("FLAC", lossless = true, sampleRate = 44_100, bitsPerSample = 16, channelCount = 2)
    private val usb = OutputDevice(OutputDeviceKind.USB, "FiiO KA3")

    private fun out(
        rate: Int = 96_000,
        mixer: MixerPath = MixerPath.SHARED,
        mixerRate: Int? = 48_000,
        track: SampleFormat = SampleFormat.FLOAT,
        device: OutputDevice? = usb,
        mode: HiResOutputMode = HiResOutputMode.STANDARD,
        reason: MixerDecision.Reason? = MixerDecision.Reason.MODE_STANDARD,
    ) = OutputPathState(
        sampleRate = rate, channelCount = 2, decodedFormat = SampleFormat.FLOAT, trackFormat = track,
        mixer = mixer, mixerSampleRate = mixerRate, mixerFormat = null, device = device, mode = mode,
        sharedReason = reason,
    )

    @Test fun `no output yet means nothing to explain`() {
        assertThat(SignalPathExplainer.explain(flac2496, null, false)).isNull()
    }

    @Test fun `shared mixer at a different rate is called out as resampling, with the fix`() {
        val v = SignalPathExplainer.explain(flac2496, out(), dspActive = false)!!
        assertThat(v.quality).isEqualTo(SignalQuality.RESAMPLED)
        assertThat(v.headline).isEqualTo("Resampled to 48 kHz by Android")
        val android = v.stages.first { it.title == "Android" }
        assertThat(android.ok).isFalse()
        assertThat(android.detail).contains("turn on Hi-Res USB output")
    }

    @Test fun `matched USB mixer with a hi-res source is hi-res, not resampled`() {
        val v = SignalPathExplainer.explain(
            flac2496, out(mixer = MixerPath.MATCHED, mixerRate = 96_000, mode = HiResOutputMode.MATCH_RATE, reason = null), false,
        )!!
        assertThat(v.quality).isEqualTo(SignalQuality.LOSSLESS)
        assertThat(v.headline).isEqualTo("Hi-Res, not resampled")
        assertThat(v.stages.all { it.ok }).isTrue()
    }

    @Test fun `bit-perfect to a USB DAC is bit-perfect, and says the EQ is bypassed`() {
        val v = SignalPathExplainer.explain(
            flac2496,
            out(mixer = MixerPath.BIT_PERFECT, mixerRate = 96_000, track = SampleFormat.INT24, mode = HiResOutputMode.BIT_PERFECT, reason = null),
            dspActive = true, // EQ on, but bit-perfect bypasses it
        )!!
        assertThat(v.quality).isEqualTo(SignalQuality.BIT_PERFECT)
        assertThat(v.stages.first { it.title == "Stash" }.detail).contains("bypassed")
    }

    @Test fun `bit-perfect into a 16-bit-only DAC with a 24-bit source is not claimed bit-perfect`() {
        val v = SignalPathExplainer.explain(
            flac2496,
            out(mixer = MixerPath.BIT_PERFECT, mixerRate = 96_000, track = SampleFormat.INT16, mode = HiResOutputMode.BIT_PERFECT, reason = null),
            false,
        )!!
        assertThat(v.quality).isNotEqualTo(SignalQuality.BIT_PERFECT)
    }

    @Test fun `CD-quality on the speaker at 44_1 kHz native is lossless`() {
        val v = SignalPathExplainer.explain(
            flac1644,
            out(rate = 44_100, mixerRate = 44_100, device = OutputDevice(OutputDeviceKind.SPEAKER, "Phone speaker")),
            false,
        )!!
        assertThat(v.quality).isEqualTo(SignalQuality.LOSSLESS)
        assertThat(v.headline).isEqualTo("Lossless, not resampled")
    }

    @Test fun `active EQ is shown as processing, not hidden`() {
        val v = SignalPathExplainer.explain(flac1644, out(rate = 44_100, mixerRate = 44_100), dspActive = true)!!
        assertThat(v.quality).isEqualTo(SignalQuality.ENHANCED)
        assertThat(v.stages.first { it.title == "Stash" }.ok).isFalse()
    }

    @Test fun `lossy source wins over everything`() {
        val opus = SourceFormat("Opus", lossless = false, sampleRate = 48_000, bitsPerSample = null, channelCount = 2)
        val v = SignalPathExplainer.explain(opus, out(rate = 48_000), false)!!
        assertThat(v.quality).isEqualTo(SignalQuality.LOSSY)
    }

    @Test fun `bluetooth is flagged as re-encoded`() {
        val bt = OutputDevice(OutputDeviceKind.BLUETOOTH, "WH-1000XM5")
        val v = SignalPathExplainer.explain(flac1644, out(rate = 44_100, mixerRate = 44_100, device = bt), false)!!
        val dev = v.stages.last()
        assertThat(dev.ok).isFalse()
        assertThat(dev.detail).contains("re-encoded")
    }

    @Test fun `khz formatting`() {
        assertThat(SignalPathExplainer.khz(44_100)).isEqualTo("44.1 kHz")
        assertThat(SignalPathExplainer.khz(96_000)).isEqualTo("96 kHz")
        assertThat(SignalPathExplainer.khz(352_800)).isEqualTo("352.8 kHz")
    }
}
