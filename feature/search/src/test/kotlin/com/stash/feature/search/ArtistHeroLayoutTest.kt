package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.Color
import org.junit.Test

class ArtistHeroLayoutTest {
    @Test fun `phone photo space follows available width`() {
        assertEquals(198f, artistPhotoLeadInDp(360f, 800f), .01f)
        assertTrue(artistPhotoLeadInDp(412f, 900f) > artistPhotoLeadInDp(360f, 800f))
    }

    @Test fun `tablet photo space is capped instead of growing with width`() {
        assertEquals(300f, artistPhotoLeadInDp(1200f, 1600f), .01f)
    }

    @Test fun `short viewport limits photo space without constraining content`() {
        assertEquals(160f, artistPhotoLeadInDp(800f, 400f), .01f)
        assertEquals(144f, artistPhotoLeadInDp(240f, 320f), .01f)
    }
    @Test fun `smoked glass uses original photo tone with actual transparency`() {
        val redPhoto = artistSmokedGlassFill(Color(0xFF8B2534))
        val bluePhoto = artistSmokedGlassFill(Color(0xFF1B466A))
        assertEquals(.58f, redPhoto.alpha, .0001f)
        assertEquals(.58f, bluePhoto.alpha, .0001f)
        assertTrue(redPhoto.red > redPhoto.blue)
        assertTrue(bluePhoto.blue > bluePhoto.red)
    }

    @Test fun `glass remains restrained and usable for neutral photographs`() {
        val neutral = artistSmokedGlassFill(ArtistNeutralPalette.hero)
        assertTrue(neutral.red < .23f)
        assertTrue(neutral.green < .23f)
        assertTrue(neutral.blue < .23f)
        assertTrue(neutral.alpha < .65f)
    }

}
