package com.stash.feature.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPhotoBackdropTest {
    @Test fun `small thumbnails are rejected before costly processing`() {
        assertFalse(artistBackdropPhotoUsable(0, 0))
        assertFalse(artistBackdropPhotoUsable(95, 200))
        assertFalse(artistBackdropPhotoUsable(200, 95))
        assertTrue(artistBackdropPhotoUsable(96, 96))
        assertTrue(artistBackdropPhotoUsable(256, 144))
    }

    @Test fun `constant photo remains constant after heavy blur`() {
        val pixels = IntArray(12 * 17) { 0xFF316B88.toInt() }
        val blurred = artistBlurBackdropPixels(pixels, 12, 17, radius = 5)
        assertArrayEquals(pixels, blurred)
    }

    @Test fun `spatial colours blend and isolated details lose contrast`() {
        val w = 32
        val h = 32
        val photo = IntArray(w * h) { index ->
            if (index % w < w / 2) 0xFF195682.toInt() else 0xFFB64C38.toInt()
        }
        val blurred = artistBlurBackdropPixels(photo, w, h, radius = 7)
        val left = blurred[15 * w + 14]
        val right = blurred[15 * w + 17]
        assertTrue(((left ushr 16) and 255) > 25)
        assertTrue(((right ushr 16) and 255) < 182)
        assertTrue(blurred.all { (it ushr 24) == 255 })
        assertArrayEquals(
            "Processing must not mutate the original image",
            IntArray(w * h) { i -> if (i % w < w / 2) 0xFF195682.toInt() else 0xFFB64C38.toInt() },
            photo,
        )
    }

    @Test fun `zero radius returns independent unchanged pixels`() {
        val input = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val result = artistBlurBackdropPixels(input, 2, 1, radius = 0)
        assertArrayEquals(input, result)
        result[0] = 0
        assertEquals(0xFF000000.toInt(), input[0])
    }
}
