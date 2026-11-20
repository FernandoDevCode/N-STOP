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

class RadioStationGeneratorArtistTest {
    private val yt: YTMusicApiClient = mockk()
    private fun gen() = RadioStationGenerator(yt)
    private fun songs(name: String) = (1..6).map {
        TrackSummary("$name$it", "$name song $it", name, null, 180.0, null)
    }
    private fun profile(name: String, id: String, related: List<ArtistSummary> = emptyList()) =
        ArtistProfile(id, name, null, null, songs(name), emptyList(), emptyList(), related)

    @Test fun artistUsesRelatedArtistsWithoutDuplicateVideoIds() = runTest {
        coEvery { yt.getArtist("MBV_ID") } returns profile(
            "My Bloody Valentine", "MBV_ID",
            listOf(ArtistSummary("SD_ID", "Slowdive", null), ArtistSummary("RD_ID", "Ride", null))
        )
        coEvery { yt.resolveArtist("Slowdive") } returns ArtistSummary("SD_ID", "Slowdive", null)
        coEvery { yt.resolveArtist("Ride") } returns ArtistSummary("RD_ID", "Ride", null)
        coEvery { yt.getArtist("SD_ID") } returns profile("Slowdive", "SD_ID")
        coEvery { yt.getArtist("RD_ID") } returns profile("Ride", "RD_ID")

        val (session, batch) = gen().start(RadioSeed.Artist("My Bloody Valentine", "MBV_ID"))
        assertTrue(batch.any { it.artist == "My Bloody Valentine" })
        assertTrue(batch.any { it.artist == "Slowdive" || it.artist == "Ride" })
        assertTrue(batch.all { !it.youtubeId.isNullOrBlank() })
        assertTrue(batch.map { it.youtubeId }.toSet().size == batch.size)
        assertTrue(session.played.isNotEmpty())
    }

    @Test fun seedOnlyFallbackWorksWithoutRelatedArtists() = runTest {
        coEvery { yt.getArtist("MBV_ID") } returns profile("My Bloody Valentine", "MBV_ID")
        val (_, batch) = gen().start(RadioSeed.Artist("My Bloody Valentine", "MBV_ID"))
        assertTrue(batch.isNotEmpty())
        assertTrue(batch.all { it.artist == "My Bloody Valentine" })
    }
}
