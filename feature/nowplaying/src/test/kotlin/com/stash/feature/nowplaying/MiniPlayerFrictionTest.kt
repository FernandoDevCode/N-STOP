package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class MiniPlayerFrictionTest {
    private val contact = Offset(100f, 23f)
    private fun simulate(playing: Boolean = true, buffering: Boolean = false,
                         touching: Boolean = false, velocity: Float = 0f): MiniFrictionParticles =
        MiniFrictionParticles(42).also { p ->
            repeat(60) { p.step(1 / 60f, contact, 1f, playing, buffering, touching, velocity) }
        }

    @Test fun continuousEmissionDoesNotDependOnProgressMoving() {
        val p = simulate()
        assertTrue(p.alive >= 8)
        // Newly emitted particles originate at exactly the rail contact.
        assertTrue(p.life.indices.any { p.life[it] > 0 && p.x[it] == contact.x && p.y[it] == contact.y })
    }

    @Test fun slowAndFastGesturesHaveDistinctEnergyAndParticleCounts() {
        val normal = simulate()
        val slow = simulate(touching = true, velocity = .05f)
        val fast = simulate(touching = true, velocity = 2f)
        assertTrue(slow.alive > normal.alive)
        assertTrue(fast.alive > slow.alive)
        assertTrue(fast.energy > slow.energy * 2)
        assertTrue(fast.alive <= MiniFrictionParticles.CAPACITY)
        assertEquals(-1f, simulate(touching = true, velocity = -2f).direction, 0f)
    }

    @Test fun pausedIsQuietBufferingReducedAndReleaseDissipates() {
        assertEquals(0, simulate(playing = false).alive)
        assertTrue(simulate(buffering = true).alive < simulate().alive)
        val p = simulate(touching = true, velocity = 2f)
        repeat(30) { p.step(1 / 60f, contact, 1f, false, false, false, 0f) }
        assertEquals(0, p.alive)
        assertTrue(p.energy < .01f)
        p.reset()
        assertEquals(0, p.alive)
    }
}
