package com.stash.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class MiniPlayerRailTest {
    @Test fun everyPointRoundTripsIncludingBothCurves() {
        for (density in listOf(1f, 2.75f, 3.5f)) for (width in listOf(252f, 332f, 452f)) {
            val rail = MiniPlayerRail(width * density, 24 * density, 13 * density, density)
            for (i in 0..1000) {
                val fraction = i / 1000f
                assertEquals(fraction, rail.fraction(rail.point(fraction)), .00001f)
            }
            assertEquals(0f, rail.fraction(Offset(-30f, 0f)), 0f)
            assertEquals(1f, rail.fraction(Offset(width * density + 30f, 0f)), 0f)
        }
    }

    @Test fun curvesAreContinuousAndTimeFollowsArcLength() {
        val rail = MiniPlayerRail(360f, 24f, 13f, 1f)
        assertEquals(Offset(1f, 11f), rail.point(0f))
        assertEquals(359f, rail.point(1f).x, .00001f)
        assertEquals(180f, rail.point(.5f).x, .00001f)
        assertEquals(23f, rail.point(.5f).y, .00001f)
        val join = rail.arc / rail.length
        assertTrue((rail.point(join + .00001f) - rail.point(join - .00001f)).getDistance() < .01f)
        assertTrue(rail.point(join / 2).y < 23f)
    }
}
