package com.stash.feature.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistPhotoHeroGeometryTest {
    @Test fun uFadeStartsEarlierAtTheEdges() {
        val left = artistPhotoFadeStartFraction(0f)
        val center = artistPhotoFadeStartFraction(.5f)
        val right = artistPhotoFadeStartFraction(1f)
        assertThat(left).isWithin(.0001f).of(right)
        assertThat(center).isGreaterThan(left)
        assertThat(center).isWithin(.001f).of(.85f)
        assertThat(artistPhotoFadeStartFraction(.25f))
            .isWithin(.0001f).of(artistPhotoFadeStartFraction(.75f))
    }

    @Test fun framePreservesNaturalHeightForLandscapePhotos() {
        val width = 390f
        assertThat(artistPhotoFrameHeightDp(width, 900f, 1.5f))
            .isWithin(.01f).of(260f)
        assertThat(artistPhotoFrameHeightDp(width, 900f, 2f))
            .isWithin(.01f).of(195f)
    }

    @Test fun tallImagesGetBoundedFrameWithoutAnyCropRequirement() {
        val height = artistPhotoFrameHeightDp(390f, 900f, .6f)
        assertThat(height).isGreaterThan(0f)
        assertThat(height).isAtMost(487.5f)
        assertThat(artistPhotoFrameHeightDp(390f, 900f, 0f))
            .isGreaterThan(0f)
    }
}
