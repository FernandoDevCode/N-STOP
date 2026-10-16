package com.stash.feature.nowplaying.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Artificial swatches avoid network/artwork fixtures and exercise conservative gates. */
class PlayerArtDirectionTest {
    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun `greyscale covers keep a neutral fallback`() {
        val pixels = IntArray(900) { if (it % 3 == 0) argb(230, 230, 230) else argb(18, 18, 18) }
        val result = playerArtDirectionFromPixels(pixels)
        assertEquals(PlayerColorMode.UNIFIED, result.mode)
        assertEquals(PlayerArtDirection.Neutral, result)
    }

    @Test fun `single-hue album uses unified direction`() {
        val pixels = IntArray(900) { if (it < 450) argb(12, 16, 25) else argb(20, 87, 158) }
        val result = playerArtDirectionFromPixels(pixels)
        assertEquals(PlayerColorMode.UNIFIED, result.mode)
        assertEquals(0.12f, result.glowStrength, 0.001f)
        assertTrue(result.progress.blue > result.progress.red)
    }

    @Test fun `multiple supported hues select at most two roles`() {
        val pixels = IntArray(900) {
            when {
                it < 200 -> argb(12, 14, 23)
                it < 550 -> argb(200, 40, 56)
                else -> argb(34, 89, 201)
            }
        }
        val result = playerArtDirectionFromPixels(pixels)
        assertEquals(PlayerColorMode.SELECTIVE, result.mode)
        assertEquals(0.23f, result.glowStrength, 0.001f)
        assertTrue(result.playGlow != result.playFill)
    }

    @Test fun `pale moon against dark blue sky selects cinematic fill and blue glow`() {
        val pixels = IntArray(1000) {
            when {
                it < 600 -> argb(10, 27, 54)
                it < 800 -> argb(31, 99, 160)
                else -> argb(228, 237, 249)
            }
        }
        val result = playerArtDirectionFromPixels(pixels)
        assertEquals(PlayerColorMode.CINEMATIC, result.mode)
        assertEquals(0.34f, result.glowStrength, 0.001f)
        assertTrue(result.playFill.red > 0.8f)
        assertTrue(result.playGlow.blue > result.playGlow.red)
    }

    @Test fun `thin multicolour prism bands are selective not a full rainbow UI`() {
        val pixels = IntArray(1600) {
            when {
                it < 30 -> argb(240, 40, 50)
                it < 60 -> argb(40, 215, 70)
                it < 90 -> argb(30, 90, 224)
                it < 120 -> argb(239, 217, 43)
                else -> argb(12, 12, 15)
            }
        }
        assertEquals(PlayerColorMode.SELECTIVE, playerArtDirectionFromPixels(pixels).mode)
    }

    @Test fun `transparent art falls back without guessing colours`() {
        val result = playerArtDirectionFromPixels(IntArray(100))
        assertEquals(PlayerArtDirection.Neutral, result)
    }
}
