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

    private fun observedReach(touching: Boolean = false, velocity: Float = 0f): List<Float> {
        val particles = MiniFrictionParticles(42)
        return buildList {
            repeat(120) {
                particles.step(1 / 60f, contact, 1f, true, false, touching, velocity)
                for (i in particles.life.indices) if (particles.life[i] > 0f) {
                    add((Offset(particles.x[i], particles.y[i]) - contact).getDistance())
                }
            }
        }
    }

    @Test fun cornerEmissionRotatesWithPathTangent() {
        // Same random sequence: tangential velocity still rotates exactly. The
        // outward normal is deliberately compressed at the side; inward chips rotate fully.
        val flat = MiniFrictionParticles(42)
        val curve = MiniFrictionParticles(42)
        flat.step(.04f, contact, 1f, true, false, false, 0f)
        curve.step(.04f, contact, 1f, true, false, false, 0f, Offset(0f, 1f))
        for (i in flat.life.indices.filter { flat.life[it] > 0 }) {
            assertEquals(flat.vx[i], curve.vy[i], .00001f)
            val expectedNormal = -flat.vy[i] * if (flat.vy[i] > 0f) .28f else 1f
            assertEquals(expectedNormal, curve.vx[i], .00001f)
            assertEquals(contact.x, curve.x[i], 0f)
            assertEquals(contact.y, curve.y[i], 0f)
        }
    }

    @Test fun normalPlaybackIsCompactWithOnlyOccasionalLongFragments() {
        val reach = observedReach()
        val compact = reach.count { it <= 18f }.toFloat() / reach.size
        assertTrue("At least 85% of particle observations should stay within 18dp; was $compact", compact >= .85f)
        assertTrue("The effect should keep a few natural fragments beyond the compact field", reach.any { it > 18f })
        assertTrue("Normal playback must not produce disconnected fragments", reach.max() < 42f)
    }

    @Test fun fastDragExpandsTheCompactFieldWithoutChangingItsAnchor() {
        val normal = observedReach().sorted()
        val fast = observedReach(touching = true, velocity = 2f).sorted()
        val normal90 = normal[(normal.lastIndex * .9f).toInt()]
        val fast90 = fast[(fast.lastIndex * .9f).toInt()]
        assertTrue("Fast drag should visibly expand the emission field", fast90 > normal90 * 1.5f)
        assertTrue("Every fragment must still originate at the contact", simulate(touching = true, velocity = 2f).let { p ->
            p.life.indices.any { p.life[it] > 0f && p.x[it] == contact.x && p.y[it] == contact.y }
        })
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
