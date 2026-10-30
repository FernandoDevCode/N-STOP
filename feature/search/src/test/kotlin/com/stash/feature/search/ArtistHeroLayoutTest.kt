package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
