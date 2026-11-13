package com.stash.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistFeaturedAlbumGlassTest {
    @Test fun `glass preserves different artwork hues without becoming opaque`() {
        val blue = artistFeaturedAlbumGlassFill(Color(0xFF264C65))
        val red = artistFeaturedAlbumGlassFill(Color(0xFF65342C))

        assertTrue("Blue artwork should yield blue tinted glass", blue.blue > blue.red)
        assertTrue("Red artwork should yield warm tinted glass", red.red > red.blue)
        assertEquals(.18f, blue.alpha, 1f / 255f)
        assertEquals(.18f, red.alpha, 1f / 255f)
    }

    @Test fun `unavailable artwork produces translucent neutral fallback`() {
        val fill = artistFeaturedAlbumGlassFill(ArtistNeutralPalette.bridge)
        val border = artistFeaturedAlbumGlassBorder(ArtistNeutralPalette.bridge)

        assertTrue(kotlin.math.abs(fill.red - fill.blue) < .02f)
        assertEquals(.18f, fill.alpha, 1f / 255f)
        // Compose alpha uses 8-bit quantization.
        assertEquals(.14f, border.alpha, 1f / 255f)
    }

    @Test fun `border follows the same artwork family as its fill`() {
        val warmArtwork = Color(0xFF7D302A)
        val fill = artistFeaturedAlbumGlassFill(warmArtwork)
        val border = artistFeaturedAlbumGlassBorder(warmArtwork)

        assertTrue(fill.red > fill.blue)
        assertTrue(border.red > border.blue)
        assertTrue(border.alpha < fill.alpha)
    }
}
