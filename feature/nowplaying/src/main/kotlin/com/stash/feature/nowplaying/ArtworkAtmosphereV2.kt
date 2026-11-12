package com.stash.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import nonstop.artlab.features.PixelImage

/**
 * Production Now Playing environment: a subdued continuation of the actual
 * cover, not the old synthetic mist / noise / beams from the Art Lab recipe.
 *
 * Runs once per artwork on Dispatchers.Default, then uses the existing bounded
 * on-disk atmosphere cache. Nothing is computed on animation frames.
 */
internal object ArtworkAtmosphereV2 {
    const val WIDTH = 192
    const val HEIGHT = 342
    const val CACHE_VERSION = 2

    fun render(cover: Bitmap): PixelImage {
        require(cover.width > 0 && cover.height > 0)
        // A 24x24 optical low-pass removes recognisable enlarged cover details
        // without inventing a second coloured illustration behind the art.
        val reduced = Bitmap.createScaledBitmap(cover, 24, 24, true)
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.rgb(8, 9, 13))
            val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Fit center content into the full field; the upper cover masks it.
            // No accent positioning, procedural noise, mist or directional beams.
            canvas.drawBitmap(
                reduced, Rect(0, 0, reduced.width, reduced.height),
                Rect(0, 0, WIDTH, HEIGHT), filter,
            )
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, HEIGHT.toFloat(),
                    intArrayOf(
                        Color.argb(233, 5, 6, 10),
                        Color.argb(222, 6, 7, 11),
                        Color.argb(202, 7, 8, 12),
                        Color.argb(208, 6, 7, 10),
                        Color.argb(243, 4, 5, 8),
                    ),
                    floatArrayOf(0f, .24f, .57f, .77f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), shade)
            val pixels = IntArray(WIDTH * HEIGHT)
            output.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            return PixelImage(WIDTH, HEIGHT, pixels)
        } finally {
            if (reduced !== cover) reduced.recycle()
            output.recycle()
        }
    }
}
