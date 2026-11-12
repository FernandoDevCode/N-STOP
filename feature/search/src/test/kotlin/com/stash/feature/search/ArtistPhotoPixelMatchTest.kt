package com.stash.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPhotoPixelMatchTest {
    private fun portrait(shift: Int = 0): IntArray = IntArray(1600) { i ->
        val x = i % 40
        val y = i / 40
        val r = (x * 5 + y * 2 + shift).coerceIn(0, 255)
        val g = (x * 3 + y * 4 + shift).coerceIn(0, 255)
        val b = (x * 2 + y * 2 + shift).coerceIn(0, 255)
        0xff000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    @Test fun smallCompressionDifferencesStillMatchTheSameImage() {
        assertTrue(ArtistPhotoPixelMatch.isSamePhoto(portrait(), portrait(3)))
    }

    @Test fun unrelatedPhotoAndUniformPlaceholdersAreRejected() {
        assertFalse(ArtistPhotoPixelMatch.isSamePhoto(portrait(), portrait(100)))
        val blank = IntArray(1600) { 0xff444444.toInt() }
        assertFalse(ArtistPhotoPixelMatch.isSamePhoto(blank, blank))
    }

    @Test fun dimensionsMustActuallyBeHigherNotUpscaledOrLower() {
        assertTrue(ArtistPhotoPixelMatch.isHigherResolution(640, 640, 1000, 1000))
        assertFalse(ArtistPhotoPixelMatch.isHigherResolution(640, 640, 640, 640))
        assertFalse(ArtistPhotoPixelMatch.isHigherResolution(640, 640, 1200, 300))
    }
}
