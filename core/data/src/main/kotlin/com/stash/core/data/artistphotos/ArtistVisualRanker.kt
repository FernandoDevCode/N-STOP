package com.stash.core.data.artistphotos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A ranked gallery image with its ORIGINAL dimensions and a small ARGB preview.
 *
 * Input ordering is explicit: popularityRank=1 is the gallery's most popular.
 * The caller must obtain images through an authorized source and decode
 * previews off the UI thread, e.g. <=128x128. Never feed full-size bitmaps.
 */
data class VisualPhotoCandidate(
    val url: String,
    val popularityRank: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sampleWidth: Int,
    val sampleHeight: Int,
    val argb: IntArray,
)

data class VisualPhotoResult(
    val candidate: VisualPhotoCandidate,
    val score: Double,
    val popularityScore: Double,
    val colorScore: Double,
    val contrastScore: Double,
    val detailScore: Double,
)

/**
 * No AI, face recognition or artist-specific exceptions.
 *
 * Community ranking is an eligibility gate (top 20) and a scoring signal.
 * Photographs that are actually monochrome are excluded; muted COLOR photos
 * remain eligible. Detail and contrast are modest indicators of visual presence,
 * not a claim to know an artist's identity or whether a photo is iconic.
 */
object ArtistVisualRanker {
    const val POPULAR_POOL = 20
    private const val MAX_PREVIEW_DIMENSION = 256

    fun rank(candidates: List<VisualPhotoCandidate>): List<VisualPhotoResult> =
        candidates.asSequence()
            .filter { it.popularityRank in 1..POPULAR_POOL }
            .sortedBy { it.popularityRank }
            .distinctBy { it.url }
            .take(POPULAR_POOL)
            .mapNotNull(::evaluate)
            .sortedWith(
                compareByDescending<VisualPhotoResult> { it.score }
                    .thenBy { it.candidate.popularityRank }
                    .thenBy { it.candidate.url },
            )
            .toList()

    fun select(candidates: List<VisualPhotoCandidate>): VisualPhotoCandidate? =
        rank(candidates).firstOrNull()?.candidate

    private fun evaluate(candidate: VisualPhotoCandidate): VisualPhotoResult? {
        val w = candidate.sampleWidth
        val h = candidate.sampleHeight
        if (candidate.url.isBlank() || !candidate.url.startsWith("https://") ||
            candidate.sourceWidth < 200 || candidate.sourceHeight < 200 ||
            w < 16 || h < 16 || w > MAX_PREVIEW_DIMENSION ||
            h > MAX_PREVIEW_DIMENSION || candidate.argb.size != w * h
        ) return null

        val lightness = DoubleArray(w * h)
        var sum = 0.0
        var squared = 0.0
        var chromaSum = 0.0
        var colorfulPixels = 0
        for (i in candidate.argb.indices) {
            val p = candidate.argb[i]
            val r = (p ushr 16) and 255
            val g = (p ushr 8) and 255
            val b = p and 255
            val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            val chroma = (max(r, max(g, b)) - min(r, min(g, b))) / 255.0
            lightness[i] = lum
            sum += lum
            squared += lum * lum
            chromaSum += chroma
            if (chroma > 0.10) colorfulPixels++
        }
        val n = lightness.size.toDouble()
        val meanChroma = chromaSum / n
        // Reject grayscale, but NOT vintage photographs with restrained hues.
        if (meanChroma < 0.035 && colorfulPixels / n < 0.025) return null

        val mean = sum / n
        val contrast = sqrt((squared / n - mean * mean).coerceAtLeast(0.0))
        var differences = 0.0
        var edges = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (x + 1 < w) {
                    differences += abs(lightness[i] - lightness[i + 1])
                    edges++
                }
                if (y + 1 < h) {
                    differences += abs(lightness[i] - lightness[i + w])
                    edges++
                }
            }
        }

        val colorScore = ((meanChroma - 0.035) / 0.30).coerceIn(0.0, 1.0)
        val contrastScore = (contrast / 0.26).coerceIn(0.0, 1.0)
        val detailScore = ((differences / edges.coerceAtLeast(1)) / 0.20)
            .coerceIn(0.0, 1.0)
        val aspect = candidate.sourceWidth.toDouble() / candidate.sourceHeight
        val framingScore = when {
            aspect >= 1.15 -> 1.0
            aspect >= 0.85 -> 0.75
            else -> 0.45
        }
        // Popularity supplies context that local pixel statistics cannot.
        // Visual scoring can still defeat a popular but flat photograph.
        val popularity = ((POPULAR_POOL + 1 - candidate.popularityRank) /
            POPULAR_POOL.toDouble()).coerceIn(0.0, 1.0)
        val visual = 0.25 * colorScore + 0.35 * contrastScore +
            0.30 * detailScore + 0.10 * framingScore
        val score = 0.35 * popularity + 0.65 * visual
        return VisualPhotoResult(candidate, score, popularity, colorScore, contrastScore, detailScore)
    }
}
