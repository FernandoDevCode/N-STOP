package com.stash.core.media.output

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The visual meter must never consume or mutate the playback buffer. */
@RunWith(RobolectricTestRunner::class)
class PlaybackVisualEnergyTest {
    @Test fun `meter reads real PCM without advancing source buffer`() {
        val pcm = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder())
        repeat(256) { pcm.putFloat(if(it % 2 == 0) .6f else -.6f) }
        pcm.flip()
        val beforePosition = pcm.position()
        val beforeLimit = pcm.limit()
        val first = pcm.getFloat(0)
        PlaybackVisualEnergy.subscribe()
        try {
            PlaybackVisualEnergy.sample(pcm, SampleFormat.FLOAT, 2)
            assertThat(PlaybackVisualEnergy.level()).isGreaterThan(.25f)
            assertThat(pcm.position()).isEqualTo(beforePosition)
            assertThat(pcm.limit()).isEqualTo(beforeLimit)
            assertThat(pcm.getFloat(0)).isEqualTo(first)
        } finally { PlaybackVisualEnergy.unsubscribe() }
        assertThat(PlaybackVisualEnergy.level()).isEqualTo(0f)
    }

    @Test fun `meter stays idle without listeners`() {
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
            .putFloat(.8f).putFloat(.8f).apply { flip() }
        PlaybackVisualEnergy.sample(pcm, SampleFormat.FLOAT, 2)
        assertThat(PlaybackVisualEnergy.level()).isEqualTo(0f)
    }
}
