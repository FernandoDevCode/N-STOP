package com.stash.feature.search

import com.stash.data.ytmusic.model.AlbumSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistFeaturedAlbumSelectionTest {
    private fun album(id: String, cover: String? = null, tracks: Int? = null) =
        AlbumSummary(
            id = id,
            title = "Album $id",
            artist = "Artist",
            thumbnailUrl = cover,
            year = "2024",
            trackCount = tracks,
        )

    @Test fun `select first album with cover and multiple tracks`() {
        val result = selectArtistFeaturedAlbum(
            albums = listOf(
                album("no-cover", tracks = 10),
                album("single", cover = "single.jpg", tracks = 1),
                album("full", cover = "full.jpg", tracks = 12),
                album("other", cover = "other.jpg", tracks = 10),
            ),
            singles = emptyList(),
        )
        assertEquals("full", result?.id)
    }

    @Test fun `fallback to album without cover before singles`() {
        assertEquals(
            "album",
            selectArtistFeaturedAlbum(listOf(album("album")), listOf(album("single", "s.jpg")))?.id,
        )
    }

    @Test fun `singles are used only when albums are absent`() {
        assertEquals(
            "single",
            selectArtistFeaturedAlbum(emptyList(), listOf(album("single", "s.jpg")))?.id,
        )
    }

    @Test fun `empty catalog leaves featured card absent`() {
        assertNull(selectArtistFeaturedAlbum(emptyList(), emptyList()))
    }
}
