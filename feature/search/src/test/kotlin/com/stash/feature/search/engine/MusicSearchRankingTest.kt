package com.stash.feature.search.engine

import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchResultType.ALBUM
import com.stash.feature.search.contract.SearchResultType.ARTIST
import com.stash.feature.search.contract.SearchResultType.TRACK
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchRankingTest {
    @Test
    fun `exact artist supported by its catalog wins against an incidental title and similar names`() {
        val incidental = hit("podium", TRACK, "Alex Terrible", 0)
        val similar = hit("similar", ARTIST, "Alex Terrible Tribute", 1)
        val solo = hit("solo", ARTIST, "Alex Terrible", 7)
        val credit = hit("track", TRACK, "Doom Slayer", 3, listOf("Alex Terrible"), listOf("solo"))
        val candidates = listOf(incidental, similar, solo, credit)
        assertEquals("podium", candidates.maxBy { it.rankingScore }.identity.catalogId)
        assertEquals(listOf("solo", "podium", "similar", "track"), ordered("alex terrible", candidates).ids())
        assertEquals("solo", ordered("Alex Terr", candidates).first().identity.catalogId)
    }

    @Test
    fun `band names survive case accents and punctuation while symbols still distinguish different bands`() {
        val variants = listOf(
            hit("plain", ARTIST, "ACDC", 1),
            hit("tribute", ARTIST, "AC/DC Tribute Band", 4),
            hit("original", ARTIST, "AC/DC", 10),
        )
        assertEquals(listOf("original", "plain", "tribute"), ordered("ac/dc", variants).ids())
        assertEquals("plain", ordered("acdc", variants).first().identity.catalogId)
        val accented = listOf(
            hit("short", ARTIST, "João", 1),
            hit("full", ARTIST, "João Gilberto", 4),
        )
        assertEquals("full", ordered(" JOAO   gil ", accented).first().identity.catalogId)
        assertEquals("full", ordered("joao gilberto", accented).first().identity.catalogId)
        val bands = listOf(
            hit("tribute", ARTIST, "Beatles Tribute Band", 1),
            hit("beatles", ARTIST, "The Beatles", 4),
            hit("song", TRACK, "Something", 6, listOf("The Beatles"), listOf("beatles")),
        )
        assertEquals("beatles", ordered("beatl", bands).first().identity.catalogId)
    }

    @Test
    fun `song titles preserve homonyms and versions and respect original order for equally strong names`() {
        val songs = listOf(
            hit("remix", TRACK, "Hello (Remix)", 0, listOf("Adele")),
            hit("lionel", TRACK, "Hello", 3, listOf("Lionel Richie")),
            hit("adele", TRACK, "Hello", 6, listOf("Adele")),
            hit("partial", TRACK, "Hello Goodbye", 9, listOf("The Beatles")),
        )
        assertEquals(listOf("lionel", "adele", "remix", "partial"), ordered("hello", songs).ids())
        // With only a shared prefix, the provider's ordering is the evidence available.
        assertEquals(listOf("remix", "lionel", "adele", "partial"), ordered("hel", songs).ids())
        assertEquals("remix", ordered("hello rem", songs).first().identity.catalogId)
        assertEquals("adele", ordered("Adele hel", songs).first().identity.catalogId)
        assertEquals("lionel", ordered("hello lionel", songs).first().identity.catalogId)
    }

    @Test
    fun `album intent and single editions remain distinct from their songs`() {
        val track = hit("track", TRACK, "The Suburbs", 0, listOf("Arcade Fire"))
        val album = hit("album", ALBUM, "The Suburbs", 2, listOf("Arcade Fire"))
            .copy(release = SearchReleaseMetadata("The Suburbs", "2010", "album"))
        val single = hit("single", ALBUM, "The Suburbs / Month of May", 5, listOf("Arcade Fire"))
            .copy(release = SearchReleaseMetadata("The Suburbs / Month of May", "2010", "single"))
        val related = hit("related", TRACK, "Ready to Start", 3, listOf("Arcade Fire")).copy(release = album.release)
        val all = listOf(track, album, single, related)
        assertEquals(listOf("track", "album", "single", "related"), ordered("the suburbs", all).ids())
        val selected = ordered("THE SUBURBS month of", all).first()
        assertEquals(single.identity, selected.identity)
        assertEquals(ALBUM, selected.type)
        assertEquals("single", selected.release?.type)
        assertTrue(ordered("the suburbs", all).single { it.identity == album.identity }.rankingScore >
            ordered("the suburbs", all).single { it.identity == related.identity }.rankingScore)
    }

    @Test
    fun `a title plus a credited artist beats an ambiguous exact title and lead credit breaks collaboration ties`() {
        val wrong = hit("literal", TRACK, "Flow Kendrick Lamar", 0, listOf("Another Artist"))
        val guest = hit("guest", TRACK, "Flow", 3, listOf("Tems", "Kendrick Lamar"))
        val lead = hit("lead", TRACK, "Flow", 6, listOf("Kendrick Lamar", "Tems"))
        val band = hit("artist", ARTIST, "Kendrick Lamar", 7)
        assertEquals(listOf("lead", "guest", "literal", "artist"), ordered("flow Kendrick Lamar", listOf(wrong, guest, lead, band)).ids())
        assertEquals(listOf("artist", "lead", "guest"), ordered("Kendrick Lamar", listOf(guest, lead, band)).ids())
    }

    @Test
    fun `equivalent input order produces stable homonym ties and unrelated hits are retained`() {
        val a = hit("a", TRACK, "Dreams", 0, listOf("Fleetwood Mac"))
        val b = hit("b", TRACK, "Dreams", 0, listOf("The Cranberries"))
        val alternative = hit("alt", TRACK, "Sweet Dreams", 3)
        val unexpected = hit("provider", TRACK, "Provider suggestion", 6)
        val expected = listOf("a", "b", "alt", "provider")
        assertEquals(expected, ordered("dreams", listOf(b, unexpected, alternative, a)).ids())
        assertEquals(expected, ordered(" DREAMS ", listOf(a, alternative, b, unexpected)).ids())
    }

    private fun ordered(query: String, candidates: List<UniversalSearchResult>): List<UniversalSearchResult> =
        UniversalSearchEngine.aggregate(
            query, SearchSourceSelection.Default,
            mapOf(CatalogSource.YOUTUBE_MUSIC to SourceSearchState.Data(query, rankMusicSearch(query, candidates))),
        ).orderedResults

    private fun List<UniversalSearchResult>.ids() = map { it.identity.catalogId }

    private fun hit(
        id: String,
        type: SearchResultType,
        title: String,
        rank: Int,
        artists: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ) = UniversalSearchResult(
        CatalogIdentity(CatalogSource.YOUTUBE_MUSIC, id), type = type, title = title,
        providerRank = rank, rankingScore = ordinalSearchScore(rank),
        artistNames = artists, artistIds = artistIds,
    )
}
