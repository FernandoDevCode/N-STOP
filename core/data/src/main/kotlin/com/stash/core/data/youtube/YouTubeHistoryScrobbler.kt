package com.stash.core.data.youtube

import android.util.Log
import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthService
import com.stash.core.auth.youtube.YouTubeCookieHelper
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.YouTubeHistoryPreference
import com.stash.data.ytmusic.CanonicalLookup
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YouTubeCredentialUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Abstraction over the single HTTP ping used to record a play in YouTube Music's
 * Watch History. Extracted for testability — tests inject a fake that returns
 * canned HTTP status codes; production uses [OkHttpPingSubmitter].
 *
 * @param url      The `videostatsPlaybackUrl` tracking URL from InnerTube.
 * @param cookies  Full browser cookie string.
 * @param sapiSid  The SAPISID value extracted from [cookies].
 * @return The HTTP response code, or throws on network error.
 */
fun interface PingSubmitter {
    suspend fun submit(url: String, cookies: String, sapiSid: String): Int
}

/**
 * Production [PingSubmitter] backed by the app-wide [OkHttpClient].
 *
 * Issues the SAPISIDHASH-authenticated GET that registers a watch-history
 * event with YT Music. The request shape mirrors `ytmusicapi.add_history_item`
 * and SimpMusic's implementation:
 *
 *   - **GET** (not POST). The `api/stats/playback` endpoint is a tracking-
 *     pixel style GET that reads its state from the query string; the
 *     `videostatsPlaybackUrl.baseUrl` InnerTube returns already contains
 *     `docid` + the signed session id.
 *   - Append **`cpn`** (16-char random content-playback-nonce), **`ver=2`**,
 *     and **`c=WEB_REMIX`** as query params. Without these YT silently
 *     accepts the request (2xx) but never records it in history. This is
 *     what caused the initial "pings marked scrobbled but YT history
 *     stays empty" bug during end-to-end testing.
 *   - Standard SAPISIDHASH auth header with origin `https://music.youtube.com`
 *     (matches the hash computed by [YouTubeCookieHelper.generateAuthHeader]).
 *   - `X-Origin` mirrors `Origin` — real YT Music clients send both.
 *
 * Only a URL that may receive YouTube credentials ([YouTubeCredentialUrl])
 * is pinged: for any other, [submit] throws before a request is built, and
 * no ping is sent. The ping never follows a redirect, so the credentials go
 * only to the URL that was checked.
 */
class OkHttpPingSubmitter @Inject constructor(
    okHttpClient: OkHttpClient,
    private val cookieHelper: YouTubeCookieHelper,
) : PingSubmitter {

    /** The shared client, without redirects (see the class doc). */
    internal val client: OkHttpClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun submit(url: String, cookies: String, sapiSid: String): Int {
        val base = YouTubeCredentialUrl.parse(url)
            ?: throw IllegalArgumentException(
                "Refused to ping ${YouTubeCredentialUrl.describeForLog(url)}: not a YouTube URL",
            )
        val request = Request.Builder()
            .url(withPlaybackParams(base))
            .get()
            .header("Authorization", cookieHelper.generateAuthHeader(sapiSid))
            .header("Cookie", cookies)
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-Goog-AuthUser", "0")
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request).execute().use { it.code }
    }

    /**
     * Adds the three query parameters YT's stats endpoint requires to
     * actually record a play: `cpn` (new 16-char random), `ver=2`, and
     * `c=WEB_REMIX`. The `videostatsPlaybackUrl` baseUrl already contains
     * `docid`, signed state, etc.; we're layering on the per-request fields.
     */
    private fun withPlaybackParams(base: HttpUrl): HttpUrl = base.newBuilder()
        .addQueryParameter("ver", "2")
        .addQueryParameter("c", "WEB_REMIX")
        .addQueryParameter("cpn", generateCpn())
        .build()

    /**
     * 16-char content-playback-nonce using YT's charset. Matches the
     * generation code in `ytmusicapi.mixins.library.add_history_item`.
     */
    private fun generateCpn(): String {
        val sb = StringBuilder(16)
        repeat(16) {
            sb.append(CPN_ALPHABET[Random.nextInt(256) and 63])
        }
        return sb.toString()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        /**
         * 64-character alphabet YT uses for cpn generation (Base64-ish:
         * A-Z, a-z, 0-9, `-`, `_`). The `& 63` in [generateCpn] selects a
         * 6-bit slice from each random byte to index into this string.
         */
        private const val CPN_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    }
}

/**
 * Drains unscrobbled listening events and submits them to YouTube Music's
 * Watch History via the `videostatsPlaybackUrl` tracking endpoint.
 *
 * Structurally parallel to [com.stash.core.data.lastfm.LastFmScrobbler]:
 * a `combine(enabled, ytConnected, pendingCount, disabledReason)` flow
 * triggers [drainQueue] whenever conditions are met.
 *
 * A behavior-driven kill-switch prevents runaway protocol errors from
 * spamming YouTube's API. Five consecutive non-auth failures trip the
 * switch ([YouTubeScrobblerState.setDisabledReason]); the switch resets
 * automatically on the next app update ([maybeClearKillSwitchOnUpdate]).
 */
@Singleton
class YouTubeHistoryScrobbler @Inject constructor(
    private val preference: YouTubeHistoryPreference,
    private val state: YouTubeScrobblerState,
    private val listeningEventDao: ListeningEventDao,
    private val trackDao: TrackDao,
    private val resolver: YtCanonicalResolver,
    private val innerTubeClient: InnerTubeClient,
    private val cookieHelper: YouTubeCookieHelper,
    private val tokenManager: TokenManager,
    private val pingSubmitter: PingSubmitter,
    /** Indirection over `BuildConfig.VERSION_CODE` — allows tests to control the value. */
    private val versionCodeProvider: () -> Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _health = MutableStateFlow(YouTubeScrobblerHealth.DISABLED)
    val health: StateFlow<YouTubeScrobblerHealth> = _health.asStateFlow()

    /**
     * Outstanding plays: only those since the opt-in instant
     * ([YouTubeHistoryPreference.sendPlaysSince]), so Settings never counts the
     * history from before the user turned the feature on, which is never sent.
     * A function, not a property, so constructing the scrobbler reads no flow.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pendingCount(): Flow<Int> = preference.sendPlaysSince.flatMapLatest { sinceMs ->
        if (sinceMs == null) flowOf(0) else listeningEventDao.pendingYtScrobbleCount(sinceMs)
    }

    /** Must be called once from `Application.onCreate`. */
    fun start() {
        scope.launch { maybeClearKillSwitchOnUpdate() }
        // Stamps the cutoff for an install that turned the feature on before it
        // existed, from app start rather than from the first drain (which waits
        // for a YouTube connection), so plays made meanwhile still count.
        scope.launch { readCutoff() }
        scope.launch {
            combine(
                preference.enabled,
                tokenManager.youTubeAuthState,
                pendingCount().distinctUntilChanged(),
                state.disabledReason,
            ) { enabled, _, _, disabledReason ->
                Triple(enabled, tokenManager.isAuthenticated(AuthService.YOUTUBE_MUSIC), disabledReason)
            }.collect { (enabled, ytConnected, disabledReason) ->
                when {
                    disabledReason != null -> _health.value = YouTubeScrobblerHealth.PROTOCOL_BROKEN
                    !enabled || !ytConnected -> _health.value = YouTubeScrobblerHealth.DISABLED
                    else -> {
                        // Flip to OK on entering the active branch so the
                        // UI reflects "enabled, nothing pending" immediately.
                        // submit() will overwrite with AUTH_FAILED / OFFLINE /
                        // PROTOCOL_BROKEN if subsequent pings fail.
                        _health.value = YouTubeScrobblerHealth.OK
                        drainQueue()
                    }
                }
            }
        }
    }

    // Internal visibility: `@VisibleForTesting` doesn't exist in :core:data; use internal.
    internal suspend fun maybeClearKillSwitchOnUpdate() {
        val current = versionCodeProvider()
        val known = state.currentLastKnownVersionCode()
        if (current > known) {
            state.setDisabledReason(null)
            state.resetConsecutiveFailures()
            state.setLastKnownVersionCode(current)
        }
    }

    /** Test entry-point: allows tests to call [maybeClearKillSwitchOnUpdate] directly. */
    @Suppress("FunctionName")
    suspend fun maybeClearKillSwitchOnUpdateForTest() = maybeClearKillSwitchOnUpdate()

    /** The opt-in cutoff, or null when the feature is off (or it cannot be read). */
    private suspend fun readCutoff(): Long? = try {
        preference.sendPlaysSinceMs()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not read the history cutoff", e)
        null
    }

    /** An unreadable kill-switch counts as tripped: stopping is the safe side. */
    private suspend fun killSwitchTripped(): Boolean = try {
        state.currentDisabledReason() != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not read the kill-switch", e)
        true
    }

    internal suspend fun drainQueue() {
        // No cutoff means the feature is off: never fall back to "everything",
        // which would send the user's whole history (see YouTubeHistoryPreference).
        val sinceMs = readCutoff() ?: return
        val pending = runCatching { listeningEventDao.pendingYtScrobbles(sinceMs = sinceMs, limit = 50) }
            .getOrElse {
                Log.w(TAG, "drainQueue: failed to load pending events", it)
                return
            }
        if (pending.isEmpty()) return
        for (event in pending) {
            // Re-checked before every event, not once per pass: the collector only
            // sees a turn-off or a tripped kill-switch after this pass returns, and
            // a 50-event pass runs for about a minute. A cutoff that moved means the
            // user turned the feature off and on again, so older plays are skipped.
            val cutoff = readCutoff() ?: break
            if (event.startedAt < cutoff) continue
            if (killSwitchTripped()) break

            val track = runCatching { trackDao.getById(event.trackId) }.getOrNull()
            if (track == null) {
                // Track deleted between recording and scrobbling — mark handled.
                runCatching { listeningEventDao.markYtScrobbled(event.id) }
                continue
            }
            if (submit(event, track) == SubmitOutcome.STOP_PASS) break
            delay(REQUEST_INTERVAL_MS + Random.nextLong(-JITTER_MS, JITTER_MS))
        }
    }

    /**
     * Whether the rest of a drain pass can still succeed after one [submit].
     * A failure every later event would hit too (no network, dead cookies, the
     * kill-switch tripping) ends the pass; otherwise each new listen re-walked
     * the whole batch, about three requests per event, against a YouTube that
     * could not accept any of them. Outcomes tied to one track do not.
     */
    internal enum class SubmitOutcome { CONTINUE, STOP_PASS }

    /**
     * Resolves the canonical video id, fetches its tracking URL, then fires
     * the authenticated history ping. Delegates the outcome to one of the
     * `on*` handlers which update [_health] and, when appropriate, the
     * kill-switch in [state].
     */
    internal suspend fun submit(event: ListeningEventEntity, track: TrackEntity): SubmitOutcome {
        val lookup = try {
            resolver.resolve(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "submit: canonical lookup threw for event=${event.id}", e)
            CanonicalLookup.Failed
        }
        val canonicalId = when (lookup) {
            is CanonicalLookup.Found -> lookup.match.videoId
            CanonicalLookup.NoMatch -> {
                // No ATV/OMV version found — mark handled to avoid UGC pollution.
                runCatching { listeningEventDao.markYtScrobbled(event.id) }
                return SubmitOutcome.CONTINUE
            }
            CanonicalLookup.Failed -> {
                // The search did not answer (offline, 429, 5xx). That says nothing
                // about whether a canonical version exists, so the play stays
                // queued; marking it handled here dropped every offline listen.
                onTransientFailure("search_failed")
                return SubmitOutcome.STOP_PASS
            }
        }

        val trackingUrl = runCatching {
            innerTubeClient.getPlaybackTracking(canonicalId)
        }.getOrNull()
        if (trackingUrl.isNullOrBlank()) {
            // Also returned for one unplayable video, so it does not end the pass:
            // that would let a single such play at the head block the whole queue.
            onTransientFailure("no_tracking_url")
            return SubmitOutcome.CONTINUE
        }

        val cookies = tokenManager.getYouTubeCookie() ?: run {
            onAuthFailure()
            return SubmitOutcome.STOP_PASS
        }
        val sapiSid = cookieHelper.extractSapiSid(cookies) ?: run {
            onAuthFailure()
            return SubmitOutcome.STOP_PASS
        }

        // PlaybackTrackingParser returns only URLs that may receive the user's
        // YouTube credentials, and the submitter sends no ping to any other.
        val code = try {
            pingSubmitter.submit(trackingUrl, cookies, sapiSid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "submit: network error for event=${event.id}", e)
            onTransientFailure("io_error")
            return SubmitOutcome.STOP_PASS
        }

        return when (code) {
            in 200..299 -> {
                onSuccess(event.id)
                SubmitOutcome.CONTINUE
            }
            401, 403 -> {
                onAuthFailure()
                SubmitOutcome.STOP_PASS
            }
            else -> {
                Log.w(TAG, "submit: unexpected HTTP $code for event=${event.id}")
                if (onProtocolFailure()) SubmitOutcome.STOP_PASS else SubmitOutcome.CONTINUE
            }
        }
    }

    /** Test entry-point: exposes [submit] to the test package. */
    @Suppress("FunctionName")
    suspend fun submitForTest(event: ListeningEventEntity, track: TrackEntity) {
        submit(event, track)
    }

    private suspend fun onSuccess(eventId: Long) {
        runCatching { listeningEventDao.markYtScrobbled(eventId) }
        state.resetConsecutiveFailures()
        _health.value = YouTubeScrobblerHealth.OK
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onTransientFailure(reason: String) {
        _health.value = YouTubeScrobblerHealth.OFFLINE
    }

    private fun onAuthFailure() {
        _health.value = YouTubeScrobblerHealth.AUTH_FAILED
    }

    /** @return true when this failure tripped the kill-switch. */
    private suspend fun onProtocolFailure(): Boolean {
        val count = state.incrementConsecutiveFailures()
        return if (count >= KILL_SWITCH_THRESHOLD) {
            state.setDisabledReason("protocol_errors")
            _health.value = YouTubeScrobblerHealth.PROTOCOL_BROKEN
            true
        } else {
            _health.value = YouTubeScrobblerHealth.OFFLINE
            false
        }
    }

    companion object {
        private const val TAG = "YouTubeHistoryScrobbler"
        private const val REQUEST_INTERVAL_MS = 750L
        private const val JITTER_MS = 250L
        private const val KILL_SWITCH_THRESHOLD = 5
    }
}
