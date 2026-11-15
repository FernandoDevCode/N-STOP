package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SearchAtmosphereMotionTest {
    @Test fun defaultIsGraphiteBlueAndArtistTintIsLimited() {
        val neutral = searchAtmosphereTint(null)
        val red = searchAtmosphereTint(Color.Red)
        assertEquals(Color(0xFF19475B), neutral)
        assertTrue(red.red > neutral.red)
        assertTrue(red.red < Color.Red.red)
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
