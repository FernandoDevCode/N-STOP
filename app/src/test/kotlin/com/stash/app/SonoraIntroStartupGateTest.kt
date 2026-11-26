package com.stash.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonoraIntroStartupGateTest {
    @Test fun waitsForIntroAndReleasesAllWaiters() = runTest {
        val gate = SonoraIntroStartupGate()
        val first = async { gate.awaitReady() }
        val second = async { gate.awaitReady() }
        runCurrent()
        advanceTimeBy(3_000)
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        gate.markReady()
        runCurrent()
        assertTrue(first.isCompleted)
        assertTrue(second.isCompleted)
    }

    @Test fun bypassAndRepeatedCompletionAreSafe() = runTest {
        val gate = SonoraIntroStartupGate()
        gate.markReady()
        gate.markReady()
        val task = async { gate.awaitReady() }
        runCurrent()
        assertTrue(task.isCompleted)
    }

    @Test fun headlessStartupHasBoundedFallback() = runTest {
        val gate = SonoraIntroStartupGate(fallbackMs = 8_000)
        val task = async { gate.awaitReady() }
        runCurrent()
        advanceTimeBy(7_999)
        assertFalse(task.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(task.isCompleted)
    }

    @Test fun cancellingForegroundWaitDoesNotReleaseOtherWaiters() = runTest {
        val gate = SonoraIntroStartupGate()
        val cancelled = async { gate.awaitReady() }
        val surviving = async { gate.awaitReady() }
        runCurrent()
        cancelled.cancel()
        runCurrent()
        assertFalse(surviving.isCompleted)
        gate.markReady()
        runCurrent()
        assertTrue(surviving.isCompleted)
    }
}
