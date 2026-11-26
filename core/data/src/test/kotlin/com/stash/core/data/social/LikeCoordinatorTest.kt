package com.stash.core.data.social

import com.stash.core.data.social.stash.StashLikedPlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

/** Local-only heart contract: no external client, preference or queue is a dependency. */
class LikeCoordinatorTest {
    private val repository = mockk<StashLikedPlaylistRepository>(relaxed = true)
    private val coordinator = LikeCoordinator(repository)

    @Test fun `like writes local membership synchronously`() = runTest {
        coordinator.setLiked(7L, true)
        coVerify(exactly = 1) { repository.add(7L) }
        coVerify(exactly = 0) { repository.remove(any()) }
    }

    @Test fun `unlike removes only local membership`() = runTest {
        coordinator.setLiked(7L, false)
        coVerify(exactly = 1) { repository.remove(7L) }
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test fun `rapid toggle preserves local action order`() = runTest {
        coordinator.setLiked(7L, true)
        coordinator.setLiked(7L, false)
        coordinator.setLiked(7L, true)
        io.mockk.coVerifySequence {
            repository.add(7L)
            repository.remove(7L)
            repository.add(7L)
        }
    }

    @Test fun `local failure reaches caller`() = runTest {
        coEvery { repository.add(7L) } throws IllegalStateException("db full")
        try {
            coordinator.setLiked(7L, true)
            fail("expected local failure")
        } catch (expected: IllegalStateException) {
            org.junit.Assert.assertEquals("db full", expected.message)
        }
    }
}
