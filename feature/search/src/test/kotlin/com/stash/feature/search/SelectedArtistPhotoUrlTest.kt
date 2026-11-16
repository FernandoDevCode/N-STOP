package com.stash.feature.search

import com.stash.core.data.artistphotos.ArtistPhotoOrigin
import com.stash.core.data.artistphotos.SelectedArtistPhoto
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource.SPOTIFY
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.SearchResultType.ALBUM
import com.stash.feature.search.contract.SearchResultType.ARTIST
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedArtistPhotoUrlTest {
    @Test
    fun `an artist photo is local to its YouTube Music artist identity`() {
        val photos = mapOf("UCtwo" to SelectedArtistPhoto("file:///photos/manual", ArtistPhotoOrigin.USER))
        val artist = CatalogIdentity(YOUTUBE_MUSIC, "UCtwo")
        assertEquals("file:///photos/manual", displayedSearchArtwork(artist, ARTIST, "original", photos))
        assertEquals("original", displayedSearchArtwork(artist, ALBUM, "original", photos))
        assertEquals("original", displayedSearchArtwork(artist.copy(source = SPOTIFY), ARTIST, "original", photos))
        assertEquals("original", displayedSearchArtwork(artist.copy(catalogId = "UCunknown"), ARTIST, "original", photos))
    }
}
