package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SearchAtmosphereMotionTest {
    @Test fun colorIsFixedAndWidthRelativeRadiusKeepsContrast() {
        assertEquals(Color(0xFF19475B), SONORA_SEARCH_BLUE)
        val drift = searchDriftAt(12f).first()
        val radius = searchHazeRadius(400f, drift)
        assertTrue(radius in 300f..500f)
        assertTrue(drift.x in 0f..1f)
        assertTrue(drift.y in 0f..1f)
    }

    @Test fun adjacentFramesDoNotJump() {
        listOf(0f, 2f, 19f, 57f, 121f, 360f).forEach { t ->
            val a = searchDriftAt(t)
            val b = searchDriftAt(t + (1f / 30f))
            assertEquals(3, a.size)
            for (i in a.indices) {
                assertTrue(abs(a[i].x - b[i].x) < .01f)
                assertTrue(abs(a[i].y - b[i].y) < .01f)
            }
        }
    }

    @Test fun trajectoryIsNotAnExactRepeatedOrPingPongSequence() {
        val a = searchDriftAt(0f)
        val b = searchDriftAt(40f)
        val c = searchDriftAt(80f)
        assertFalse(a == b || a == c || b == c)
    }
}
