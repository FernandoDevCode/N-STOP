package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistSectionPositionsTest {
    @Test fun `all populated shelves preserve album and singles positions`() {
        val sections = artistSectionPositions(
            hasSongs = true, hasAlbums = true, hasSingles = true,
            hasRelated = true, hasAbout = true,
        )
        assertEquals(2, sections.songs)
        assertEquals(4, sections.albums)
        assertEquals(6, sections.singles)
        assertEquals(10, sections.about)
        assertEquals(listOf(2, 4, 6, 10), sections.available())
    }

    @Test fun `missing tracks and singles do not shift album deep links`() {
        val sections = artistSectionPositions(
            hasSongs = false, hasAlbums = true, hasSingles = false,
            hasRelated = false, hasAbout = true,
        )
        assertNull(sections.songs)
        assertEquals(2, sections.albums)
        assertNull(sections.singles)
        assertEquals(4, sections.about)
    }

    @Test fun `empty catalog does not create dead navigation tabs`() {
        val sections = artistSectionPositions(
            hasSongs = false, hasAlbums = false, hasSingles = false,
            hasRelated = false, hasAbout = false,
        )
        assertEquals(emptyList<Int>(), sections.available())
    }
}
