package com.stash.core.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [FFmpegBridgeImpl.awaitCapturingStderr] with a real child process standing
 * in for ffmpeg. Cancelling the caller (the loudness backfill worker stopped
 * when the charger is unplugged) used to leave the decode running to the end.
 */
class FFmpegBridgeCancellationTest {

    @Test fun `cancelling the caller kills the process`() = runBlocking {
        val process = ProcessBuilder("sleep", "30").start()
        val job = launch(Dispatchers.IO) { FFmpegBridgeImpl.awaitCapturingStderr(process) }
        delay(200) // let it block in the stderr read

        // Well under the 30 s the process would otherwise run.
        withTimeout(5_000) { job.cancelAndJoin() }

        assertFalse(process.isAlive)
    }

    @Test fun `a process that ends returns its exit code and stderr`() = runBlocking {
        val process = ProcessBuilder("sh", "-c", "echo summary >&2; exit 3").start()

        val (exit, stderr) = FFmpegBridgeImpl.awaitCapturingStderr(process)

        assertEquals(3, exit)
        assertEquals("summary\n", stderr)
    }
}
