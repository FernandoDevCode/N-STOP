package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyCatalogItem
import com.stash.data.spotify.SpotifyCatalogPage
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.CatalogSource.SPOTIFY
import com.stash.feature.search.contract.CatalogSource.YOUTUBE
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.SearchReleaseMetadata
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UniversalSearchResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UniversalSearchSessionEngineTest {
    @Test
    fun `YT Music and YouTube are independent and no Spotify search is required`() = runTest {
        var musicCalls = 0
        var webCalls = 0
        val song = result(YOUTUBE_MUSIC, "song")
        val video = result(YOUTUBE, "video")
        val session = session(setOf(SPOTIFY), web = { webCalls++; listOf(video) },
            music = { musicCalls++; listOf(song, song) })
        query(session, "slipknot")
        assertEquals(1, musicCalls)
        assertEquals(1, webCalls)
        assertEquals(setOf(YOUTUBE_MUSIC, YOUTUBE), session.state.value.orderedResults.map { it.identity.source }.toSet())
        assertEquals(listOf(song), session.state.value.orderedResults.filter { it.identity.source == YOUTUBE_MUSIC })
        assertEquals(SourceSearchState.Idle, session.state.value.sourceStates[SPOTIFY])
    }

    @Test
    fun `YT Music failure never suppresses independent YouTube video results`() = runTest {
        val video = result(YOUTUBE, "web")
        val session = session(setOf(SPOTIFY), web = { listOf(video) },
            music = { throw IOException("offline") })
        query(session, "slipknot")
        assertEquals(listOf(video), session.state.value.orderedResults)
        assertTrue(session.state.value.hasPartialSuccess)
        assertTrue(session.state.value.sourceStates[YOUTUBE_MUSIC] is SourceSearchState.Error)
    }

    @Test
    fun `error retry and timeout belong to one real catalog and keep partial results`() = runTest {
        var musicRequests = 0
        var webRequests = 0
        val session = session(setOf(SPOTIFY, YOUTUBE_MUSIC, YOUTUBE),
            web = { webRequests++; listOf(result(YOUTUBE, "web")) },
            music = {
                if (++musicRequests == 1) throw IOException("offline")
                awaitCancellation()
            })
        query(session, "echoes")
        assertTrue(session.state.value.hasPartialSuccess)
        assertEquals(1, session.state.value.sourceStates.values.count { it is SourceSearchState.Error })
        session.onRetrySource(SPOTIFY)
        session.onRetrySource(YOUTUBE_MUSIC)
        session.onRetrySource(YOUTUBE_MUSIC)
        runCurrent()
        assertEquals(2, musicRequests)
        assertEquals(1, webRequests)
        assertTrue((session.state.value.sourceStates[YOUTUBE_MUSIC] as SourceSearchState.Loading).isRetry)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(session.state.value.hasPartialSuccess)
        assertEquals(YOUTUBE, session.state.value.orderedResults.single().identity.source)
    }

    @Test
    fun `stale work cannot update a newer query even for the same catalog ID`() = runTest {
        val stale = CompletableDeferred<Unit>()
        var attempts = 0
        val hit = result(YOUTUBE_MUSIC, "same").copy(release = SearchReleaseMetadata("Verified album"))
        val session = session(setOf(SPOTIFY), music = { listOf(hit) }, artwork = {
            attempts++
            withContext(NonCancellable) { stale.await() }
            "https://i.scdn.co/image/old"
        })
        query(session, "old")
        assertEquals(listOf(hit), session.state.value.orderedResults)
        session.onVisibleResultsChanged("old", setOf(hit.stableKey))
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(session.state.value.artworkOverrides.isEmpty())
        session.onQueryChanged("new")
        assertTrue(session.state.value.orderedResults.isEmpty())
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(hit), session.state.value.orderedResults)
        session.onVisibleResultsChanged("old", setOf(hit.stableKey))
        stale.complete(Unit)
        runCurrent()
        assertTrue(session.state.value.artworkOverrides.isEmpty())
        assertEquals(1, attempts)
    }

    @Test
    fun `only visible results are enriched and revisits reuse the positive and negative outcomes`() = runTest {
        val hits = (1..6).map {
            result(YOUTUBE_MUSIC, "id$it").copy(release = SearchReleaseMetadata("Verified album"))
        }
        val attempts = mutableListOf<UniversalSearchResult>()
        val session = session(setOf(SPOTIFY), music = { hits }, artwork = {
            attempts += it
            if (it == hits[1]) null else "https://i.scdn.co/image/" + it.identity.catalogId
        })
        query(session, "echoes")
        assertTrue(attempts.isEmpty())
        val initial = setOf(hits[0].stableKey, hits[1].stableKey)
        session.onVisibleResultsChanged("echoes", initial)
        runCurrent()
        assertEquals(hits.take(2), attempts)
        session.onVisibleResultsChanged("echoes", setOf(hits[5].stableKey))
        runCurrent()
        session.onVisibleResultsChanged("echoes", initial)
        session.onVisibleResultsChanged("echoes", initial)
        runCurrent()
        assertEquals(listOf(hits[0], hits[1], hits[5]), attempts)
        assertEquals(hits, session.state.value.orderedResults)
        assertEquals(setOf(hits[0].stableKey, hits[5].stableKey), session.state.value.artworkOverrides.keys)
    }

    @Test
    fun `one in flight lookup takes the next candidate from the latest viewport`() = runTest {
        val hits = (1..6).map {
            result(YOUTUBE_MUSIC, "id$it").copy(release = SearchReleaseMetadata("Verified album"))
        }
        val release = CompletableDeferred<Unit>()
        val attempts = mutableListOf<UniversalSearchResult>()
        val session = session(setOf(SPOTIFY), music = { hits }, artwork = {
            attempts += it
            if (it == hits[0]) release.await()
            null
        })
        query(session, "echoes")
        session.onVisibleResultsChanged("echoes", setOf(hits[0].stableKey, hits[1].stableKey))
        runCurrent()
        session.onVisibleResultsChanged("echoes", setOf(hits[5].stableKey))
        runCurrent()
        assertEquals(listOf(hits[0]), attempts)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(hits[0], hits[5]), attempts)
    }

    @Test
    fun `Spotify unavailability leaves the Music catalogue and original image available`() = runTest {
        val hit = result(YOUTUBE_MUSIC, "real-id").copy(release = SearchReleaseMetadata("Verified album"))
        val lookup = SearchArtworkLookup(
            search = { _, _ -> throw SpotifyApiException(401, "search", "Unavailable") },
            now = { testScheduler.currentTime },
        )
        val session = session(setOf(SPOTIFY), music = { listOf(hit) }, artwork = lookup::find)
        query(session, "echoes")
        session.onVisibleResultsChanged("echoes", setOf(hit.stableKey))
        runCurrent()
        assertEquals(listOf(hit), session.state.value.orderedResults)
        assertTrue(session.state.value.artworkOverrides.isEmpty())
        assertEquals(SourceSearchState.Data("echoes", listOf(hit)), session.state.value.sourceStates[YOUTUBE_MUSIC])
    }

    private fun TestScope.query(session: UniversalSearchSession, query: String) {
        session.onQueryChanged(query)
        advanceTimeBy(300)
        runCurrent()
    }

    private fun TestScope.session(
        enabled: Set<CatalogSource>,
        web: CatalogSearch = { emptyList() },
        music: CatalogSearch = { emptyList() },
        artwork: suspend (UniversalSearchResult) -> String? = { null },
    ): UniversalSearchSession {
        val history = mock<UniversalSearchHistory>()
        whenever(history.recent).thenReturn(flowOf(emptyList()))
        return UniversalSearchSession(
            backgroundScope, mapOf(YOUTUBE to web, YOUTUBE_MUSIC to music),
            SearchSourceSelection(enabled), {}, history, {}, {}, timeoutMs = 1_000, artwork = artwork,
        )
    }

    private fun result(source: CatalogSource, id: String) = UniversalSearchResult(
        identity = CatalogIdentity(source, id),
        type = if (source == YOUTUBE) SearchResultType.VIDEO else SearchResultType.TRACK,
        title = "Echoes", subtitle = "Pink Floyd", artworkUrl = "original", durationMs = 300_000,
        providerRank = 0, rankingScore = 1.0,
    )
}
