package com.stash.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.Color
import nonstop.artlab.features.PixelImage
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Production V8 background: a subdued continuation of the source artwork.
 * Never synthesizes colored fog, noise, light beams or album-specific effects.
 * Fixed small sampling grid + bilinear reconstruction; cacheable and independent
 * of device Canvas GPU/shader behavior. Runs only on artwork load.
 */
internal object ArtworkAtmosphereV2 {
    const val WIDTH = 192
    const val HEIGHT = 342
    const val CACHE_VERSION = 2
    private const val GRID = 24

    private fun blend(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    fun render(cover: Bitmap): PixelImage {
        require(cover.width > 0 && cover.height > 0)
        val cells = IntArray(GRID * GRID)
        // An image-specific low-pass: a few uniformly distributed samples per
        // cell preserve large color masses, without retaining enlarged detail.
        for (gy in 0 until GRID) for (gx in 0 until GRID) {
            var r = 0
            var g = 0
            var b = 0
            for (oy in 0..1) for (ox in 0..1) {
                val sx = (((gx + (ox + .5f) * .5f) / GRID) * cover.width)
                    .toInt().coerceIn(0, cover.width - 1)
                val sy = (((gy + (oy + .5f) * .5f) / GRID) * cover.height)
                    .toInt().coerceIn(0, cover.height - 1)
                val p = cover.getPixel(sx, sy)
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
            }
            cells[gy * GRID + gx] = Color.rgb(r / 4, g / 4, b / 4)
        }

        val pixels = IntArray(WIDTH * HEIGHT)
        // Maximum influence stays below 30%, keeping the controls legible,
        // while source hue remains physically connected with the artwork.
        val heights = floatArrayOf(0f, .24f, .57f, .77f, 1f)
        val weights = floatArrayOf(.09f, .13f, .24f, .20f, .05f)
        for (y in 0 until HEIGHT) {
            val ny = y.toFloat() / (HEIGHT - 1)
            var segment = 0
            while (segment < heights.size - 2 && ny > heights[segment + 1]) segment++
            val u = ((ny - heights[segment]) /
                (heights[segment + 1] - heights[segment])).coerceIn(0f, 1f)
            val strength = blend(weights[segment], weights[segment + 1], u)
            val fy = ny * (GRID - 1)
            val iy = floor(fy).toInt().coerceIn(0, GRID - 2)
            val vy = fy - iy
            for (x in 0 until WIDTH) {
                val fx = x.toFloat() / (WIDTH - 1) * (GRID - 1)
                val ix = floor(fx).toInt().coerceIn(0, GRID - 2)
                val vx = fx - ix
                val a = cells[iy * GRID + ix]
                val b = cells[iy * GRID + ix + 1]
                val c = cells[(iy + 1) * GRID + ix]
                val d = cells[(iy + 1) * GRID + ix + 1]
                fun channel(shift: Int, base: Int): Int {
                    val q = blend(
                        blend(((a ushr shift) and 255).toFloat(), ((b ushr shift) and 255).toFloat(), vx),
                        blend(((c ushr shift) and 255).toFloat(), ((d ushr shift) and 255).toFloat(), vx),
                        vy,
                    )
                    return blend(base.toFloat(), q, strength).roundToInt().coerceIn(0, 255)
                }
                pixels[y * WIDTH + x] = Color.rgb(channel(16, 6), channel(8, 7), channel(0, 10))
            }
        }
        return PixelImage(WIDTH, HEIGHT, pixels)
    }
}
