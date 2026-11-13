package com.stash.core.media.streaming

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.media.diagnostics.PlaybackDiagnosticsLog
import com.stash.data.download.lossless.LosslessSourcePreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Sonora-approved streaming policy: YouTube is the only audio resolver.
 * Spotify supplies catalog identities. Excluded Qobuz and JioSaavn resolvers
 * are never called, even if a persisted legacy force flag is enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamSourceRegistryTest {
    private val kennyy: KennyyStreamResolver = mockk()
    private val qobuz: QobuzStreamResolver = mockk()
    private val qbdlx: QbdlxStreamResolver = mockk()
    private val jiosaavn: JioSaavnStreamResolver = mockk()
    private val youtube: YouTubeStreamResolver = mockk()
    private val streamingPreference: StreamingPreference = mockk {
        coEvery { isForceQbdlxOnly() } returns false
        coEvery { isForceYouTubeFallback() } returns false
    }
    private val losslessPrefs: LosslessSourcePreferences = mockk {
        coEvery { enabledNow() } returns true
    }
    private val log = PlaybackDiagnosticsLog()

    private fun registry(dispatcher: CoroutineDispatcher = Dispatchers.IO) = StreamSourceRegistry(
        kennyy, qobuz, qbdlx, jiosaavn, youtube, streamingPreference,
        LosslessSourceHealth(), losslessPrefs, dispatcher, log,
    )

    private fun track() = TrackEntity(
        id = 1L,
        title = "Secret Song",
        artist = "Artist",
        album = "Album",
        durationMs = 200_000L,
        youtubeId = "abc123",
    )

    private fun stream() = StreamUrl(
        url = "https://example.test/audio.opus",
        expiresAtMs = Long.MAX_VALUE,
        codec = "opus",
        origin = "youtube",
    )

    private fun verifyNoExcludedSources() {
        coVerify(exactly = 0) { qbdlx.resolve(any()) }
        coVerify(exactly = 0) { kennyy.resolve(any()) }
        coVerify(exactly = 0) { qobuz.resolve(any()) }
        coVerify(exactly = 0) { jiosaavn.resolve(any()) }
    }

    @Test
    fun `youtube is the only streaming provider in normal mode`() = runTest {
        coEvery { youtube.resolve(any(), any()) } returns stream()
        val result = registry().resolve(track())
        assertThat(result?.origin).isEqualTo("youtube")
        coVerify(exactly = 1) { youtube.resolve(any(), allowYtDlp = true) }
        verifyNoExcludedSources()
    }

    @Test
    fun `background fill forwards fast-only resolver flag`() = runTest {
        coEvery { youtube.resolve(any(), any()) } returns stream()
        assertThat(registry().resolve(track(), allowYtDlp = false)?.origin).isEqualTo("youtube")
        coVerify(exactly = 1) { youtube.resolve(any(), allowYtDlp = false) }
        coVerify(exactly = 0) { youtube.resolve(any(), allowYtDlp = true) }
        verifyNoExcludedSources()
    }

    @Test
    fun `legacy force-Qobuz preference cannot revive retired provider`() = runTest {
        coEvery { streamingPreference.isForceQbdlxOnly() } returns true
        coEvery { youtube.resolve(any(), any()) } returns stream()
        assertThat(registry().resolve(track())?.origin).isEqualTo("youtube")
        coVerify(exactly = 1) { youtube.resolve(any(), any()) }
        verifyNoExcludedSources()
    }

    @Test
    fun `old lossless preference cannot activate Qobuz`() = runTest {
        coEvery { losslessPrefs.enabledNow() } returns true
        coEvery { youtube.resolve(any(), any()) } returns stream()
        assertThat(registry().resolve(track())?.origin).isEqualTo("youtube")
        verifyNoExcludedSources()
    }

    @Test
    fun `legacy force-YouTube preference does not change source list`() = runTest {
        coEvery { streamingPreference.isForceYouTubeFallback() } returns true
        coEvery { youtube.resolve(any(), any()) } returns stream()
        assertThat(registry().resolve(track())?.origin).isEqualTo("youtube")
        coVerify(exactly = 1) { youtube.resolve(any(), any()) }
        verifyNoExcludedSources()
    }

    @Test
    fun `disabled youtube permission leaves all providers unqueried`() = runTest {
        val result = registry().resolve(track(), allowYouTube = false)
        assertThat(result).isNull()
        coVerify(exactly = 0) { youtube.resolve(any(), any()) }
        verifyNoExcludedSources()
    }

    @Test
    fun `youtube miss is recorded as no source available`() = runTest {
        coEvery { youtube.resolve(any(), any()) } returns null
        assertThat(registry().resolve(track())).isNull()
        assertThat(log.recentResolves().single().servedBy).isEqualTo("none (all missed)")
        verifyNoExcludedSources()
    }

    @Test
    fun `youtube response is logged without leaking the track title or URL`() = runTest {
        coEvery { youtube.resolve(any(), any()) } returns stream()
        registry().resolve(track())
        val record = log.recentResolves().single()
        assertThat(record.trackId).isEqualTo(1L)
        assertThat(record.servedBy).isEqualTo("youtube")
        assertThat(record.lossless).isFalse()
        assertThat(record.toString()).doesNotContain("Secret Song")
        assertThat(record.toString()).doesNotContain("example.test")
    }

    @Test
    fun `resolver bugs are treated as misses rather than app crashes`() = runTest {
        coEvery { youtube.resolve(any(), any()) } throws IllegalStateException("obsolete token")
        assertThat(registry().resolve(track())).isNull()
        assertThat(log.recentResolves().single().servedBy).isEqualTo("none (all missed)")
    }

    @Test
    fun `user cancellation is propagated and never recorded as a miss`() = runTest {
        coEvery { youtube.resolve(any(), any()) } throws CancellationException("newer tap")
        val outcome = runCatching { registry().resolve(track()) }
        assertThat(outcome.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(log.recentResolves()).isEmpty()
    }

    @Test
    fun `youtube timeout is reported as a miss not a cancellation`() = runTest {
        coEvery { youtube.resolve(any(), any()) } coAnswers {
            delay(LazyResolvingDataSource.RESOLVE_DEADLINE_MS * 2)
            null
        }
        val outcome = runCatching {
            registry(StandardTestDispatcher(testScheduler)).resolve(track())
        }
        assertThat(outcome.exceptionOrNull()).isNull()
        assertThat(outcome.getOrNull()).isNull()
        assertThat(log.recentResolves().single().servedBy).isEqualTo("none (timed out)")
        verifyNoExcludedSources()
    }
}
