package com.stash.core.data.radio

import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.ArtistProfile
import com.stash.data.ytmusic.model.ArtistSummary
import com.stash.data.ytmusic.model.TrackSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationGeneratorGrowTest {
    private val yt: YTMusicApiClient = mockk()
    private fun gen() = RadioStationGenerator(yt)
    private fun track(a: String, n: Int) = TrackSummary(a + n, a + " song " + n, a, null, 180.0, null)
    private fun profile(a: String, id: String, related: List<ArtistSummary> = emptyList()) =
        ArtistProfile(id, a, null, null, (1..6).map { track(a, it) },
            emptyList(), emptyList(), related)

    @Test fun nextBatchUsesPreviouslyUnusedRelatedArtists() = runTest {
        coEvery { yt.getArtist("MBV") } returns profile("My Bloody Valentine", "MBV",
            (1..20).map { ArtistSummary("N" + it + "_ID", "N" + it, null) })
        coEvery { yt.resolveArtist(any()) } answers {
            ArtistSummary(firstArg<String>() + "_ID", firstArg(), null)
        }
        coEvery { yt.getArtist(match { it.startsWith("N") }) } answers {
            val id = firstArg<String>()
            val name = id.removeSuffix("_ID")
            ArtistProfile(id, name, null, null, listOf(track(name, 1)),
                emptyList(), emptyList(), emptyList())
        }
        val generator = gen()
        val (session, first) = generator.start(RadioSeed.Artist("My Bloody Valentine", "MBV"))
        val second = generator.nextBatch(session)
        assertTrue(second.any { it.artist in (13..20).map { n -> "N" + n } })
        val all = (first + second).mapNotNull { it.youtubeId }
        assertTrue(all.distinct().size == all.size)
    }

    @Test fun nextBatchDoesNotRepeatSeedWithoutRelatedArtists() = runTest {
        coEvery { yt.getArtist("MBV") } returns profile("My Bloody Valentine", "MBV")
        val generator = gen()
        val (session, batch) = generator.start(RadioSeed.Artist("My Bloody Valentine", "MBV"))
        assertTrue(batch.isNotEmpty())
        assertTrue(generator.nextBatch(session).isEmpty())
    }
}
