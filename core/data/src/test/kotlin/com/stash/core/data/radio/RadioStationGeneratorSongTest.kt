package com.stash.core.data.radio

import com.stash.data.ytmusic.CanonicalMatch
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.ArtistProfile
import com.stash.data.ytmusic.model.ArtistSummary
import com.stash.data.ytmusic.model.TrackSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationGeneratorSongTest {
    private val yt: YTMusicApiClient = mockk()
    private fun gen() = RadioStationGenerator(yt)
    private fun track(name: String) = TrackSummary("v0000000001", name, "Lil Wayne", null, 180.0, null)

    @Test fun songRadioMixesSeedAndRelatedArtists() = runTest {
        coEvery { yt.resolveArtist("Lil Wayne") } returns ArtistSummary("WAYNE", "Lil Wayne", null)
        coEvery { yt.getArtist("WAYNE") } returns ArtistProfile("WAYNE", "Lil Wayne", null, null,
            listOf(track("Lollipop")), emptyList(), emptyList(),
            listOf(ArtistSummary("DRAKE", "Drake", null)))
        coEvery { yt.resolveArtist("Drake") } returns ArtistSummary("DRAKE", "Drake", null)
        coEvery { yt.getArtist("DRAKE") } returns ArtistProfile("DRAKE", "Drake", null, null,
            listOf(TrackSummary("v0000000002", "Forever", "Drake", null, 180.0, null)),
            emptyList(), emptyList(), emptyList())
        val (_, batch) = gen().start(RadioSeed.Song("A Milli", "Lil Wayne", ytVideoId = "v0000000003"))
        assertTrue(batch.any { it.artist == "Lil Wayne" })
        assertTrue(batch.any { it.artist == "Drake" })
        assertTrue(batch.mapNotNull { it.youtubeId }.distinct().size == batch.size)
    }

    @Test fun unresolvedCandidatesArePrefetchedConcurrently() = runTest {
        coEvery { yt.searchCanonicalMatch(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            val key = firstArg<String>() + "|" + secondArg<String>()
            CanonicalMatch(
                videoId = "v%010d".format(key.hashCode().toLong() and 0x7fffffffL),
                thumbnailUrl = null,
            )
        }
        val candidates = (1..20).map {
            RadioCandidate("Artist " + it, "Song " + it, null, 1f)
        }
        val session = RadioSession(RadioSeed.Song("Seed", "Seed Artist"),
            candidates.toMutableList(), mutableSetOf())
        val batch = gen().drainBatch(session, RadioStationGenerator.FIRST_BATCH)
        assertEquals(RadioStationGenerator.FIRST_BATCH, batch.size)
        assertTrue("expected concurrent lookahead", testScheduler.currentTime <= 2_000)
    }
}
