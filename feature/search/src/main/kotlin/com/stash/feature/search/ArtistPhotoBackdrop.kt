package com.stash.feature.search

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.stash.core.common.ArtUrlUpgrader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A fixed, compact raster: resizing and blurring never happen during Compose drawing.
internal const val ARTIST_BACKDROP_WIDTH = 72
internal const val ARTIST_BACKDROP_HEIGHT = 128
private const val BACKDROP_CACHE_CAPACITY = 12
private val backdropCacheLock = Any()
private val backdropCache = object : LinkedHashMap<String, ImageBitmap>(
    BACKDROP_CACHE_CAPACITY, .75f, true,
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
        size > BACKDROP_CACHE_CAPACITY
}

/** Each backdrop is a *blurred copy* of the real hero photograph, never a synthesized extension. */
internal suspend fun artistPhotoBackdrop(context: Context, photoUrl: String?): ImageBitmap? {
    if (photoUrl.isNullOrBlank()) return null
    synchronized(backdropCacheLock) { backdropCache[photoUrl] }?.let { return it }

    return try {
        // Coil reuses its own caches. A limited source size avoids decoding original megapixel bitmaps.
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(256, 256))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return null

        val backdrop = withContext(Dispatchers.Default) {
            val source = result.image.toBitmap()
            if (!artistBackdropPhotoUsable(source.width, source.height)) return@withContext null

            // Center crop is intentionally independent of hero's Fit: a narrow portrait fills
            // the viewport while all recognizable detail disappears in the heavy blur.
            val targetRatio = ARTIST_BACKDROP_WIDTH.toFloat() / ARTIST_BACKDROP_HEIGHT
            val sourceRatio = source.width.toFloat() / source.height
            val cropWidth = if (sourceRatio > targetRatio) {
                (source.height * targetRatio).toInt().coerceIn(1, source.width)
            } else source.width
            val cropHeight = if (sourceRatio < targetRatio) {
                (source.width / targetRatio).toInt().coerceIn(1, source.height)
            } else source.height
            val crop = Bitmap.createBitmap(
                source,
                (source.width - cropWidth) / 2,
                (source.height - cropHeight) / 2,
                cropWidth,
                cropHeight,
            )
            val scaled = Bitmap.createScaledBitmap(
                crop, ARTIST_BACKDROP_WIDTH, ARTIST_BACKDROP_HEIGHT, true,
            )
            try {
                val pixels = IntArray(ARTIST_BACKDROP_WIDTH * ARTIST_BACKDROP_HEIGHT)
                scaled.getPixels(pixels, 0, ARTIST_BACKDROP_WIDTH, 0, 0,
                    ARTIST_BACKDROP_WIDTH, ARTIST_BACKDROP_HEIGHT)
                // Two passes of a separable box filter create a broad Gaussian-like blur.
                val blurred = artistBlurBackdropPixels(
                    pixels, ARTIST_BACKDROP_WIDTH, ARTIST_BACKDROP_HEIGHT, radius = 12, passes = 2,
                )
                Bitmap.createBitmap(ARTIST_BACKDROP_WIDTH, ARTIST_BACKDROP_HEIGHT,
                    Bitmap.Config.ARGB_8888).apply {
                    setPixels(blurred, 0, ARTIST_BACKDROP_WIDTH, 0, 0,
                        ARTIST_BACKDROP_WIDTH, ARTIST_BACKDROP_HEIGHT)
                }.asImageBitmap()
            } finally {
                if (scaled !== crop && scaled !== source) scaled.recycle()
                if (crop !== source) crop.recycle()
            }
        }
        if (backdrop != null) {
            synchronized(backdropCacheLock) { backdropCache[photoUrl] = backdrop }
        }
        backdrop
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null // V3 chromatic background is the deterministic fallback.
    }
}

/** Do not amplify tiny thumbnails with blur or trigger wasteful image loading on invalid URLs. */
internal fun artistBackdropPhotoUsable(width: Int, height: Int): Boolean =
    width >= 96 && height >= 96

/**
 * CPU-only, deterministic blur; input and result are independent ARGB arrays.
 * All work is done in Dispatchers.Default before the bitmap reaches the drawing layer.
 */
internal fun artistBlurBackdropPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    passes: Int = 2,
): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height)
    require(radius >= 0 && passes >= 0)
    if (radius == 0 || passes == 0) return pixels.copyOf()

    var data = pixels.copyOf()
    repeat(passes) {
        val horizontal = IntArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (dx in -radius..radius) {
                    val pixel = data[y * width + (x + dx).coerceIn(0, width - 1)]
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                    count++
                }
                horizontal[y * width + x] = (0xFF shl 24) or
                    ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            }
        }
        val vertical = IntArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (dy in -radius..radius) {
                    val pixel = horizontal[(y + dy).coerceIn(0, height - 1) * width + x]
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                    count++
                }
                vertical[y * width + x] = (0xFF shl 24) or
                    ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            }
        }
        data = vertical
    }
    return data
}
