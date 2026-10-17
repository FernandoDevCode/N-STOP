package com.stash.feature.nowplaying.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.stash.core.ui.ambient.AmbientPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    /** Supported artwork swatches only; empty on unified/neutral covers. */
    val accents: List<Color> = emptyList(),
    val canvas: Color = Color(0xFF05080D),
    /** Dark artwork with small, genuinely contrasting details, not a new colour mode. */
    val sparseHighlights: Boolean = false,
    val artworkOpacity: Float = .14f,
    val ambientStrength: Float = .18f,
    /** Normalised centre of the supported colour family, measured once in the sample. */
    val lightX: Float = .5f,
    val lightY: Float = .3f,
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
    val lightX = FloatArray(12)
    val lightY = FloatArray(12)
    // The bitmap path always supplies a square 40x40 sample. Non-square synthetic
    // samples still get bounded regions rather than depending on a specific artwork.
    val width = sqrt(argb.size.toDouble()).toInt().coerceAtLeast(1)
    val height = (argb.size + width - 1) / width
    val regions = Array(12) { BooleanArray(16) }
    var opaque = 0
    var dark = 0
    var light = 0
    var neutralBright = 0
    var neutralR = 0f
    var neutralG = 0f
    var neutralB = 0f
    var colourful = 0
    var highlights = 0

    for ((position, pixel) in argb.withIndex()) {
        if ((pixel ushr 24) < 128) continue
        opaque++
        val colour = sample(pixel)
        val luminance = Color(pixel).luminance()
        if (luminance < .045f) dark++
        if (luminance > .60f) light++
        if (luminance > .13f && colour.value > .52f) highlights++
        if (colour.value > 0.72f && colour.saturation < 0.23f) {
            neutralBright++
            neutralR += ((pixel ushr 16) and 255) / 255f
            neutralG += ((pixel ushr 8) and 255) / 255f
            neutralB += (pixel and 255) / 255f
        }
        if (colour.saturation < 0.28f || colour.value < 0.16f) continue
        colourful++
        val i = (colour.hue / 30f).toInt().coerceIn(0, 11)
        // Suppress almost-black coloured noise without discarding bright narrow
        // bands. Linear luminance and HSV together retain saturated blue details.
        val w = colour.saturation * (.25f + .75f * sqrt(luminance))
        val x = ((position % width) + .5f) / width
        val y = ((position / width) + .5f) / height
        weights[i] += w
        lightX[i] += x * w
        lightY[i] += y * w
        regions[i][(y * 4).toInt().coerceIn(0, 3) * 4 + (x * 4).toInt().coerceIn(0, 3)] = true
        counts[i]++
        red[i] += ((pixel ushr 16) and 255) / 255f
        green[i] += ((pixel ushr 8) and 255) / 255f
        blue[i] += (pixel and 255) / 255f
    }

    if (opaque == 0 || colourful < opaque * .035f || light >= opaque * .70f) return PlayerArtDirection.Neutral
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
        // Thin, deliberate colour bands (e.g. prism covers) can occupy only
        // a small part of an otherwise black album. Still require real area.
        counts[second] >= max(10f, opaque * 0.018f).toInt()
    val darkCoverage = dark.toFloat() / opaque
    val colourCoverage = colourful.toFloat() / opaque
    val supported = weights.indices.filter {
        counts[it] >= max(8f, opaque * .005f) && weights[it] >= weights[first] * .10f
    }
    val sparse = darkCoverage >= .72f && colourCoverage < .24f &&
        highlights >= max(8f, opaque * .012f) && supported.isNotEmpty()
    // On a black canvas each luminous family may occupy less than 1.8% of the
    // whole cover. Require agreement among the supported bins, not broad-area
    // thresholds that would throw those details away and leave a single red hue.
    val meaningfulSecond = distinctSecond || (sparse && second != null &&
        second in supported && weights[second] >= weights[first] * .20f)
    val secondHue = if (meaningfulSecond && second != null) second * 30f + 15f else firstHue
    // A small isolated mark is weaker ambient evidence than colour spanning the
    // artwork. This affects illumination, never changes a song's identity.
    val spatialSupport = regions[first].count { it } / 16f
    val confidence = (colourCoverage / .30f).coerceIn(.15f, 1f) *
        (.55f + .45f * spatialSupport)

    // White moon on a dark blue sky is cinematic. A short white caption on a
    // rainbow cover is not: both contrast and meaningful neutral area matter.
    val cinematic = !sparse && dark >= opaque * 0.38f &&
        neutralBright >= opaque * 0.09f &&
        colourful >= opaque * 0.16f &&
        weights[first] >= colourful * 0.10f

    val blueWeight = weights.indices.filter { it in 6..8 }.sumOf { weights[it].toDouble() }
    val blueLed = firstHue in 180f..270f && blueWeight > weights.sum() * .62
    val mode = when {
        cinematic -> PlayerColorMode.CINEMATIC
        sparse && meaningfulSecond -> PlayerColorMode.SELECTIVE
        blueLed -> PlayerColorMode.UNIFIED
        distinctSecond -> PlayerColorMode.SELECTIVE
        else -> PlayerColorMode.UNIFIED
    }

    val main = hsv(firstHue, 0.64f, 0.84f)
    val secondAccent = hsv(secondHue, 0.64f, 0.63f)
    val pale = if (neutralBright > 0) {
        Color(
            (neutralR / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
            (neutralG / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
            (neutralB / neutralBright * 0.84f + 0.16f).coerceIn(0.78f, 1f),
        )
    } else Color(0xFFECEFF3)
    val darkControl = sparse && mode == PlayerColorMode.SELECTIVE
    val fill = when {
        darkControl -> Color(0xFF11161D)
        cinematic -> pale
        // A blue-led family reflects cooler light in the control material;
        // the canvas/progress retain the measured hue. No album-specific rule.
        blueLed -> lerp(hsv(firstHue * .35f + 185f * .65f, .62f, .98f), Color.White, .18f)
        else -> lerp(main, Color.White, .38f)
    }
    val glow = if (blueLed && !cinematic) main else if (meaningfulSecond && !cinematic) secondAccent else main
    // Broad multicolour covers use two supported families. Only a dark base
    // with several small luminous bands warrants a longer selective spectrum.
    val accentBins = if (sparse && supported.size >= 3) supported else
        if (mode == PlayerColorMode.SELECTIVE && second != null) listOf(first, second) else emptyList()
    val accents = accentBins.map { i ->
        val source = Color(red[i] / counts[i], green[i] / counts[i], blue[i] / counts[i])
        // Keep progress on dark canvas legible, including deep blue/red samples.
        lerp(source, Color.White, if (source.luminance() < .20f) .24f else .10f)
    }.let { colors -> if (colors.size <= 7) colors else List(7) { i -> colors[i * colors.lastIndex / 6] } }
    val canvas = if (sparse) Color(0xFF050609) else hsv(firstHue, .55f, .035f)
    // A small multicolour highlight may support a second pool of light, but
    // never a rainbow canvas. Pick a genuinely sampled, contrasting family.
    val reflectionHue = if (sparse) supported.maxByOrNull { hueDistance(firstHue, it * 30f + 15f) }
        ?.let { it * 30f + 15f } ?: secondHue else if (mode == PlayerColorMode.UNIFIED) firstHue else secondHue

    return PlayerArtDirection(
        mode = mode,
        ambient = AmbientPalette(
            dom = hsv(firstHue, if (sparse) .50f else .78f, if (sparse) .50f else .70f),
            vib = hsv(firstHue, 0.65f, 0.57f),
            mut = hsv(reflectionHue, if (sparse) .55f else .68f, if (sparse) .50f else .60f),
            key = artKey,
        ),
        progress = if (sparse && accents.size >= 3) Color(0xFFC7DDE5) else lerp(main, Color.White, .18f),
        playFill = fill,
        playGlow = glow,
        // Check the darkest part of the actual button gradient, not gamma-encoded RGB.
        playInk = if (lerp(fill, Color.Black, .12f).luminance() > .18f) Color(0xFF080B10) else Color.White,
        accents = if (mode == PlayerColorMode.SELECTIVE) accents else emptyList(),
        canvas = canvas,
        sparseHighlights = sparse,
        artworkOpacity = if (sparse) .18f else .24f,
        ambientStrength = if (sparse) .18f else .36f + confidence * .36f,
        lightX = (lightX[first] / weights[first]).coerceIn(.15f, .85f),
        lightY = (lightY[first] / weights[first]).coerceIn(.15f, .85f),
        glowStrength = when (mode) {
            PlayerColorMode.UNIFIED -> 0.12f
            PlayerColorMode.SELECTIVE -> 0.23f
            PlayerColorMode.CINEMATIC -> 0.34f
        },
    )
}
