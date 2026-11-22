package com.stash.feature.search

import com.stash.core.data.musicbrainz.MusicBrainzAlbumGroup
import com.stash.core.data.musicbrainz.MusicBrainzEdition
import com.stash.core.data.musicbrainz.MusicBrainzEditionTrack
import com.stash.core.model.TrackItem
import com.stash.core.model.YouTubeVideoId
import com.stash.data.ytmusic.YTMusicCatalogItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** An edition in MusicBrainz is metadata, never a playable YTM album ID. */
internal data class MusicBrainzDiscoveryState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val groups: List<MusicBrainzAlbumGroup> = emptyList(),
    val selectedGroup: MusicBrainzAlbumGroup? = null,
    val editions: List<MusicBrainzEdition> = emptyList(),
    val selectedEdition: MusicBrainzEdition? = null,
    val tracks: List<MusicBrainzEditionTrack> = emptyList(),
    val resolved: Map<Int, TrackItem> = emptyMap(),
    val unavailable: Set<Int> = emptySet(),
    val resolving: Int? = null,
    val error: String? = null,
)

/** Conservative matching: same complete title/version, artist credit and near-identical duration.
 * Never quietly substitute a live, demo, remix or a second ambiguous matching video. */
internal fun findMusicBrainzTrackAudio(
    artistName: String,
    track: MusicBrainzEditionTrack,
    results: List<YTMusicCatalogItem>,
): YTMusicCatalogItem? {
    val name = exactMusicKey(track.title)
    val artist = exactMusicKey(artistName)
    return results.asSequence()
        .filter { it.kind == YTMusicCatalogItem.Kind.TRACK && YouTubeVideoId.isValid(it.id) }
        .filter { exactMusicKey(it.title) == name }
        .filter { item ->
            item.subtitle?.substringBefore('·')?.substringBefore('•')?.substringBefore(',')
                ?.let { exactMusicKey(it) == artist } == true
        }
        .filter { item -> track.durationMs == null || item.durationMs?.let { abs(it - track.durationMs) <= 4_000 } == true }
        .distinctBy { it.id }
        .take(2)
        .toList()
        .singleOrNull()
}

private fun exactMusicKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
