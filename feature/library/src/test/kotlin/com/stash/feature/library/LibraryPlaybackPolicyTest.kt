package com.stash.feature.library

import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.media.streaming.ConnectivityMonitor
import com.stash.core.model.Track
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.doReturn

internal fun testPlaybackPolicy(online: Boolean = true, cellular: Boolean = false, allowCellular: Boolean = false) =
    LibraryPlaybackPolicy(
        mock<ConnectivityMonitor> {
            on { isConnected() } doReturn online
            on { isCellular() } doReturn cellular
        },
        mock<StreamingPreference> {
            on { streamOnCellular } doReturn flowOf(allowCellular)
        },
    )

class LibraryPlaybackPolicyTest {
    private val local = Track(1, "Local", "Artist", isDownloaded = true, filePath = "/music/local.flac")
    private val remote = Track(2, "Remote", "Artist", savedInCollection = true)
    private val unavailable = Track(3, "Unavailable", "Artist", isStreamableCheckedAt = 1L)
    private val another = local.copy(id = 4)
    private val collection = listOf(remote, local, unavailable, another)

    @Test fun online_includes_saved_remote_and_local_in_order() = runTest {
        assertEquals(listOf(2L, 1L, 4L), testPlaybackPolicy().eligible(collection).map { it.id })
    }

    @Test fun offline_keeps_only_local_in_order_without_altering_collection() = runTest {
        assertEquals(listOf(1L, 4L), testPlaybackPolicy(online = false).eligible(collection).map { it.id })
        assertEquals(4, collection.size)
    }

    @Test fun cellular_preference_is_respected() = runTest {
        assertEquals(listOf(1L, 4L), testPlaybackPolicy(cellular = true).eligible(collection).map { it.id })
        assertEquals(listOf(2L, 1L, 4L), testPlaybackPolicy(cellular = true, allowCellular = true).eligible(collection).map { it.id })
    }
}
