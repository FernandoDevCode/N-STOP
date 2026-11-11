package com.stash.feature.home

import com.stash.core.data.db.entity.TrackTagEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonoraTasteScorerTest {
    @Test fun jazzInSavedLibraryButNotRecentPlaysBecomesForgottenDiscovery() {
        val tracks = (1L..10L).map { id ->
            Track(
                id = id,
                title = "Track $id",
                artist = if (id <= 6) "Rock $id" else "Jazz $id",
                source = if (id % 2L == 0L) MusicSource.SPOTIFY else MusicSource.YOUTUBE,
                spotifySavedAt = if (id % 2L == 0L) 10L else null,
                ytMusicSavedAt = if (id % 2L == 1L) 10L else null,
            )
        }
        val tags = tracks.map {
            TrackTagEntity(it.id, if (it.id <= 6) "rock" else "jazz",
                1f, TrackTagEntity.SOURCE_TRACK, 0L)
        }
        val recent = (1L..6L).associateWith { 10 }
        val taste = SonoraTasteScorer.rank(tracks, tags, recent)
        assertEquals("rock", taste.currentTags.first())
        assertEquals("jazz", taste.forgottenTags.first())
        assertFalse(taste.forgottenTags.contains("rock"))
    }

    @Test fun noRecentPlaysUsesSavedLibraryForCurrentDiscovery() {
        val tracks = (1L..5L).map {
            Track(id = it, title = "Saved track $it", artist = "Library artist",
                spotifySavedAt = 123L)
        }
        val tags = tracks.map {
            TrackTagEntity(it.id, "alternative rock", 1f, TrackTagEntity.SOURCE_TRACK, 0L)
        }
        val taste = SonoraTasteScorer.rank(tracks, tags, emptyMap())
        assertEquals(listOf("alternative rock"), taste.currentTags)
        assertTrue("Cannot call a genre forgotten without play history", taste.forgottenTags.isEmpty())
    }

    @Test fun unsupportedOneOffTagsCannotPretendToBeStrongPreferences() {
        val tracks = (1L..5L).map { Track(id=it, title="t", artist="a") }
        val tags = (1L..4L).map {
            TrackTagEntity(it, "jazz", 1f, TrackTagEntity.SOURCE_TRACK, 0L)
        } + TrackTagEntity(5L, "rare arbitrary label", 1f, TrackTagEntity.SOURCE_TRACK, 0L)
        val result = SonoraTasteScorer.rank(tracks, tags, emptyMap())
        assertTrue("jazz" in result.currentTags)
        assertFalse("rare arbitrary label" in result.currentTags)
        assertTrue(result.forgottenTags.isEmpty())
    }
}
