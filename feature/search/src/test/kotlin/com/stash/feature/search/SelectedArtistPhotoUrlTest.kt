package com.stash.feature.search

import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.core.data.artistphotos.ArtistPhotoOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedArtistPhotoUrlTest {
    @Test fun sameNamedArtistsRetainIndependentImages() {
        val selection = mapOf(
            "UCone" to SelectedArtistPhoto("https://example.org/one.jpg", ArtistPhotoOrigin.SPOTIFY),
            "UCtwo" to SelectedArtistPhoto("file:///photos/manual", ArtistPhotoOrigin.USER),
        )
        assertEquals("https://example.org/one.jpg", selectedArtistPhotoUrl("UCone", "old", selection))
        assertEquals("file:///photos/manual", selectedArtistPhotoUrl("UCtwo", "old", selection))
    }

    @Test fun unknownArtistAndMissingIdentityKeepCatalogFallback() {
        val selections = mapOf("UCone" to SelectedArtistPhoto("https://example.org/a.jpg", ArtistPhotoOrigin.SPOTIFY))
        assertEquals("yt-avatar", selectedArtistPhotoUrl("unknown", "yt-avatar", selections))
        assertEquals("yt-avatar", selectedArtistPhotoUrl(null, "yt-avatar", selections))
    }
}
