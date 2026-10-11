package com.stash.core.data.lastfm

import kotlin.coroutines.cancellation.CancellationException

/**
 * Last.fm answered with an `{ error, message }` body. [code] is Last.fm's own
 * numeric error code (as text, the way the JSON carries it).
 *
 * Typed so callers can tell a DEFINITIVE answer ("that track/artist isn't in
 * the catalogue", error 6) from an outage (network, 5xx, 429, every read key
 * throttled). The enrichment workers write permanent "nothing to find"
 * sentinels; before this they could only see "the call failed", so a 30 s
 * Wi-Fi drop poisoned a whole batch of tracks for good.
 *
 * Extends [IllegalStateException] because that is what `error(...)` threw here
 * before, so any existing catch keeps matching.
 */
class LastFmApiException(
    val code: String,
    message: String,
) : IllegalStateException(message) {
    companion object {
        /** Last.fm's "Track not found" / "The artist you supplied could not be found". */
        const val NOT_FOUND = "6"
    }
}

/**
 * True when this read failed for a reason that may go away (anything except a
 * definitive Last.fm "not found"), so the caller must NOT record "no data".
 * A success is never transient.
 *
 * The client's read methods wrap their body in `runCatching`, which also
 * catches coroutine cancellation; that is rethrown here so a cancelled worker
 * stops instead of carrying on as if one request had failed.
 */
fun Result<*>.isTransientLastFmFailure(): Boolean {
    val error = exceptionOrNull() ?: return false
    if (error is CancellationException) throw error
    return !(error is LastFmApiException && error.code == LastFmApiException.NOT_FOUND)
}
