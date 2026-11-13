package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistFeaturedAlbumGlassTest {
    @Test fun `glass inherits photo hue without becoming opaque`() {
        val blue = artistFeaturedAlbumGlassFill(Color(0xFF264C65))
        val red = artistFeaturedAlbumGlassFill(Color(0xFF65342C))

        assertTrue(blue.blue > blue.red)
        assertTrue(red.red > red.blue)
        assertEquals(.16f, blue.alpha, .001f)
        assertEquals(.16f, red.alpha, .001f)
    }

    @Test fun `monochrome artist keeps quiet neutral glass`() {
        val fill = artistFeaturedAlbumGlassFill(ArtistNeutralPalette.bridge)
        val border = artistFeaturedAlbumGlassBorder(ArtistNeutralPalette.bridge)

        assertTrue(kotlin.math.abs(fill.red - fill.blue) < .02f)
        assertEquals(.12f, border.alpha, .001f)
    }
}
