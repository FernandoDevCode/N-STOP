package com.stash.feature.search

import com.stash.core.data.musicbrainz.MusicBrainzEditionTrack
import com.stash.data.ytmusic.YTMusicCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicBrainzAlbumDiscoveryTest {
    private val track = MusicBrainzEditionTrack(
        position = 1, disc = 1, title = "Eyeless", durationMs = 234_000,
        recordingId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    )
    private fun result(id: String, title: String = "Eyeless", artist: String = "Slipknot",
        duration: Long? = 234_000) = YTMusicCatalogItem(
        kind = YTMusicCatalogItem.Kind.TRACK, id = id,
        title = title, subtitle = "$artist · Slipknot", artworkUrl = null,
        durationMs = duration, isExplicit = false, rank = 0,
    )

    @Test fun `unique matching YTM track is usable while the MusicBrainz ID remains metadata`() {
        val match = findMusicBrainzTrackAudio("Slipknot", track,
            listOf(result("ABCDEFGHIJK"), result("ABCDEFGHIJK")))
        assertEquals("ABCDEFGHIJK", match?.id)
    }

    @Test fun `different version, performer and duration are not silently substituted`() {
        assertNull(findMusicBrainzTrackAudio("Slipknot", track,
            listOf(result("ABCDEFGHIJK", title = "Eyeless (Live)"))))
        assertNull(findMusicBrainzTrackAudio("Slipknot", track,
            listOf(result("ABCDEFGHIJK", artist = "Slipknot Tribute"))))
        assertNull(findMusicBrainzTrackAudio("Slipknot", track,
            listOf(result("ABCDEFGHIJK", duration = 240_000))))
    }

    @Test fun `two different matching recordings are ambiguous rather than guessed`() {
        assertNull(findMusicBrainzTrackAudio("Slipknot", track,
            listOf(result("ABCDEFGHIJK"), result("ABCDEFGHIJL"))))
    }
}
