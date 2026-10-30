package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAtmosphereTest {
    private fun photo(vararg groups: Pair<Int, Int>): IntArray =
        groups.flatMap { (colour, count) -> List(count) { colour } }.toIntArray()

    @Test fun `empty or monochrome portrait yields neutral backdrop`() {
        assertEquals(ArtistNeutralAtmosphere, artistAtmosphereFromPixels(intArrayOf()))
        assertEquals(
            ArtistNeutralAtmosphere,
            artistAtmosphereFromPixels(photo(0xFF171717.toInt() to 80, 0xFFBCBCBC.toInt() to 20)),
        )
    }

    @Test fun `dark band photo with small warm accent cannot turn orange`() {
        val pixels = photo(
            0xFF17191D.toInt() to 65,
            0xFF888789.toInt() to 20,
            0xFFDB5319.toInt() to 15,
        )
        assertEquals(ArtistNeutralAtmosphere, artistAtmosphereFromPixels(pixels))
    }

    @Test fun `predominantly blue sky has deep muted cool atmosphere`() {
        val pixels = photo(
            0xFF287AC4.toInt() to 72,
            0xFFE1E1E2.toInt() to 18,
            0xFF15191E.toInt() to 10,
        )
        val result = artistAtmosphereFromPixels(pixels)
        assertNotEquals(ArtistNeutralAtmosphere, result)
        assertTrue(result.blue > result.red)
        assertTrue(result.blue > result.green)
        assertTrue(result.blue < .35f)
    }

    @Test fun `predominantly warm photo is dark and low saturation`() {
        val pixels = photo(
            0xFFCD5D20.toInt() to 80,
            0xFF7C6E62.toInt() to 20,
        )
        val result = artistAtmosphereFromPixels(pixels)
        assertNotEquals(ArtistNeutralAtmosphere, result)
        assertTrue(result.red < .25f)
        assertTrue(result.red - result.blue < .10f)
    }

    @Test fun `weak mixed hues cannot dominate the whole photo`() {
        val pixels = photo(
            0xFF4D4E50.toInt() to 46,
            0xFFB43225.toInt() to 20,
            0xFF208E9C.toInt() to 18,
            0xFF989A92.toInt() to 16,
        )
        assertEquals(ArtistNeutralAtmosphere, artistAtmosphereFromPixels(pixels))
    }
}
