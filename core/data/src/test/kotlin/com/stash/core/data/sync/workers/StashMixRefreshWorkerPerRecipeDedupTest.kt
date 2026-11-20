package com.stash.core.data.sync.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.mix.MixGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StashMixRefreshWorkerPerRecipeDedupTest {
    private val context: Context = mockk(relaxed = true)
    private val recipes: StashMixRecipeDao = mockk(relaxed = true)
    private val playlists: PlaylistDao = mockk(relaxed = true)
    private val tracks: TrackDao = mockk(relaxed = true)
    private val generator: MixGenerator = mockk()
    private fun recipe(id: Long, playlistId: Long?) = StashMixRecipeEntity(
        id = id, name = "Mix " + id, discoveryRatio = 0.85f,
        targetLength = 40, playlistId = playlistId, isBuiltin = true, isActive = true,
    )
    private fun worker() = StashMixRefreshWorker(
        context,
        mockk<WorkerParameters>(relaxed = true) {
            every { inputData } returns workDataOf(StashMixRefreshWorker.KEY_RECIPE_ID to 1L)
        },
        recipes, playlists, tracks, generator,
    )

    @Test fun singleMixRefreshExcludesOtherMixTracks() = runTest {
        val target = recipe(1L, 100L)
        val other1 = recipe(2L, 200L)
        val other2 = recipe(3L, 300L)
        coEvery { recipes.getById(1L) } returns target
        coEvery { recipes.getActive() } returns listOf(target, other1, other2)
        coEvery { playlists.getTrackIdsForPlaylists(any()) } returns listOf(50L, 51L, 52L)
        val excluded = slot<Set<Long>>()
        coEvery { generator.generate(target.copy(discoveryRatio = 0f), capture(excluded)) } returns emptyList()
        worker().doWork()
        assertEquals(setOf(50L, 51L, 52L), excluded.captured)
    }

    @Test fun singleMixWithoutOtherMaterializedMixesHasNoExclusions() = runTest {
        val target = recipe(1L, 100L)
        val other = recipe(2L, null)
        coEvery { recipes.getById(1L) } returns target
        coEvery { recipes.getActive() } returns listOf(target, other)
        val excluded = slot<Set<Long>>()
        coEvery { generator.generate(target.copy(discoveryRatio = 0f), capture(excluded)) } returns emptyList()
        worker().doWork()
        assertTrue(excluded.captured.isEmpty())
        coVerify(exactly = 0) { playlists.getTrackIdsForPlaylists(any()) }
    }
}
