package com.stash.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped latch for speculative warmups only. The first Activity releases
 * it when the intro disappears (or when no intro is needed). Headless launches
 * and interrupted/slow startups have a bounded fallback; no service or user
 * action should depend on this gate.
 */
internal class SonoraIntroStartupGate(
    private val fallbackMs: Long = 8_000L,
) {
    private val ready = CompletableDeferred<Unit>()

    fun markReady() {
        ready.complete(Unit)
    }

    suspend fun awaitReady() {
        withTimeoutOrNull(fallbackMs) { ready.await() }
    }
}
