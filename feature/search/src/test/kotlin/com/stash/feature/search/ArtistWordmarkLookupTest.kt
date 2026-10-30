package com.stash.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistWordmarkLookupTest {
    @Test fun `artist names must match exactly and case insensitively`() {
        assertTrue(ArtistWordmarkLookup.matchingArtist("Megadeth", "MEGADETH"))
        assertTrue(ArtistWordmarkLookup.matchingArtist("  U2", "U2  "))
        assertFalse(ArtistWordmarkLookup.matchingArtist("Megadeth", "Megadeth Tribute"))
        assertFalse(ArtistWordmarkLookup.matchingArtist("The Weeknd", "Weeknd"))
        assertFalse(ArtistWordmarkLookup.matchingArtist("U2", "U2 Cover Band"))
    }
}
