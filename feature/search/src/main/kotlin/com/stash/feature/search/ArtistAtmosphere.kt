package com.stash.feature.search

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Three distinct jobs: preserve the photo's colour near the hero, connect it to
 * the page, then leave a quiet dark reading surface. No artist-specific rules.
 */
internal data class ArtistAtmospherePalette(
    val hero: Color,
    val bridge: Color,
    val surface: Color,
)

internal val ArtistNeutralAtmosphere = Color(0xFF202124)
internal val ArtistBaseInk = Color(0xFF0E1115)
internal val ArtistNeutralPalette = ArtistAtmospherePalette(
    hero = Color(0xFF2C2D2F),
    bridge = Color(0xFF262729),
    surface = ArtistNeutralAtmosphere,
)

private const val HUE_BUCKETS = 24
private const val PHOTO_CACHE_SIZE = 48
private val photoCacheLock = Any()
private val photoPaletteCache = object : LinkedHashMap<String, ArtistAtmospherePalette>(
    PHOTO_CACHE_SIZE, 0.75f, true,
) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, ArtistAtmospherePalette>?,
    ): Boolean = size > PHOTO_CACHE_SIZE
}

/**
 * Vote by occupied photo area, not highest saturation. Each 15-degree bucket
 * joins its neighbours, including the circular 345/0-degree red boundary.
 * Darkness is never an automatic veto: black silhouettes against an extensive
 * red background should still produce a red atmosphere.
 */
internal fun artistPaletteFromPixels(pixels: IntArray): ArtistAtmospherePalette {
    if (pixels.isEmpty()) return ArtistNeutralPalette

    val votes = DoubleArray(HUE_BUCKETS)
    val counts = IntArray(HUE_BUCKETS)
    val hueSin = DoubleArray(HUE_BUCKETS)
    val hueCos = DoubleArray(HUE_BUCKETS)
    var opaque = 0
    var chromatic = 0

    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        opaque++
        val red = ((pixel ushr 16) and 255) / 255.0
        val green = ((pixel ushr 8) and 255) / 255.0
        val blue = (pixel and 255) / 255.0
        val high = max(red, max(green, blue))
        val low = min(red, min(green, blue))
        val chroma = high - low
        if (high < 0.11 || chroma < 0.04 || chroma / high < 0.17) continue

        val hue = (when (high) {
            red -> (green - blue) / chroma + if (green < blue) 6.0 else 0.0
            green -> (blue - red) / chroma + 2.0
            else -> (red - green) / chroma + 4.0
        } * 60.0) % 360.0
        val bin = (hue / (360.0 / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
        // Every qualified pixel counts. A tiny vivid patch cannot outweigh
        // a much larger, moderately colourful background.
        val weight = 0.85 + 0.15 * (chroma / high)
        val radians = hue * PI / 180.0
        votes[bin] += weight
        counts[bin]++
        hueSin[bin] += sin(radians) * weight
        hueCos[bin] += cos(radians) * weight
        chromatic++
    }

    if (opaque == 0 || chromatic.toDouble() / opaque < 0.22) {
        return ArtistNeutralPalette
    }

    var winner = 0
    var strongest = -1.0
    for (center in 0 until HUE_BUCKETS) {
        val previous = (center + HUE_BUCKETS - 1) % HUE_BUCKETS
        val next = (center + 1) % HUE_BUCKETS
        val strength = votes[previous] + votes[center] + votes[next]
        if (strength > strongest) {
            strongest = strength
            winner = center
        }
    }

    val previous = (winner + HUE_BUCKETS - 1) % HUE_BUCKETS
    val next = (winner + 1) % HUE_BUCKETS
    val covered = counts[previous] + counts[winner] + counts[next]
    // Require meaningful photographic area and a coherent hue family.
    // Balanced multicolour photographs still get a stable deterministic vote.
    val chromaticShare = chromatic.toDouble() / opaque
    val coherenceNeeded = if (chromaticShare < 0.45) 0.56 else 0.38
    if (covered.toDouble() / opaque < 0.20 ||
        covered.toDouble() / chromatic < coherenceNeeded
    ) return ArtistNeutralPalette

    val x = hueCos[previous] + hueCos[winner] + hueCos[next]
    val y = hueSin[previous] + hueSin[winner] + hueSin[next]
    val hue = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    val coverage = (covered.toDouble() / opaque).coerceIn(0.0, 1.0)
    val depth = ((coverage - 0.20) / 0.55).coerceIn(0.0, 1.0)

    return ArtistAtmospherePalette(
        hero = artistHsv(hue, 0.49 + depth * 0.17, 0.30),
        bridge = artistHsv(hue, 0.36 + depth * 0.16, 0.23),
        // Keep the artist's temperature but return to the app's quiet charcoal.
        // A pure HSV surface uniformly dyes long track lists (brown/red/blue).
        surface = artistSurface(hue, depth),
    )
}

/**
 * Global reading-surface policy: always start from neutral charcoal and add
 * only a restrained hue bias. The photo still owns hero and bridge; this colour
 * is for long song lists, albums and the scrolling page background.
 */
private fun artistSurface(hue: Double, depth: Double): Color {
    val tint = artistHsv(hue, 0.40, 0.18)
    val influence = (0.28 + depth * 0.10).toFloat()
    return Color(
        red = ArtistNeutralAtmosphere.red * (1f - influence) + tint.red * influence,
        green = ArtistNeutralAtmosphere.green * (1f - influence) + tint.green * influence,
        blue = ArtistNeutralAtmosphere.blue * (1f - influence) + tint.blue * influence,
        alpha = 1f,
    )
}

/** Legacy single-colour helper, kept for the original artist-colour tests. */
internal fun artistAtmosphereFromPixels(pixels: IntArray): Color =
    artistPaletteFromPixels(pixels).surface

/** Pure conversion so palette tests do not require Android framework calls. */
private fun artistHsv(hue: Double, saturation: Double, value: Double): Color {
    val sector = (hue / 60.0).toInt() % 6
    val fraction = hue / 60.0 - floor(hue / 60.0)
    val p = value * (1.0 - saturation)
    val q = value * (1.0 - fraction * saturation)
    val t = value * (1.0 - (1.0 - fraction) * saturation)
    val (r, g, b) = when (sector) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return Color(r.toFloat(), g.toFloat(), b.toFloat())
}

/**
 * Load a small copy from the existing Coil image loader; sample away from the
 * UI thread. Key by the actual photo URL, not artist name or current track.
 * Cache only successful reads so temporary network failures remain retryable.
 */
internal suspend fun artistPaletteFromPhoto(
    context: Context,
    photoUrl: String?,
): ArtistAtmospherePalette {
    if (photoUrl.isNullOrBlank()) return ArtistNeutralPalette
    synchronized(photoCacheLock) { photoPaletteCache[photoUrl] }?.let { return it }

    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return ArtistNeutralPalette

        val palette = withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            try {
                val pixels = IntArray(48 * 48)
                small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                artistPaletteFromPixels(pixels)
            } finally {
                if (small !== bitmap) small.recycle()
            }
        }
        synchronized(photoCacheLock) { photoPaletteCache[photoUrl] = palette }
        palette
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        ArtistNeutralPalette
    }
}


/**
 * Samples the lower band of the original photo, not a second cropped photo.
 * Horizontal samples retain left/center/right colour placement. The caller
 * fades this strip vertically into the reading surface; no bitmap is repeated.
 */
internal suspend fun artistBottomEdgeColors(context: Context, photoUrl: String?): List<Color> {
    if (photoUrl.isNullOrBlank()) return emptyList()
    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(160, 160))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return emptyList()
        withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val width = bitmap.width
            val height = bitmap.height
            if (width < 3 || height < 3) return@withContext emptyList()
            (0..6).map { index ->
                val centerX = (index * (width - 1) / 6)
                val radiusX = (width / 11).coerceAtLeast(1)
                val startY = (height * .72f).toInt().coerceIn(0, height - 1)
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0L
                val step = (height / 36).coerceAtLeast(1)
                for (y in startY until height step step) {
                    for (x in (centerX - radiusX).coerceAtLeast(0)..(centerX + radiusX).coerceAtMost(width - 1)) {
                        val pixel = bitmap.getPixel(x, y)
                        if ((pixel ushr 24) < 128) continue
                        red += (pixel ushr 16) and 255
                        green += (pixel ushr 8) and 255
                        blue += pixel and 255
                        count++
                    }
                }
                if (count == 0L) ArtistNeutralPalette.bridge else Color(
                    (red.toFloat() / count / 255f),
                    (green.toFloat() / count / 255f),
                    (blue.toFloat() / count / 255f),
                )
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        emptyList()
    }
}


/**
 * V4: a real photo-derived continuation, rather than a flat seven-stop wash.
 * A low resolution texture samples a progressively deeper part of the actual
 * bottom third. Horizontal averaging increases toward the bottom so recognizable
 * objects disappear while their spatial light/colour remains. Transparent
 * output dissolves into the page charcoal. Never repeats the full photograph.
 * Bitmap construction runs off the main thread, once per photo change.
 */
internal suspend fun artistPhotoContinuation(
    context: Context,
    photoUrl: String?,
): ImageBitmap? {
    if (photoUrl.isNullOrBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(192, 192))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return null
        withContext(Dispatchers.Default) {
            val source = result.image.toBitmap()
            val sampled = Bitmap.createScaledBitmap(source, 112, 112, true)
            val outWidth = 112
            val outHeight = 240
            try {
                val pixels = IntArray(outWidth * outHeight)
                for (y in 0 until outHeight) {
                    val progress = y.toFloat() / (outHeight - 1)
                    // Start with actual lower-image pixels; gradually approach
                    // the bottom edge as detail diffuses away.
                    val sourceY = (76 + (35 * progress).toInt()).coerceIn(0, 111)
                    val radius = 2 + (progress * 25).toInt()
                    // Smoothstep opacity gives one continuous dissolve without
                    // a horizontal boundary or repeated photographic objects.
                    val fade = (1f - progress * progress * (3f - 2f * progress))
                    val alpha = (fade * 255f).toInt().coerceIn(0, 255)
                    for (x in 0 until outWidth) {
                        var r = 0
                        var g = 0
                        var b = 0
                        var n = 0
                        for (dx in -radius..radius step 3) {
                            val sx = (x + dx).coerceIn(0, outWidth - 1)
                            val c = sampled.getPixel(sx, sourceY)
                            r += (c ushr 16) and 255
                            g += (c ushr 8) and 255
                            b += c and 255
                            n++
                        }
                        // Slight neutral darkening keeps white costumes from
                        // washing out section titles; colour character remains.
                        val attenuation = .83f - progress * .16f
                        pixels[y * outWidth + x] =
                            (alpha shl 24) or
                            ((r.toFloat() / n * attenuation).toInt().coerceIn(0, 255) shl 16) or
                            ((g.toFloat() / n * attenuation).toInt().coerceIn(0, 255) shl 8) or
                            (b.toFloat() / n * attenuation).toInt().coerceIn(0, 255)
                    }
                }
                Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888).asImageBitmap()
            } finally {
                if (sampled !== source) sampled.recycle()
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }
}
