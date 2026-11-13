package com.stash.feature.home

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.discovery.HomeDiscoveryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock

/** Old Qobuz browse links can still be restored, but must never fetch. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistBrowseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `restored legacy playlist page never calls an excluded catalog`() = runTest {
        val repo = mock<HomeDiscoveryRepository>()
        val vm = PlaylistBrowseViewModel(SavedStateHandle(mapOf("genre" to "All")), repo)
        advanceUntilIdle()
        assertThat(vm.state.value.playlists).isEmpty()
        assertThat(vm.state.value.endReached).isTrue()
        assertThat(vm.state.value.isLoading).isFalse()
        verifyNoInteractions(repo)
    }

    @Test fun `restored genre route cannot re-enable old catalog paging`() = runTest {
        val repo = mock<HomeDiscoveryRepository>()
        val vm = PlaylistBrowseViewModel(SavedStateHandle(mapOf("genre" to "Metal")), repo)
        vm.loadMore()
        advanceUntilIdle()
        assertThat(vm.state.value.playlists).isEmpty()
        verifyNoInteractions(repo)
    }

    @Test fun `typing and reloading a legacy route never searches Qobuz`() = runTest {
        val repo = mock<HomeDiscoveryRepository>()
        val vm = PlaylistBrowseViewModel(SavedStateHandle(mapOf("genre" to "All")), repo)
        vm.onQueryChange("new release")
        advanceUntilIdle()
        vm.loadMore()
        assertThat(vm.state.value.query).isEqualTo("new release")
        assertThat(vm.state.value.playlists).isEmpty()
        verifyNoInteractions(repo)
    }
}
