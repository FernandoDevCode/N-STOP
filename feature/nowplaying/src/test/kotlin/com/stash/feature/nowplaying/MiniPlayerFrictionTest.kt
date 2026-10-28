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

    @Test fun cornerEmissionRotatesWithPathTangent() {
        // Same random sequence: horizontal velocity at the straight becomes vertical
        // on the left curve, while the lift turns inward from the side.
        val flat = MiniFrictionParticles(42)
        val curve = MiniFrictionParticles(42)
        flat.step(.04f, contact, 1f, true, false, false, 0f)
        curve.step(.04f, contact, 1f, true, false, false, 0f, Offset(0f, 1f))
        for (i in flat.life.indices.filter { flat.life[it] > 0 }) {
            assertEquals(flat.vx[i], curve.vy[i], .00001f)
            assertEquals(-flat.vy[i], curve.vx[i], .00001f)
            assertEquals(contact.x, curve.x[i], 0f)
            assertEquals(contact.y, curve.y[i], 0f)
        }
    }

    @Test fun fragmentsOccupyBothSidesAndMostlyTrailTheContact() {
        val p = simulate()
        val live = p.life.indices.filter { p.life[it] > 0 }
        assertTrue(live.any { p.y[it] < contact.y - 1f })
        assertTrue(live.any { p.y[it] > contact.y + 1f })
        assertTrue(live.count { p.x[it] < contact.x } > live.count { p.x[it] > contact.x })
        assertTrue(live.map { p.trail[it] }.distinct().size > 3)
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
