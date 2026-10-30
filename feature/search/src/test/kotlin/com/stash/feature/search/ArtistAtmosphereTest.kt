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
    @Test fun `red portrait remains wine coloured even with most pixels dark`() {
        // Old implementation vetoed this scene because dark coverage > 56%.
        val palette = artistPaletteFromPixels(photo(
            0xFF101115.toInt() to 61,
            0xFFE00A27.toInt() to 39,
        ))
        assertNotEquals(ArtistNeutralPalette, palette)
        assertTrue("red surface should be warmer than blue", palette.surface.red > palette.surface.blue)
        assertTrue("hero bridge must express more colour", palette.hero.red > palette.bridge.red)
        assertTrue("bridge should fade towards reading surface", palette.bridge.red > palette.surface.red)
        assertTrue("reading surface stays dark", palette.surface.red < .20f)
    }

    @Test fun `red hue wrapping at zero degrees is stable`() {
        val palette = artistPaletteFromPixels(photo(
            0xFFE51A3F.toInt() to 34, // near 350 degrees
            0xFFE53F1A.toInt() to 34, // near 10 degrees
            0xFF3C3D3E.toInt() to 32,
        ))
        assertTrue(palette.hero.red > palette.hero.blue)
        assertTrue(palette.hero.red > palette.hero.green)
        assertNotEquals(ArtistNeutralPalette, palette)
    }

    @Test fun `minor vivid detail cannot recolour a mostly grayscale portrait`() {
        val palette = artistPaletteFromPixels(photo(
            0xFF090B0F.toInt() to 60,
            0xFFB2B2B2.toInt() to 23,
            0xFFFF1600.toInt() to 17,
        ))
        assertEquals(ArtistNeutralPalette, palette)
        assertEquals(ArtistNeutralAtmosphere, palette.surface)
    }

    @Test fun `multicolour scenes choose the largest coherent hue family`() {
        val palette = artistPaletteFromPixels(photo(
            0xFF31A953.toInt() to 45,
            0xFF2876C0.toInt() to 32,
            0xFFB82323.toInt() to 23,
        ))
        assertTrue(palette.surface.green > palette.surface.red)
        assertTrue(palette.surface.green > palette.surface.blue)
    }

    @Test fun `monochrome picture uses neutral charcoal rather than blue tint`() {
        val palette = artistPaletteFromPixels(photo(
            0xFF030303.toInt() to 30,
            0xFF818181.toInt() to 40,
            0xFFF3F3F3.toInt() to 30,
        ))
        assertEquals(ArtistNeutralPalette, palette)
        assertTrue(kotlin.math.abs(palette.surface.red - palette.surface.blue) < .03f)
    }

}
