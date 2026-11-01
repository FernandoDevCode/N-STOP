package com.stash.core.data.artistphotos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistVisualRankerTest {
    private val side = 48

    private fun candidate(
        id: String,
        rank: Int,
        rgb: (Int, Int) -> Int,
        width: Int = 1200,
        height: Int = 800,
    ) = VisualPhotoCandidate(
        url = "https://example.org/$id.jpg",
        popularityRank = rank,
        sourceWidth = width,
        sourceHeight = height,
        sampleWidth = side,
        sampleHeight = side,
        argb = IntArray(side * side) { i -> rgb(i % side, i / side) },
    )

    private fun pixel(r: Int, g: Int, b: Int) =
        (0xff shl 24) or (r.coerceIn(0,255) shl 16) or
            (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)

    private fun dynamicColor(x: Int, y: Int): Int =
        pixel((37 * x + 13 * y) % 256, (11 * x + 67 * y) % 256,
            (59 * x + 29 * y) % 256)

    @Test fun grayscaleIsExcludedEvenWhenFirstInTheGallery() {
        val gray = candidate("gray", 1, { x, y ->
            val v = (37 * x + 31 * y) % 256
            pixel(v, v, v)
        })
        val color = candidate("color", 7, ::dynamicColor)
        assertThat(ArtistVisualRanker.select(listOf(gray, color))).isEqualTo(color)
    }

    @Test fun aFlatColoredPhotoDoesNotAutomaticallyBeatAnExpressiveOne() {
        val flat = candidate("flat", 1, { _, _ -> pixel(220, 15, 22) })
        val detailed = candidate("detailed", 4, ::dynamicColor)
        assertThat(ArtistVisualRanker.select(listOf(flat, detailed)))
            .isEqualTo(detailed)
    }

    @Test fun popularityWinsWhenVisualEvidenceIsIdentical() {
        val first = candidate("first", 1, ::dynamicColor)
        val fifth = candidate("fifth", 5, ::dynamicColor)
        assertThat(ArtistVisualRanker.select(listOf(fifth, first)))
            .isEqualTo(first)
    }

    @Test fun mutedButColoredPhotosRemainEligible() {
        val muted = candidate("muted", 1, { x, y ->
            val v = (x * 3 + y * 3) % 140
            pixel(v + 20, v + 12, v)
        })
        assertThat(ArtistVisualRanker.select(listOf(muted))).isEqualTo(muted)
    }

    @Test fun onlyTopTwentyPopularImagesAreConsidered() {
        val weak = candidate("weak", 1, { _, _ -> pixel(150, 60, 40) })
        val outsider = candidate("outsider", 21, ::dynamicColor)
        assertThat(ArtistVisualRanker.select(listOf(weak, outsider)))
            .isEqualTo(weak)
    }

    @Test fun invalidOrUnavailablePreviewsAreNotSelected() {
        val gray = candidate("gray", 1, { _, _ -> pixel(100, 100, 100) })
        val invalid = candidate("invalid", 2, ::dynamicColor)
            .copy(argb = IntArray(4))
        assertThat(ArtistVisualRanker.rank(listOf(gray, invalid))).isEmpty()
        assertThat(ArtistVisualRanker.select(emptyList())).isNull()
    }

    @Test fun tinyOriginalIsNotPreferredToProperlySizedPhotoWithSamePixels() {
        val tiny = candidate("tiny", 1, ::dynamicColor, width = 160, height = 160)
        val valid = candidate("valid", 2, ::dynamicColor)
        assertThat(ArtistVisualRanker.select(listOf(tiny, valid))).isEqualTo(valid)
    }
}
