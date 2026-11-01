package com.stash.feature.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistPhotoHeroGeometryTest {
    @Test fun continuousRadialFadeHasSymmetricUContours() {
        val left = artistPhotoFadeDistanceFraction(0f, .65f, 390f, 300f)
        val center = artistPhotoFadeDistanceFraction(.5f, .65f, 390f, 300f)
        val right = artistPhotoFadeDistanceFraction(1f, .65f, 390f, 300f)
        assertThat(left).isWithin(.0001f).of(right)
        assertThat(left).isGreaterThan(center)
        assertThat(artistPhotoFadeDistanceFraction(.25f, .7f, 390f, 300f))
            .isWithin(.0001f).of(artistPhotoFadeDistanceFraction(.75f, .7f, 390f, 300f))
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
