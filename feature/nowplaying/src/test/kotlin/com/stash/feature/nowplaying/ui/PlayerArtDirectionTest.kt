package com.stash.feature.nowplaying.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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

    @Test fun `broad multicolour cover limits accents to two supported families`() {
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
        assertEquals(2, result.accents.size)
        assertTrue(!result.sparseHighlights)
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
                it < 20 -> argb(240, 40, 50)
                it < 40 -> argb(40, 215, 70)
                it < 60 -> argb(30, 90, 224)
                it < 80 -> argb(239, 217, 43)
                else -> argb(12, 12, 15)
            }
        }
        val result = playerArtDirectionFromPixels(pixels)
        assertEquals(PlayerColorMode.SELECTIVE, result.mode)
        assertTrue(result.sparseHighlights)
        assertTrue(result.canvas.luminance() < .003f)
        assertTrue(result.artworkOpacity < .20f)
        assertTrue(result.ambientStrength < .20f)
        assertTrue(result.accents.size >= 3)
        assertTrue(result.playFill.luminance() < .02f)
        assertTrue(result.progress.blue >= result.progress.red)
    }

    @Test fun `red and green unknown covers keep their own dark canvas and readable controls`() {
        listOf(argb(205, 35, 46), argb(28, 165, 87), argb(25, 95, 190)).forEach { colour ->
            val result = playerArtDirectionFromPixels(IntArray(1600) { colour })
            assertEquals(PlayerColorMode.UNIFIED, result.mode)
            assertTrue(result.canvas.luminance() < .01f)
            assertTrue(result.ambientStrength > .30f)
            val darkest = lerp(result.playFill, Color.Black, .10f).luminance()
            val lightest = lerp(result.playFill, Color.White, .30f).luminance()
            val ink = result.playInk.luminance()
            val contrast = if (ink < darkest) (darkest + .05f) / (ink + .05f)
                else (ink + .05f) / (lightest + .05f)
            assertTrue("icon contrast $contrast", contrast >= 3f)
        }
    }

    @Test fun `same histogram in isolated and distributed regions changes ambient confidence`() {
        val clustered = IntArray(1600) { if (it / 40 < 20 && it % 40 < 20) argb(22, 91, 190) else argb(19, 20, 24) }
        val distributed = IntArray(1600) { if (it % 4 == 0) argb(22, 91, 190) else argb(19, 20, 24) }
        val a = playerArtDirectionFromPixels(clustered)
        val b = playerArtDirectionFromPixels(distributed)
        assertEquals(a.mode, b.mode)
        assertEquals(a.progress, b.progress)
        assertTrue(a.ambientStrength < b.ambientStrength)
        assertTrue(a.lightX < b.lightX)
    }

    @Test fun `tiny coloured noise on light or black artwork cannot establish a palette`() {
        listOf(argb(248, 248, 244), argb(6, 6, 7)).forEach { ground ->
            val pixels = IntArray(1600) { if (it < 8) argb(230, 30, 70) else ground }
            assertEquals(PlayerArtDirection.Neutral, playerArtDirectionFromPixels(pixels))
        }
    }

    @Test fun `predominantly light cover does not turn a small coloured mark into a saturated theme`() {
        val pixels = IntArray(1600) { if (it < 240) argb(205, 25, 70) else argb(248, 248, 240) }
        assertEquals(PlayerArtDirection.Neutral, playerArtDirectionFromPixels(pixels))
    }

    @Test fun `transparent art falls back without guessing colours`() {
        val result = playerArtDirectionFromPixels(IntArray(100))
        assertEquals(PlayerArtDirection.Neutral, result)
    }
}
