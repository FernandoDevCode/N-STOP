package com.stash.core.media.output

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * [OutputPlan.key] describes what the hardware does, nothing more: a change
 * of reason alone (STANDARD → MATCH while on the speaker or Bluetooth, or the
 * stored mode loading after a cold start) must not rebuild the stream.
 */
class OutputPlanKeyTest {

    private fun device(id: Int): AudioDeviceInfo = mockk { every { this@mockk.id } returns id }

    private fun shared(reason: MixerDecision.Reason, format: SampleFormat = SampleFormat.FLOAT, id: Int = 1) =
        OutputPlan(MixerDecision.Shared(reason), format, device(id), null)

    private fun configured(): OutputPlan {
        val option = MixerOption(SampleFormat.INT24, 96_000, 2, bitPerfect = true, index = 2)
        return OutputPlan(MixerDecision.Configure(option, SampleFormat.INT24), SampleFormat.INT24, device(7), Any())
    }

    @Test fun `shared plans with the same track format share a key whatever the reason`() {
        val keys = MixerDecision.Reason.entries.map { shared(it).key }.toSet()
        assertThat(keys).hasSize(1)
    }

    @Test fun `switching STANDARD to MATCH off USB does not rebuild`() {
        val live = shared(MixerDecision.Reason.MODE_STANDARD)
        val fresh = shared(MixerDecision.Reason.NOT_USB)
        assertThat(live.needsRebuildFor(fresh)).isFalse()
    }

    @Test fun `moving between non-USB outputs does not rebuild`() {
        // Speaker → Bluetooth: Android reroutes the shared mixer itself.
        assertThat(shared(MixerDecision.Reason.NOT_USB, id = 1).needsRebuildFor(shared(MixerDecision.Reason.NOT_USB, id = 2)))
            .isFalse()
    }

    @Test fun `the reason stays on the plan for the signal path`() {
        val plan = shared(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)
        assertThat((plan.decision as MixerDecision.Shared).reason)
            .isEqualTo(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)
    }

    @Test fun `a different track format is a different set-up`() {
        assertThat(shared(MixerDecision.Reason.MODE_STANDARD).key)
            .isNotEqualTo(shared(MixerDecision.Reason.MODE_STANDARD, SampleFormat.INT24).key)
    }

    @Test fun `going to or from a configured USB mixer rebuilds`() {
        val configured = configured()
        val shared = shared(MixerDecision.Reason.NOT_USB)
        assertThat(shared.needsRebuildFor(configured)).isTrue()
        assertThat(configured.needsRebuildFor(shared)).isTrue()
    }

    @Test fun `a rejected stream follows once the refusal no longer stands`() {
        // Only a refusal that still stands keeps a stream in its non-float format.
        val live = configured().rejected()
        assertThat(live.needsRebuildFor(shared(MixerDecision.Reason.MODE_STANDARD))).isTrue()
    }
}
