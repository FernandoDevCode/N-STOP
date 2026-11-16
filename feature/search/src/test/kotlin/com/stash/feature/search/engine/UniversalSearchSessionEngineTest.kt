package com.stash.feature.search.engine

import com.stash.data.spotify.SpotifyApiException
import com.stash.data.spotify.SpotifyRateLimitException
import com.stash.data.ytmusic.YouTubeWebVideo
import com.stash.feature.search.contract.CatalogIdentity
import com.stash.feature.search.contract.CatalogSource
import com.stash.feature.search.contract.CatalogSource.SPOTIFY
import com.stash.feature.search.contract.CatalogSource.YOUTUBE
import com.stash.feature.search.contract.CatalogSource.YOUTUBE_MUSIC
import com.stash.feature.search.contract.SearchResultAction
import com.stash.feature.search.contract.SearchResultType
import com.stash.feature.search.contract.SearchSourceSelection
import com.stash.feature.search.contract.SourceSearchState
import com.stash.feature.search.contract.UnavailableReason
import com.stash.feature.search.contract.UniversalSearchResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UniversalSearchSessionEngineTest {
    @Test
    fun `filters are independent and an empty selection makes no calls`() = runTest {
        val calls = mutableListOf<CatalogSource>()
        val session = session(CatalogSource.entries.associateWith { source ->
            { _: String -> calls.add(source); listOf(result(source)) }
        })
        assertEquals(setOf(SPOTIFY, YOUTUBE), session.state.value.selection.enabled)
        session.onSourceToggled(SPOTIFY)
        session.onSourceToggled(YOUTUBE)
        runQuery(session, "cover")
        assertTrue(calls.isEmpty())
        assertTrue(session.state.value.orderedResults.isEmpty())
        assertTrue(session.state.value.sourceStates.values.all { it == SourceSearchState.Idle })

        session.onSourceToggled(YOUTUBE_MUSIC)
        runCurrent()
        assertEquals(listOf(YOUTUBE_MUSIC), calls)
        session.onSourceToggled(YOUTUBE)
        runCurrent()
        assertEquals(listOf(YOUTUBE_MUSIC, YOUTUBE), calls)
        assertEquals(listOf(YOUTUBE, YOUTUBE_MUSIC), session.state.value.orderedResults.map { it.identity.source })
        session.onSourceToggled(YOUTUBE)
        assertEquals(listOf(YOUTUBE_MUSIC), session.state.value.orderedResults.map { it.identity.source })
        assertEquals(SourceSearchState.Idle, session.state.value.sourceStates[YOUTUBE])
    }

    @Test
    fun `typing clears old results immediately and a noncooperative request stays canceled`() = runTest {
        val stale = CompletableDeferred<Unit>()
        val session = session(
            mapOf(
                YOUTUBE to { query ->
                    if (query == "old") withContext(NonCancellable) { stale.await() }
                    listOf(result(YOUTUBE, query))
                },
                SPOTIFY to { query -> listOf(result(SPOTIFY, query)) },
            ),
        )
        runQuery(session, "old")
        assertEquals(listOf("old"), session.state.value.orderedResults.map { it.identity.catalogId })
        session.onQueryChanged("new")
        assertTrue(session.state.value.orderedResults.isEmpty())
        stale.complete(Unit)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("new", "new"), session.state.value.orderedResults.map { it.identity.catalogId })
        assertTrue(session.state.value.sourceStates.values.none {
            it is SourceSearchState.Data && it.query != "new"
        })
    }

    @Test
    fun `timeout and provider errors preserve partial success and retry only their source`() = runTest {
        val calls = mutableListOf<CatalogSource>()
        var recover = false
        var timedOutCallCanceled = false
        val session = session(
            mapOf(
                SPOTIFY to {
                    calls.add(SPOTIFY)
                    if (recover) listOf(result(SPOTIFY)) else throw IOException()
                },
                YOUTUBE to { calls.add(YOUTUBE); listOf(result(YOUTUBE)) },
                YOUTUBE_MUSIC to {
                    calls.add(YOUTUBE_MUSIC)
                    try {
                        awaitCancellation()
                    } finally {
                        timedOutCallCanceled = true
                    }
                },
            ),
        )
        session.onSourceToggled(YOUTUBE_MUSIC)
        runQuery(session, "live")
        val original = session.state.value.orderedResults.single()
        assertEquals(YOUTUBE, original.identity.source)
        assertTrue(session.state.value.hasPartialSuccess)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(timedOutCallCanceled)
        val timeout = session.state.value.sourceStates[YOUTUBE_MUSIC] as SourceSearchState.Error
        assertTrue(timeout.canRetry)
        recover = true
        session.onRetrySource(SPOTIFY)
        val retry = session.state.value.sourceStates[SPOTIFY] as SourceSearchState.Loading
        assertTrue(retry.isRetry)
        assertTrue(retry.previous.isEmpty())
        assertEquals(listOf(original), session.state.value.orderedResults)
        runCurrent()
        assertEquals(listOf(SPOTIFY, YOUTUBE, YOUTUBE_MUSIC, SPOTIFY), calls)
        assertEquals(timeout, session.state.value.sourceStates[YOUTUBE_MUSIC])
    }

    @Test
    fun `signed out Spotify is unavailable and connection delegates the existing action`() = runTest {
        var connects = 0
        var authenticated = false
        val session = session(
            mapOf(SPOTIFY to {
                if (!authenticated) throw SpotifyApiException(0, "search", "signed out")
                listOf(result(SPOTIFY))
            }),
            connectSpotify = { connects++ },
        )
        runQuery(session, "demo")
        assertEquals(
            SourceSearchState.Unavailable("demo", UnavailableReason.AUTHENTICATION_REQUIRED),
            session.state.value.sourceStates[SPOTIFY],
        )
        session.onRetrySource(SPOTIFY)
        session.onConnectSource(YOUTUBE)
        session.onConnectSource(SPOTIFY)
        assertEquals(1, connects)
        authenticated = true
        session.onSpotifyConnected()
        runCurrent()
        assertEquals(SPOTIFY, session.state.value.orderedResults.single().identity.source)
    }

    @Test
    fun `Spotify retry-after gates both retry and a different query`() = runTest {
        var calls = 0
        val session = session(mapOf(SPOTIFY to {
            calls++
            throw SpotifyRateLimitException(2)
        }))
        runQuery(session, "first")
        assertFalse((session.state.value.sourceStates[SPOTIFY] as SourceSearchState.Error).canRetry)
        session.onRetrySource(SPOTIFY)
        runQuery(session, "second")
        assertEquals(1, calls)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue((session.state.value.sourceStates[SPOTIFY] as SourceSearchState.Error).canRetry)
        session.onRetrySource(SPOTIFY)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `interleaves catalogs and preserves equal looking versions with different IDs`() = runTest {
        val demo = YouTubeWebVideo("JOvAJJI2Ui8", "Megadeth - Hangar 18 (Demo)", null, null, null, 0)
            .toUniversalSearchResult()
        val cover = demo.copy(
            identity = CatalogIdentity(YOUTUBE, "MoL0u4fxUW4"), title = "Hangar 18 (Cover)",
            versionLabel = "Cover", providerRank = 1, rankingScore = 0.5,
        )
        val otherSource = result(YOUTUBE_MUSIC, demo.identity.catalogId)
        val session = session(
            mapOf(
                SPOTIFY to { listOf(result(SPOTIFY)) },
                YOUTUBE to { listOf(demo, demo.copy(title = "Duplicate metadata"), cover) },
                YOUTUBE_MUSIC to { delay(50); listOf(otherSource) },
            ),
            navigate = { error("Media search results have no executable action") },
        )
        session.onSourceToggled(YOUTUBE_MUSIC)
        runQuery(session, "hangar 18")
        advanceTimeBy(50)
        runCurrent()
        val hits = session.state.value.orderedResults
        assertEquals(listOf(SPOTIFY, YOUTUBE, YOUTUBE_MUSIC, YOUTUBE), hits.map { it.identity.source })
        assertEquals(listOf(demo, otherSource, cover), hits.drop(1))
        assertEquals("Demo", demo.versionLabel)
        assertEquals(2, (session.state.value.sourceStates[YOUTUBE] as SourceSearchState.Data).items.size)
        hits.forEach {
            assertNull(it.audioIdentity)
            assertTrue(it.supportedActions.isEmpty())
            session.onResultAction(it, SearchResultAction.PLAY)
        }
    }

    private fun TestScope.runQuery(session: UniversalSearchSession, query: String) {
        session.onQueryChanged(query)
        advanceTimeBy(300)
        runCurrent()
    }

    private fun TestScope.session(
        providers: Map<CatalogSource, CatalogSearch>,
        connectSpotify: () -> Unit = {},
        navigate: (UniversalSearchResult) -> Unit = {},
    ): UniversalSearchSession {
        val history = mock<UniversalSearchHistory>()
        whenever(history.recent).thenReturn(flowOf(emptyList()))
        return UniversalSearchSession(
            backgroundScope,
            CatalogSource.entries.associateWith { source -> providers[source] ?: { emptyList() } },
            SearchSourceSelection.Default, {}, history, connectSpotify, navigate, {},
            timeoutMs = 1_000, nowMs = { testScheduler.currentTime },
        )
    }

    private fun result(source: CatalogSource, id: String = source.name) = UniversalSearchResult(
        identity = CatalogIdentity(source, id),
        type = if (source == YOUTUBE) SearchResultType.VIDEO else SearchResultType.TRACK,
        title = "Hangar 18",
        providerRank = 0,
        rankingScore = 1.0,
    )
}
