package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistFeaturedAlbumGlassTest {
    @Test fun `artwork hue remains obvious in the translucent card`() {
        val red = artistFeaturedAlbumGlassFill(Color(0xFFB7212D))
        val yellow = artistFeaturedAlbumGlassFill(Color(0xFFB79B21))

        assertTrue("Mothica-like cover must produce red glass", red.red > red.green * 2f)
        assertTrue("Linkin Park-like cover must produce golden glass", yellow.red > yellow.blue * 3f)
        assertTrue("Golden glass should preserve green component", yellow.green > yellow.blue * 2f)
        assertEquals(.42f, red.alpha, 1f / 255f)
        assertEquals(.42f, yellow.alpha, 1f / 255f)
    }

    @Test fun `transparent glass falls back to neutral when no artwork hue exists`() {
        val fill = artistFeaturedAlbumGlassFill(null)
        val border = artistFeaturedAlbumGlassBorder(null)

        assertTrue(kotlin.math.abs(fill.red - fill.blue) < .02f)
        assertEquals(.085f, fill.alpha, 1f / 255f)
        assertEquals(.12f, border.alpha, 1f / 255f)
    }

    @Test fun `border follows artwork without excessive opacity`() {
        val redArtwork = Color(0xFFB7212D)
        val fill = artistFeaturedAlbumGlassFill(redArtwork)
        val border = artistFeaturedAlbumGlassBorder(redArtwork)

        assertTrue(border.red > border.green)
        assertTrue(border.alpha < fill.alpha)
    }
}
