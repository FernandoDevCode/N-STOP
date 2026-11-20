package com.stash.core.data.radio

import com.stash.core.model.MusicSource
import com.stash.core.model.Track
import com.stash.core.model.YouTubeVideoId
import com.stash.data.ytmusic.CanonicalMatch
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.TrackSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and extends a balanced radio station using YouTube Music related artists
 * and its playable popular tracks. Stateless across stations — all live
 * state lives in the returned [RadioSession].
 */
@Singleton
class RadioStationGenerator @Inject constructor(
    private val yt: YTMusicApiClient,
) {
    /** Start a station: returns the session + the first playable batch. */
    suspend fun start(seed: RadioSeed): Pair<RadioSession, List<Track>> = when (seed) {
        is RadioSeed.Artist -> startArtist(seed)
        is RadioSeed.Song -> startSong(seed)
    }

    private suspend fun startArtist(seed: RadioSeed.Artist): Pair<RadioSession, List<Track>> {
        val id = seed.ytBrowseId ?: yt.resolveArtist(seed.name)?.id
        val profile = id?.let { runCatching { yt.getArtist(it) }.getOrNull() }
        val allNeighbors = profile?.related.orEmpty().take(NEIGHBOR_MAX)
            .map { RadioNeighbor(it.name, 1f) }
        val neighbors = allNeighbors.take(NEIGHBOR_POOL)
        val artistTracks = coroutineScope {
            val seedJob = async { seed.name to (profile?.popular ?: emptyList()) }
            val neighborJobs = neighbors.map { neighbor ->
                async { neighbor.name to resolvePopular(neighbor.name) }
            }
            (listOf(seedJob) + neighborJobs).awaitAll().toMap()
        }
        val pool = buildArtistPool(seed.name, neighbors, artistTracks)
        val session = RadioSession(seed, RadioInterleaver.order(pool).toMutableList(), mutableSetOf())
        session.remainingNeighbors.addAll(allNeighbors.drop(NEIGHBOR_POOL))
        session.neighborsLoaded = true
        return session to drainBatch(session, FIRST_BATCH)
    }

    private suspend fun relatedArtists(name: String, browseId: String? = null): List<RadioNeighbor> {
        val id = browseId ?: yt.resolveArtist(name)?.id ?: return emptyList()
        return runCatching { yt.getArtist(id).related }
            .getOrDefault(emptyList())
            .take(NEIGHBOR_MAX)
            .map { RadioNeighbor(it.name, 1f) }
    }

    private suspend fun resolvePopular(name: String): List<TrackSummary> {
        val id = yt.resolveArtist(name)?.id ?: return emptyList()
        return runCatching { yt.getArtist(id).popular }.getOrDefault(emptyList())
    }

    /**
     * Seed weight tuned so that WITHIN the current pool the seed is the single
     * most frequent artist — weight = SEED_SHARE/(1-SEED_SHARE) * W (W = total
     * neighbor weight) makes the seed's weight ~1/3 of the total, so the
     * interleaver front-loads the seed's tracks across the opening. The seed has
     * a finite catalog (~[TRACKS_PER_ARTIST] popular tracks), so as the station
     * widens to more neighbors the seed naturally recedes — "recognizable open,
     * always drifting outward." A literal steady 1/3-forever is impossible
     * without repeating the seed's few tracks, which the no-repeat set forbids.
     */
    private fun buildArtistPool(
        seedName: String,
        neighbors: List<RadioNeighbor>,
        tracks: Map<String, List<TrackSummary>>,
    ): List<RadioCandidate> {
        val neighborWeightSum = neighbors.sumOf { it.match.coerceAtLeast(0.05f).toDouble() }.toFloat()
        val seedWeight = if (neighborWeightSum <= 0f) 1f
            else (SEED_SHARE / (1f - SEED_SHARE)) * neighborWeightSum
        val out = ArrayList<RadioCandidate>()
        tracks[seedName]?.take(TRACKS_PER_ARTIST)?.forEach {
            out += RadioCandidate(seedName, it.title, it.videoId, seedWeight, it.thumbnailUrl)
        }
        for (n in neighbors) {
            val w = n.match.coerceAtLeast(0.05f)
            tracks[n.name]?.take(TRACKS_PER_ARTIST)?.forEach {
                out += RadioCandidate(n.name, it.title, it.videoId, w, it.thumbnailUrl)
            }
        }
        return out
    }

    /** Walk the ordered pool from the cursor, emit up to [size] fresh tracks,
     *  resolving any candidate that still lacks a videoId. Skips dupes + misses.
     *  Does NOT set `exhausted` — widen() owns that (the pool may still widen). */
    internal suspend fun drainBatch(session: RadioSession, size: Int): List<Track> {
        val prefetched = prefetchMatches(session, size + RESOLVE_SLACK)
        val out = ArrayList<Track>(size)
        while (out.size < size && session.cursor < session.ordered.size) {
            val cand = session.ordered[session.cursor++]
            val id = cand.identity()
            if (id in session.played) continue
            // Candidates with a known videoId (artist-radio, seeded song) keep
            // their own thumbnail; those without one resolve via YT Music search,
            // which also yields the song's square album cover — so no-thumbnail
            // candidates get a crisp cover instead of the low-res video frame.
            val existingId = cand.videoId?.takeIf { it.isNotBlank() }
            val videoId: String
            val resolvedArt: String?
            if (existingId != null) {
                videoId = existingId
                resolvedArt = null
            } else {
                val match = (if (id in prefetched) prefetched[id] else yt.searchCanonicalMatch(cand.artist, cand.title))
                    ?: continue
                videoId = match.videoId
                resolvedArt = match.thumbnailUrl
            }
            // Two different candidates (e.g. a seed-artist top track and a similar
            // track) can resolve to the SAME videoId — guard on the resolved id too
            // so the same audio never plays twice in one station.
            val vidKey = "vid:$videoId"
            if (vidKey in session.played) continue
            session.played += id
            session.played += vidKey
            out += cand.toTrack(videoId, resolvedArt)
        }
        return out
    }

    /**
     * Resolves, concurrently, the next [count] candidates from the cursor that
     * still need a YouTube search, keyed by [identity] (a null value is a
     * miss). [drainBatch] used to search them one at a time: every song-radio
     * candidate but the seed lacks a videoId, so the first batch waited on
     * about a dozen sequential searches (5-10 s) before anything could play.
     * Artist radio already resolves its neighbours in parallel. Candidates
     * past [count] (when misses or duplicates use up the lookahead) are still
     * searched one by one in [drainBatch].
     */
    private suspend fun prefetchMatches(session: RadioSession, count: Int): Map<String, CanonicalMatch?> {
        val toResolve = LinkedHashMap<String, RadioCandidate>()
        var i = session.cursor
        while (toResolve.size < count && i < session.ordered.size) {
            val cand = session.ordered[i++]
            if (!cand.videoId.isNullOrBlank()) continue
            val id = cand.identity()
            if (id !in session.played) toResolve.putIfAbsent(id, cand)
        }
        if (toResolve.isEmpty()) return emptyMap()
        return coroutineScope {
            toResolve.entries.chunked(RESOLVE_PARALLELISM).flatMap { chunk ->
                chunk.map { (id, cand) ->
                    async { id to yt.searchCanonicalMatch(cand.artist, cand.title) }
                }.awaitAll()
            }.toMap()
        }
    }

    /** The next playable batch. Widens the pool when it nears exhaustion. */
    suspend fun nextBatch(session: RadioSession): List<Track> {
        if (session.cursor >= session.ordered.size - WIDEN_THRESHOLD && !session.exhausted) {
            widen(session)
        }
        return drainBatch(session, GROW_BATCH)
    }

    /** Append more candidates by PAGING the unused neighbor list (never
     *  re-fetching the same top-N, which would only re-return already-played
     *  neighbors). Song radio lazy-loads the neighbor list here on first widen;
     *  artist radio filled it at start. Sets [exhausted] only when no neighbors
     *  remain to try. */
    private suspend fun widen(session: RadioSession) {
        if (!session.neighborsLoaded) {
            val seedArtistName = when (val s = session.seed) {
                is RadioSeed.Artist -> s.name
                is RadioSeed.Song -> s.artist
            }
            session.remainingNeighbors.addAll(
                relatedArtists(seedArtistName, (session.seed as? RadioSeed.Artist)?.ytBrowseId),
            )
            session.neighborsLoaded = true
        }
        if (session.remainingNeighbors.isEmpty()) { session.exhausted = true; return }
        // Page the next slice of never-used neighbors.
        val next = ArrayList<RadioNeighbor>()
        while (next.size < NEIGHBOR_POOL && session.remainingNeighbors.isNotEmpty()) {
            next += session.remainingNeighbors.removeFirst()
        }
        val fresh = ArrayList<RadioCandidate>()
        for (n in next) {
            val id = yt.resolveArtist(n.name)?.id ?: continue
            val popular = runCatching { yt.getArtist(id).popular }.getOrDefault(emptyList())
            popular.take(TRACKS_PER_ARTIST).forEach {
                val cand = RadioCandidate(n.name, it.title, it.videoId, n.match.coerceAtLeast(0.05f), it.thumbnailUrl)
                if (cand.identity() !in session.played) fresh += cand
            }
        }
        if (fresh.isEmpty()) {
            // This slice yielded nothing new; only truly done when no neighbors remain.
            if (session.remainingNeighbors.isEmpty()) session.exhausted = true
            return
        }
        session.ordered += RadioInterleaver.order(fresh)
    }

    private fun RadioCandidate.toTrack(videoId: String, resolvedArt: String? = null) = Track(
        id = videoId.hashCode().toLong(),
        title = title,
        artist = artist,
        durationMs = 0L,
        // Album art, best-first: the candidate's own InnerTube cover (artist-radio
        // popular tracks carry a square 544px one), else the square album cover
        // pulled from the YT Music search that resolved the videoId (song-radio /
        // Last.fm neighbours), else the low-res video frame as a last resort. This
        // is the single chokepoint every emitted radio Track flows through.
        albumArtUrl = thumbnailUrl ?: resolvedArt ?: ytThumbnail(videoId),
        youtubeId = videoId,
        source = MusicSource.YOUTUBE,
        isStreamable = true,
    )

    /** YouTube thumbnail for a videoId, used only when a track has no richer
     *  cover. `maxresdefault` (1280x720) is the full 16:9 frame with NO black
     *  letterbox bars (unlike `hqdefault`/`sddefault`, which pad 16:9 into a 4:3
     *  frame), so it center-crops cleanly to a square without black space —
     *  same reason this used to ask for `mqdefault`, but four times the width.
     *  Videos without one are walked back down by `ArtFallbackInterceptor`.
     *  The player upgrades this to the square Qobuz cover once qbdlx resolves.
     *  Null for an id without the YouTube video-id shape: the id came from a
     *  server response and is only built into a URL when it has it. */
    private fun ytThumbnail(videoId: String): String? =
        videoId.takeIf(YouTubeVideoId::isValid)?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }

    private suspend fun startSong(seed: RadioSeed.Song): Pair<RadioSession, List<Track>> {
        val browseId = yt.resolveArtist(seed.artist)?.id
        val profile = browseId?.let { runCatching { yt.getArtist(it) }.getOrNull() }
        val related = profile?.related.orEmpty()
        val neighbors = related.take(NEIGHBOR_POOL)
        val pool = ArrayList<RadioCandidate>()
        val seedWeight = if (neighbors.isEmpty()) 1f else
            (SEED_SHARE / (1f - SEED_SHARE)) * neighbors.size
        pool += RadioCandidate(seed.artist, seed.title, seed.ytVideoId, seedWeight)
        profile?.popular.orEmpty()
            .filterNot { it.title.equals(seed.title, ignoreCase = true) }
            .take(TRACKS_PER_ARTIST)
            .forEach { pool += RadioCandidate(seed.artist, it.title, it.videoId, seedWeight, it.thumbnailUrl) }
        val relatedTracks = coroutineScope {
            neighbors.map { n -> async { n.name to resolvePopular(n.name) } }.awaitAll()
        }
        for ((artist, songs) in relatedTracks) {
            songs.take(TRACKS_PER_ARTIST).forEach {
                pool += RadioCandidate(artist, it.title, it.videoId, 1f, it.thumbnailUrl)
            }
        }
        val session = RadioSession(seed, RadioInterleaver.order(pool).toMutableList(), mutableSetOf())
        session.remainingNeighbors.addAll(related.drop(NEIGHBOR_POOL).map { RadioNeighbor(it.name, 1f) })
        session.neighborsLoaded = true
        return session to drainBatch(session, FIRST_BATCH)
    }

    companion object {
        const val SEED_SHARE = 1f / 3f
        const val NEIGHBOR_LIMIT = 30    // Last.fm page size for song getSimilarTracks
        const val NEIGHBOR_MAX = 100     // full artist-neighbor list (paged by widen)
        const val NEIGHBOR_POOL = 12     // neighbors consumed per pool/widen slice
        const val TRACKS_PER_ARTIST = 6
        const val FIRST_BATCH = 12
        const val GROW_BATCH = 12
        const val WIDEN_THRESHOLD = 6
        const val SONG_SIMILAR_POOL = 40

        /** Extra candidates resolved ahead of a batch, to cover typical misses. */
        const val RESOLVE_SLACK = 3

        /** YouTube searches in flight at once while prefetching. */
        const val RESOLVE_PARALLELISM = 5
    }
}
