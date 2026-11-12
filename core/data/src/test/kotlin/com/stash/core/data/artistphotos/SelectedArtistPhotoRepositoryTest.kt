package com.stash.core.data.artistphotos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectedArtistPhotoRepositoryTest {
    private val spotify1 = "https://i.scdn.co/image/photo1"
    private val spotify2 = "https://i.scdn.co/image/photo2"
    private val artist = "https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb"
    private lateinit var context: Context

    @Before fun clearStorage() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sonora_selected_artist_photo_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun differentIdsDoNotLeakBetweenSameNameArtistsAndSurviveRecreation() {
        val repo = SelectedArtistPhotoRepository(context)
        repo.selectSpotify("UCfirst", spotify1, artist)
        repo.selectSpotify("UCsecond", spotify2, artist)
        assertEquals(spotify1, repo.photos.value["UCfirst"]?.photoUrl)
        assertEquals(spotify2, repo.photos.value["UCsecond"]?.photoUrl)
        assertEquals(spotify1, SelectedArtistPhotoRepository(context).get("UCfirst")?.photoUrl)
    }

    @Test fun noNetworkFallbackCanOverwriteSelectedPhoto() {
        val repo = SelectedArtistPhotoRepository(context)
        repo.selectSpotify("UC1", spotify1, artist)
        repo.selectFallbackIfAbsent("UC1", "https://deezer.invalid/different.jpg")
        assertEquals(spotify1, repo.get("UC1")?.photoUrl)
    }

    @Test fun manualWinsEvenWhenSpotifyChangesThenRestoreUsesNewSpotifyPhoto() {
        val repo = SelectedArtistPhotoRepository(context)
        repo.selectSpotify("UC1", spotify1, artist)
        repo.selectManual("UC1", "https://example.org/manual.jpg")
        repo.selectSpotify("UC1", spotify2, artist)
        assertEquals("https://example.org/manual.jpg", repo.get("UC1")?.photoUrl)
        assertEquals(ArtistPhotoOrigin.USER, repo.get("UC1")?.origin)
        repo.restoreAutomatic("UC1")
        assertEquals(spotify2, repo.get("UC1")?.photoUrl)
        assertEquals(ArtistPhotoOrigin.SPOTIFY, repo.get("UC1")?.origin)
    }

    @Test fun sameSpotifyImageRetainsSelectedUrlWhileDifferentImageReplacesIt() {
        val highRes = "https://example.org/verified-original.jpg"
        val previouslyVerified = SelectedArtistPhoto(highRes, ArtistPhotoOrigin.SPOTIFY, spotify1, artist)
        assertEquals(highRes, ArtistPhotoSelectionRules.spotify(previouslyVerified, spotify1, artist).photoUrl)
        assertEquals(spotify2, ArtistPhotoSelectionRules.spotify(previouslyVerified, spotify2, artist).photoUrl)
    }

    @Test fun removingManualWithoutKnownSpotifyClearsSelectionSafely() {
        val repo = SelectedArtistPhotoRepository(context)
        repo.selectManual("UC1", "file:///private/photo")
        repo.restoreAutomatic("UC1")
        assertNull(repo.get("UC1"))
    }

    @Test fun highResSelectionIsRetainedUntilOfficialPhotoChanges() {
        val repo = SelectedArtistPhotoRepository(context)
        val better = "https://upload.wikimedia.org/wikipedia/commons/a/ab/portrait.jpg"
        repo.selectSpotify("UC1", spotify1, artist)
        repo.markResolutionChecked("UC1", spotify1, better)
        assertEquals(better, repo.get("UC1")?.photoUrl)
        assertEquals(spotify1, repo.get("UC1")?.resolutionCheckedFor)

        repo.selectSpotify("UC1", spotify1, artist)
        assertEquals(better, repo.get("UC1")?.photoUrl)
        assertEquals(better, SelectedArtistPhotoRepository(context).get("UC1")?.photoUrl)

        repo.selectSpotify("UC1", spotify2, artist)
        assertEquals(spotify2, repo.get("UC1")?.photoUrl)
        assertNull(repo.get("UC1")?.resolutionCheckedFor)
    }

    @Test fun lateImageSearchResultCannotOverwriteNewSpotifyPhotoOrManualOverride() {
        val repo = SelectedArtistPhotoRepository(context)
        val better = "https://upload.wikimedia.org/wikipedia/commons/a/ab/a.jpg"
        repo.selectSpotify("UC1", spotify1, artist)
        repo.selectSpotify("UC1", spotify2, artist)
        repo.markResolutionChecked("UC1", spotify1, better)
        assertEquals(spotify2, repo.get("UC1")?.photoUrl)
        repo.selectManual("UC1", "file:///manual/portrait.jpg")
        repo.markResolutionChecked("UC1", spotify2, better)
        assertEquals("file:///manual/portrait.jpg", repo.get("UC1")?.photoUrl)
    }

    @Test fun rejectedHigherResolutionRevertsToSpotifyWithoutClearingIdentity() {
        val repo = SelectedArtistPhotoRepository(context)
        val better = "https://upload.wikimedia.org/wikipedia/commons/a/ab/a.jpg"
        repo.selectSpotify("UC1", spotify1, artist)
        repo.markResolutionChecked("UC1", spotify1, better)
        repo.rejectHighResIfSelected("UC1", better)
        assertEquals(spotify1, repo.get("UC1")?.photoUrl)
        assertEquals(spotify1, repo.get("UC1")?.resolutionCheckedFor)
    }

    @Test fun manualSelectionRemembersHighResAutomaticPhotoOnRestore() {
        val repo = SelectedArtistPhotoRepository(context)
        val better = "https://cdn-images.dzcdn.net/images/artist/abc/1000x1000-000000.jpg"
        repo.selectSpotify("UC1", spotify1, artist)
        repo.markResolutionChecked("UC1", spotify1, better)
        repo.selectManual("UC1", "file:///manual/portrait.jpg")
        repo.restoreAutomatic("UC1")
        assertEquals(better, repo.get("UC1")?.photoUrl)
    }

    @Test fun legacyDeezerFallbackIsDroppedButSpotifyAndManualArePreserved() {
        val repo = SelectedArtistPhotoRepository(context)
        repo.selectFallbackIfAbsent("UC1", "https://deezer.example/portrait.jpg")
        repo.selectSpotify("UC2", spotify1, artist)
        repo.selectManual("UC3", "file:///manual.jpg")
        repo.clearLegacyFallback("UC1")
        repo.clearLegacyFallback("UC2")
        repo.clearLegacyFallback("UC3")
        assertNull(repo.get("UC1"))
        assertEquals(spotify1, repo.get("UC2")?.photoUrl)
        assertEquals("file:///manual.jpg", repo.get("UC3")?.photoUrl)
    }

}
