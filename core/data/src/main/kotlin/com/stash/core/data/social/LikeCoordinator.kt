package com.stash.core.data.social

import com.stash.core.data.social.stash.StashLikedPlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Sonora hearts are local-only. External library imports never fan out writes. */
@Singleton
class LikeCoordinator @Inject constructor(
    private val stashLikedRepository: StashLikedPlaylistRepository,
) {
    // Kept for existing UI collectors; there is no remote mirror queue.
    val mirrorFailures: SharedFlow<String> = MutableSharedFlow<String>()

    suspend fun setLiked(trackId: Long, liked: Boolean) {
        if (liked) stashLikedRepository.add(trackId) else stashLikedRepository.remove(trackId)
    }
}
