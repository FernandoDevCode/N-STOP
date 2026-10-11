package com.stash.feature.nowplaying

import com.stash.core.data.prefs.LyricsPreference
import com.stash.core.media.output.HiResOutputController
import com.stash.feature.nowplaying.ui.LyricsOutput
import com.stash.feature.nowplaying.ui.lyricsOutputOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The audio output lyrics are heard through, and the lyrics offset the listener set for it in
 * the sync dialog ("Apply to all songs on <device>"). Bluetooth headphones play noticeably later
 * than wired ones, so lyrics timed right on one are early on the other; the per-song offset can't
 * fix both, a per-output one can.
 */
interface LyricsOutputOffsets {
    /** The output playing now, or null while it isn't known (nothing has played yet). */
    val output: Flow<LyricsOutput?>

    /** [output]'s offset (signed ms; positive delays lyrics), 0 when none is set. */
    fun offsetMs(output: LyricsOutput): Flow<Long>

    suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long)

    /** No output known: the ViewModel's default, so unit tests see per-song offsets only. */
    object None : LyricsOutputOffsets {
        override val output: Flow<LyricsOutput?> = flowOf(null)
        override fun offsetMs(output: LyricsOutput): Flow<Long> = flowOf(0L)
        override suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long) = Unit
    }
}

/**
 * The output as the signal path sees it: [HiResOutputController.output] carries the device the
 * AudioTrack is actually routed to, refreshed when Android moves it (headphones connected or
 * unplugged mid-song), on every API level, which a guess from AudioManager's device list is not.
 * Read only; offsets are kept in [LyricsPreference].
 */
@Singleton
class RoutedLyricsOutputOffsets @Inject constructor(
    controller: HiResOutputController,
    private val preference: LyricsPreference,
) : LyricsOutputOffsets {
    override val output: Flow<LyricsOutput?> = controller.output
        .map { path -> path?.device?.let { lyricsOutputOf(it.kind.name, it.name) } }
        .distinctUntilChanged()

    override fun offsetMs(output: LyricsOutput): Flow<Long> = preference.outputOffsetMs(output.key)

    override suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long) =
        preference.setOutputOffsetMs(output.key, offsetMs)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LyricsOutputOffsetsModule {
    @Binds
    abstract fun bindLyricsOutputOffsets(impl: RoutedLyricsOutputOffsets): LyricsOutputOffsets
}
