package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class MiniPlayerRailTest {
    @Test fun requiredProgressSamplesReachSidesAndFollowCornerTangents() {
        val rail = MiniPlayerRail(360f, 24f, 13f)
        for (f in listOf(0f, .01f, .25f, .5f, .75f, .99f, 1f)) {
            assertEquals(f, rail.fraction(rail.point(f)), .00001f)
            assertEquals(1f, rail.tangent(f).getDistance(), .00001f)
        }
        assertEquals(0f, rail.point(0f).x, 0f)
        assertEquals(360f, rail.point(1f).x, 0f)
        assertEquals(1f, rail.tangent(0f).y, .00001f)
        assertEquals(-1f, rail.tangent(1f).y, .00001f)
        assertEquals(1f, rail.tangent(.5f).x, .00001f)
        val join = rail.arc / rail.length
        assertTrue((rail.tangent(join + .00001f) - rail.tangent(join - .00001f)).getDistance() < .001f)
    }

    @Test fun everyPointRoundTripsIncludingBothCurves() {
        for (density in listOf(1f, 2.75f, 3.5f)) for (width in listOf(252f, 332f, 452f)) {
            val rail = MiniPlayerRail(width * density, 24 * density, 13 * density)
            for (i in 0..1000) {
                val fraction = i / 1000f
                assertEquals(fraction, rail.fraction(rail.point(fraction)), .00001f)
            }
            assertEquals(0f, rail.fraction(Offset(-30f, 0f)), 0f)
            assertEquals(1f, rail.fraction(Offset(width * density + 30f, 0f)), 0f)
        }
    }

    @Test fun curvesAreContinuousAndTimeFollowsArcLength() {
        val rail = MiniPlayerRail(360f, 24f, 13f)
        assertEquals(Offset(0f, 11f), rail.point(0f))
        assertEquals(360f, rail.point(1f).x, .00001f)
        assertEquals(180f, rail.point(.5f).x, .00001f)
        assertEquals(24f, rail.point(.5f).y, .00001f)
        val join = rail.arc / rail.length
        assertTrue((rail.point(join + .00001f) - rail.point(join - .00001f)).getDistance() < .01f)
        assertTrue(rail.point(join / 2).y < 24f)
    }
}
