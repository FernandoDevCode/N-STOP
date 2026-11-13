package com.stash.feature.search

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedReleaseArtworkTintTest {
    private fun pixels(vararg groups: Pair<Int, Int>): IntArray =
        groups.flatMap { (colour, count) -> List(count) { colour } }.toIntArray()

    @Test fun `red cover with gray lettering retains red tint`() {
        // Mothica-type cover: red field, dark figures and gray lettering.
        val tint = requireNotNull(featuredArtworkTintFromPixels(pixels(
            0xFFE21826.toInt() to 57,
            0xFF151517.toInt() to 23,
            0xFF9D9E9F.toInt() to 20,
        )))
        assertTrue(tint.red > tint.green * 3f)
        assertTrue(tint.red > tint.blue * 3f)
    }

    @Test fun `yellow cover with large white title retains gold tint`() {
        // Linkin Park-type cover: yellow art blocks plus white letters and black frames.
        val tint = requireNotNull(featuredArtworkTintFromPixels(pixels(
            0xFFF4CB35.toInt() to 40,
            0xFF101015.toInt() to 32,
            0xFFF2F2F2.toInt() to 28,
        )))
        assertTrue(tint.red > tint.blue * 3f)
        assertTrue(tint.green > tint.blue * 3f)
        assertTrue(tint.red >= tint.green)
    }

    @Test fun `dark red across 360 degree boundary stays red`() {
        val tint = requireNotNull(featuredArtworkTintFromPixels(pixels(
            0xFFE01735.toInt() to 45,
            0xFFE83F18.toInt() to 35,
            0xFF141414.toInt() to 20,
        )))
        assertTrue(tint.red > tint.blue * 2f)
        assertTrue(tint.red > tint.green * 2f)
    }

    @Test fun `monochrome sleeve and tiny coloured mark do not hallucinate tint`() {
        assertNull(featuredArtworkTintFromPixels(intArrayOf()))
        assertNull(featuredArtworkTintFromPixels(pixels(
            0xFF292929.toInt() to 45,
            0xFFBDBDBD.toInt() to 55,
        )))
        assertNull(featuredArtworkTintFromPixels(pixels(
            0xFF242424.toInt() to 58,
            0xFFDBDBDB.toInt() to 37,
            0xFFE62128.toInt() to 5,
        )))
    }

    @Test fun `multicolor sleeve chooses largest coherent hue region`() {
        val tint = requireNotNull(featuredArtworkTintFromPixels(pixels(
            0xFF269D43.toInt() to 48,
            0xFF305CC1.toInt() to 26,
            0xFFCF2736.toInt() to 26,
        )))
        assertTrue(tint.green > tint.red)
        assertTrue(tint.green > tint.blue)
    }
}
