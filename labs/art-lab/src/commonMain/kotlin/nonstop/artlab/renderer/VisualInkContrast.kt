package nonstop.artlab.renderer

import nonstop.artlab.features.PixelImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Small, precomputed luminance map of the actual atmospheric composite.
 * Used only to select legible UI ink. No artwork identity, theme mode,
 * per-frame bitmap readback or hard-coded album exceptions.
 */
class VisualInkContrast private constructor(
    private val luma: FloatArray,
    private val cols: Int,
    private val rows: Int,
) {
    /**
     * Chooses the ink with the best *worst-case* WCAG contrast in a local
     * neighborhood. The composited black scrim is included in the estimate.
     */
    fun darkInkAt(x: Float, y: Float, scrimTop: Float, scrimBottom: Float): Boolean {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        val blackOverlay = when {
            ny <= .5f -> scrimTop.coerceIn(0f, 1f) * (1f - ny * 2f)
            else -> scrimBottom.coerceIn(0f, 1f) * ((ny - .5f) * 2f)
        }
        var lowest = 1f
        var highest = 0f
        for (dy in -1..1) for (dx in -1..1) {
            val xx = ((nx * cols).toInt() + dx).coerceIn(0, cols - 1)
            val yy = ((ny * rows).toInt() + dy).coerceIn(0, rows - 1)
            val value = luma[yy * cols + xx] * (1f - blackOverlay)
            lowest = min(lowest, value)
            highest = max(highest, value)
        }
        // For mixed backgrounds choose the foreground whose *least*
        // contrasting location remains more readable.
        val worstWhite = 1.05f / (highest + .05f)
        val worstDark = (lowest + .05f) / .05f
        return worstDark > worstWhite
    }

    companion object {
        private const val COLS = 24
        private const val ROWS = 48

        fun from(image: PixelImage): VisualInkContrast {
            val result = FloatArray(COLS * ROWS)
            fun linear(c: Int): Float {
                val v = c / 255f
                return if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
            }
            for (y in 0 until ROWS) for (x in 0 until COLS) {
                var sum = 0f
                // Four samples per cell avoid a single bright/dark outlier.
                for (oy in 0..1) for (ox in 0..1) {
                    val px = (((x + (ox + .5f) / 2f) / COLS) * image.width)
                        .toInt().coerceIn(0, image.width - 1)
                    val py = (((y + (oy + .5f) / 2f) / ROWS) * image.height)
                        .toInt().coerceIn(0, image.height - 1)
                    val argb = image.argb[py * image.width + px]
                    val r = linear((argb ushr 16) and 255)
                    val g = linear((argb ushr 8) and 255)
                    val b = linear(argb and 255)
                    sum += .2126f * r + .7152f * g + .0722f * b
                }
                result[y * COLS + x] = sum / 4f
            }
            return VisualInkContrast(result, COLS, ROWS)
        }
    }
}
