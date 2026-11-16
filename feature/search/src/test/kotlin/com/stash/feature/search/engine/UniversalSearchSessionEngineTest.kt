package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiException
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
    fun `either music filter or both route only once to YouTube Music`() = runTest {
        for (enabled in listOf(setOf(SPOTIFY), setOf(YOUTUBE_MUSIC), setOf(SPOTIFY, YOUTUBE_MUSIC))) {
            var requests = 0
            val answer = CompletableDeferred<Unit>()
            val hit = result(YOUTUBE_MUSIC, "real-video")
            val session = session(enabled, music = { requests++; answer.await(); listOf(hit, hit) })
            query(session, "echoes")
            assertEquals(1, requests)
            assertEquals(1, session.state.value.sourceStates.values.count { it is SourceSearchState.Loading })
            answer.complete(Unit)
            runCurrent()
            assertEquals(listOf(hit), session.state.value.orderedResults)
            assertEquals("YOUTUBE_MUSIC:TRACK:real-video", hit.stableKey)
        }
    }

    @Test
    fun `toggling an alias keeps the running request until the last alias is removed`() = runTest {
        var requests = 0
        var canceled = false
        val session = session(setOf(SPOTIFY), music = {
            requests++
            try {
                awaitCancellation()
            } finally {
                canceled = true
            }
        })
        query(session, "echoes")
        session.onSourceToggled(YOUTUBE_MUSIC)
        session.onSourceToggled(SPOTIFY)
        runCurrent()
        assertFalse(canceled)
        assertEquals(1, requests)
        session.onSourceToggled(YOUTUBE_MUSIC)
        runCurrent()
        assertTrue(canceled)
        assertTrue(session.state.value.selection.enabled.isEmpty())
        assertTrue(session.state.value.sourceStates.values.all { it == SourceSearchState.Idle })
    }

    @Test
    fun `empty and WEB only selections never query Music`() = runTest {
        var webRequests = 0
        val session = session(emptySet(), web = { webRequests++; listOf(result(YOUTUBE, "web")) },
            music = { error("Music is disabled") })
        query(session, "echoes")
        assertEquals(0, webRequests)
        session.onSourceToggled(YOUTUBE)
        runCurrent()
        assertEquals(1, webRequests)
        assertEquals(YOUTUBE, session.state.value.orderedResults.single().identity.source)
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
        val hit = result(YOUTUBE_MUSIC, "same").copy(release = SearchReleaseMetadata("Verified album"))
        val session = session(setOf(SPOTIFY), music = { listOf(hit) }, artwork = {
            withContext(NonCancellable) { stale.await() }
            "https://i.scdn.co/image/old"
        })
        query(session, "old")
        assertEquals(listOf(hit), session.state.value.orderedResults)
        assertTrue(session.state.value.artworkOverrides.isEmpty())
        session.onQueryChanged("new")
        assertTrue(session.state.value.orderedResults.isEmpty())
        stale.complete(Unit)
        runCurrent()
        assertTrue(session.state.value.artworkOverrides.isEmpty())
    }

    @Test
    fun `artwork leaves the ordered results intact and spends at most four lookups`() = runTest {
        val hits = (1..6).map {
            result(YOUTUBE_MUSIC, "id$it").copy(release = SearchReleaseMetadata("Verified album"))
        }
        val session = session(setOf(SPOTIFY), music = { hits }, artwork = {
            "https://i.scdn.co/image/" + it.identity.catalogId
        })
        query(session, "echoes")
        assertEquals(hits, session.state.value.orderedResults)
        assertEquals(hits.take(4).map { it.stableKey }.toSet(), session.state.value.artworkOverrides.keys)
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
