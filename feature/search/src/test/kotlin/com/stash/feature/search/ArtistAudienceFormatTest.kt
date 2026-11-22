package com.stash.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistAudienceFormatTest {
    @Test fun `monthly audience abbreviations use Brazilian notation`() {
        assertEquals("Público mensal · 3,2 mi", artistMonthlyAudienceLabel("3.2M monthly audience"))
        assertEquals("Público mensal · 18 mil", artistMonthlyAudienceLabel("18K monthly audience"))
        assertEquals("Público mensal · 1,5 bi", artistMonthlyAudienceLabel("1.5B monthly audience"))
        assertEquals("Público mensal · 12,3 mil", artistMonthlyAudienceLabel("12,345 monthly audience"))
        assertEquals("Público mensal · 140", artistMonthlyAudienceLabel("140 monthly audience"))
    }

    @Test fun `no estimate when actual monthly audience is absent`() {
        assertNull(artistMonthlyAudienceLabel(null))
        assertNull(artistMonthlyAudienceLabel(""))
        assertNull(artistMonthlyAudienceLabel("Subscribers 10M"))
        assertNull(artistMonthlyAudienceLabel("Artist"))
    }
}
