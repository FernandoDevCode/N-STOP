package com.stash.feature.nowplaying

import com.stash.core.model.RepeatMode
import com.stash.core.model.Track
import nonstop.artlab.direction.isValid
import org.junit.Assert.*
import org.junit.Test

class V8IntegrationTest {
    @Test fun stalledPlaybackStillOffersPause() {
        val state = NowPlayingUiState(isPlaying = false, playWhenReady = true, isBuffering = true).v8State(0)
        assertTrue(state.playing)
    }
    @Test fun unknownDurationAndOutOfRangePositionsStayFinite() {
        assertEquals(0f, NowPlayingUiState(durationMs = 0).v8State(Long.MAX_VALUE).progress)
        assertEquals(0f, NowPlayingUiState(durationMs = 1000).v8State(-100).progress)
        assertEquals(1f, NowPlayingUiState(durationMs = 1000).v8State(2000).progress)
        assertEquals(.25f, NowPlayingUiState(durationMs = 1000).v8State(250).progress)
    }
    @Test fun liveFavoritesShuffleAndRepeatReachRenderer() {
        val track = Track(title = "Faixa real", artist = "Artista", stashLikedAt = 42)
        val state = NowPlayingUiState(currentTrack = track, shuffleEnabled = true, repeatMode = RepeatMode.ONE).v8State(0)
        assertTrue(state.liked); assertTrue(state.shuffle); assertTrue(state.repeat)
        assertFalse(state.lyrics)
        assertFalse(NowPlayingUiState().v8State(0).liked)
    }
    @Test fun missingArtworkIsAValidProceduralFallback() {
        val result = AndroidArtPipeline.fallback()
        assertTrue(result.recipe.isValid())
        assertEquals("none", result.recipe.modelVersion)
        assertNull(result.cover)
        assertNull(result.atmosphere)
    }
}
