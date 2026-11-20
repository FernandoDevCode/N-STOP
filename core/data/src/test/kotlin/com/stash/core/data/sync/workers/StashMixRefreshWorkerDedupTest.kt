package com.stash.core.data.sync.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.mix.MixGenerator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StashMixRefreshWorkerDedupTest {
    private val context: Context = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true) {
        every { inputData } returns workDataOf()
    }
    private val recipes: StashMixRecipeDao = mockk(relaxed = true)
    private val playlists: PlaylistDao = mockk(relaxed = true)
    private val tracks: TrackDao = mockk(relaxed = true)
    private val generator: MixGenerator = mockk()

    private fun recipe(id: Long, name: String) = StashMixRecipeEntity(
        id = id, name = name, discoveryRatio = 0.85f, targetLength = 40,
        isBuiltin = true, isActive = true,
    )
    private fun track(id: Long) = TrackEntity(
        id = id, title = "Track " + id, artist = "Artist " + id,
        canonicalTitle = "track " + id, canonicalArtist = "artist " + id,
        isDownloaded = true,
    )

    @Test fun localMixesExcludeTracksAlreadyAssignedToEarlierMixes() = runTest {
        val deep = recipe(1L, "Deep Cuts")
        val daily = recipe(2L, "Daily Discover")
        coEvery { recipes.getActive() } returns listOf(daily, deep)
        val firstExclude = slot<Set<Long>>()
        val secondExclude = slot<Set<Long>>()
        coEvery { generator.generate(deep.copy(discoveryRatio = 0f), capture(firstExclude)) } returns listOf(track(10L), track(11L))
        coEvery { generator.generate(daily.copy(discoveryRatio = 0f), capture(secondExclude)) } returns listOf(track(20L))
        val worker = StashMixRefreshWorker(context, params, recipes, playlists, tracks, generator)
        worker.doWork()
        assertTrue(firstExclude.captured.isEmpty())
        assertEquals(setOf(10L, 11L), secondExclude.captured)
    }
}
