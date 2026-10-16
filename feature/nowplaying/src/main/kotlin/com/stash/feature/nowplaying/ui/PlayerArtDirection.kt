package com.stash.feature.nowplaying.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.stash.core.ui.ambient.AmbientPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Conservative, artwork-only colour direction for Now Playing.
 *
 * Roles are separate: progress, play fill, play halo and ambience do not have to
 * share one swatch. There is no semantic/image-model dependency. Unclear artwork
 * falls back to a single hue or neutral colour rather than an arbitrary rainbow.
 */
internal enum class PlayerColorMode { UNIFIED, SELECTIVE, CINEMATIC }

@Immutable
internal data class PlayerArtDirection(
    val mode: PlayerColorMode,
    val ambient: AmbientPalette,
    val progress: Color,
    val playFill: Color,
    val playGlow: Color,
    val playInk: Color,
    /** 0..1, muted for ordinary covers and stronger only when evidence supports it. */
    val glowStrength: Float,
) {
    companion object {
        val Neutral = PlayerArtDirection(
            mode = PlayerColorMode.UNIFIED,
            ambient = AmbientPalette(
                dom = Color(0xFF18232C), vib = Color(0xFF61788B),
                mut = Color(0xFF24333D), key = "neutral-art",
            ),
            progress = Color(0xFFB2C6D7),
            playFill = Color(0xFFE9EFF4),
            playGlow = Color(0xFF7499B4),
            playInk = Color(0xFF10151D),
            glowStrength = 0f,
        )
    }
}

private data class Sample(val hue: Float, val saturation: Float, val value: Float)
private fun sample(argb: Int): Sample {
    val r = ((argb ushr 16) and 255) / 255f
    val g = ((argb ushr 8) and 255) / 255f
    val b = (argb and 255) / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val delta = hi - lo
    val s = if (hi <= 0f) 0f else delta / hi
    val raw = when {
        delta < 0.0001f -> 0f
        hi == r -> (g - b) / delta + (if (g < b) 6f else 0f)
        hi == g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    return Sample((raw * 60f) % 360f, s, hi)
}

private fun hsv(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs(h % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 1f -> Triple(c, x, 0f)
        h < 2f -> Triple(x, c, 0f)
        h < 3f -> Triple(0f, c, x)
        h < 4f -> Triple(0f, x, c)
        h < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, 1f)
}

private fun hueDistance(a: Float, b: Float): Float {
    val difference = abs(a - b) % 360f
    return min(difference, 360f - difference)
}

/** Small bounded process cache, shared between cover and lyrics presentations. */
private val artworkDirections = object : LinkedHashMap<String, PlayerArtDirection>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlayerArtDirection>?): Boolean =
        size > 64
}

/** Uses at most a 40 x 40 pixel sample; called off the UI thread. */
internal fun playerArtDirectionFromBitmap(bitmap: Bitmap, artKey: String): PlayerArtDirection {
    synchronized(artworkDirections) { artworkDirections[artKey]?.let { return it } }
    val scaled = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
    val resolved = try {
        val pixels = IntArray(40 * 40)
        scaled.getPixels(pixels, 0, 40, 0, 0, 40, 40)
        playerArtDirectionFromPixels(pixels, artKey)
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
    synchronized(artworkDirections) { artworkDirections[artKey] = resolved }
    return resolved
}

/** Pure analysis, also usable by JVM unit tests without Android bitmap objects. */
internal fun playerArtDirectionFromPixels(argb: IntArray, artKey: String = "art"): PlayerArtDirection {
    if (argb.isEmpty()) return PlayerArtDirection.Neutral

    val weights = FloatArray(12)
    val counts = IntArray(12)
    val red = FloatArray(12)
    val green = FloatArray(12)
    val blue = FloatArray(12)
    var opaque = 0
    var dark = 0
    var neutralBright = 0
    var neutralR = 0f
    var neutralG = 0f
    var neutralB = 0f
    var colourful = 0

    for (pixel in argb) {
        if ((pixel ushr 24) < 128) continue
        opaque++
        val colour = sample(pixel)
        if (colour.value < 0.27f) dark++
        if (colour.value > 0.72f && colour.saturation < 0.23f) {
            neutralBright++
            neutralR += ((pixel ushr 16) and 255) / 255f
            neutralG += ((pixel ushr 8) and 255) / 255f
            neutralB += (pixel and 255) / 255f
        }
        if (colour.saturation < 0.28f || colour.value < 0.16f || colour.value > 0.96f) continue
        colourful++
        val i = (colour.hue / 30f).toInt().coerceIn(0, 11)
        val w = colour.saturation * (0.75f + colour.value * 0.25f)
        weights[i] += w
        counts[i]++
        red[i] += ((pixel ushr 16) and 255) / 255f
        green[i] += ((pixel ushr 8) and 255) / 255f
        blue[i] += (pixel and 255) / 255f
    }

    if (opaque == 0 || colourful < opaque * 0.035f) return PlayerArtDirection.Neutral
    val first = weights.indices.maxByOrNull { weights[it] } ?: return PlayerArtDirection.Neutral
    if (counts[first] < 8) return PlayerArtDirection.Neutral
    val firstHue = sample(
        (0xFF000000L or
            ((red[first] / counts[first] * 255f).roundToInt().coerceIn(0,255).toLong() shl 16) or
            ((green[first] / counts[first] * 255f).roundToInt().coerceIn(0,255).toLong() shl 8) or
            (blue[first] / counts[first] * 255f).roundToInt().coerceIn(0,255).toLong()
        ).toInt(),
    ).hue
    val second = weights.indices
        .filter { it != first && hueDistance(it * 30f + 15f, firstHue) >= 65f }
        .maxByOrNull { weights[it] }
    val distinctSecond = second != null &&
        weights[second] >= weights[first] * 0.34f &&
        counts[second] >= max(8f, opaque * 0.035f).toInt()
    val secondHue = if (distinctSecond && second != null) second * 30f + 15f else firstHue

    // White moon on a dark blue sky is cinematic. A short white caption on a
    // rainbow cover is not: both contrast and meaningful neutral area matter.
    val cinematic = dark >= opaque * 0.38f &&
        neutralBright >= opaque * 0.09f &&
        colourful >= opaque * 0.16f &&
        weights[first] >= colourful * 0.10f

    val mode = when {
        cinematic -> PlayerColorMode.CINEMATIC
        distinctSecond -> PlayerColorMode.SELECTIVE
        else -> PlayerColorMode.UNIFIED
    }

    val main = hsv(firstHue, 0.70f, 0.76f)
    val secondAccent = hsv(secondHue, 0.64f, 0.63f)
    val pale = if (neutralBright > 0) {
        Color(
            (neutralR / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
            (neutralG / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
            (neutralB / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
        )
    } else Color(0xFFECEFF3)
    val fill = if (cinematic) pale else main
    val glow = if (distinctSecond && !cinematic) secondAccent else main

    return PlayerArtDirection(
        mode = mode,
        ambient = AmbientPalette(
            dom = hsv(firstHue, 0.55f, 0.23f),
            vib = hsv(firstHue, 0.65f, 0.57f),
            mut = hsv(secondHue, if (distinctSecond) 0.36f else 0.21f, 0.25f),
            key = artKey,
        ),
        progress = main,
        playFill = fill,
        playGlow = glow,
        playInk = if (sampleColorLuminance(fill) > 0.43f) Color(0xFF10131A) else Color.White,
        glowStrength = when (mode) {
            PlayerColorMode.UNIFIED -> 0.12f
            PlayerColorMode.SELECTIVE -> 0.23f
            PlayerColorMode.CINEMATIC -> 0.34f
        },
    )
}

private fun sampleColorLuminance(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
