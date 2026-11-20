package com.stash.core.data.sync.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.mix.MixGenerator
import com.stash.core.data.mix.StashMixDefaults
import com.stash.core.data.prefs.DownloadNetworkPreference
import com.stash.core.data.sync.TrackMatcher
import com.stash.core.data.sync.enqueueUniquePeriodicWorkReviving
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Periodic worker that regenerates every active Stash Mix. For each
 * recipe:
 *
 *  1. Run [MixGenerator.generate] — pure-Kotlin ranking over the library.
 *  2. Find-or-create the backing [PlaylistEntity]. First refresh creates
 *     a new row with `type = STASH_MIX` and stores its id back on the
 *     recipe. Subsequent refreshes replace the track list in place so
 *     the Home-screen card URL and the user's history pointers stay
 *     stable.
 *  3. Rewrite [PlaylistTrackCrossRef] rows (REFRESH semantics).
 *  4. Recompute the cover art tiles from the top tracks.
 *  5. Stamp `last_refreshed_at`.
 *  6. For recipes with non-zero discovery ratio: query Last.fm
 *     `artist.getSimilar` seeded from the user's current top artists in
 *     that recipe's tag space, and enqueue candidate tracks into
 *     [com.stash.core.data.db.dao.DiscoveryQueueDao]. The actual search
 *     + download happens in a separate worker.
 *
 * Runs once per day (via WorkManager periodic scheduling) with no network
 * constraint — mix generation is purely local. The discovery Last.fm
 * queries inside run in a best-effort runCatching so a network outage
 * never blocks refreshing.
 */
@HiltWorker
class StashMixRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recipeDao: StashMixRecipeDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val mixGenerator: MixGenerator,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "StashMixRefresh"
        private const val WORK_NAME = "stash_mix_refresh"

        /** Serializes [materializeMix] across concurrent worker instances. */
        private val materializeMutex = kotlinx.coroutines.sync.Mutex()
        const val ONE_SHOT_WORK_NAME = "stash_mix_refresh_oneshot"

        /**
         * Unique name for the materialize-only re-link, kept apart from
         * [ONE_SHOT_WORK_NAME]. Sharing it with REPLACE meant every stub the
         * discovery drain materialized cancelled an in-flight FULL refresh
         * (cold start, "Refresh" tap) mid-loop: the remaining recipes queued
         * no discovery candidates that run and its closing drain kick never
         * happened. The two can safely overlap; [materializeMutex]
         * serializes the playlist writes.
         */
        internal const val MATERIALIZE_WORK_NAME = "stash_mix_relink_oneshot"

        /** The unique-work name [enqueueOneTime] uses for each mode. */
        internal fun oneShotWorkName(materializeOnly: Boolean): String =
            if (materializeOnly) MATERIALIZE_WORK_NAME else ONE_SHOT_WORK_NAME

        private const val TOP_ARTISTS_LIMIT = 8
        private const val SIMILAR_REQUEST_INTERVAL_MS = 220L
        private const val AFFINITY_LOOKBACK_DAYS = 180L

        /**
         * Floor for the filtered discovery pool. Below this, the TAG_GRAPH
         * fallback fires.
         *
         * Why 20 and not the recipe's discoveryCap (e.g. 34 for an 85%-discovery
         * recipe with targetLength 40)? Downstream attrition: not every queued
         * candidate becomes a viable survivor. StashDiscoveryWorker.handle()
         * applies an additional blocklist check; some candidates fail at the
         * download stage (yt-dlp can't match, no audio available, etc.); and
         * some get canonical-deduped against tracks added to the library AFTER
         * the seed-gen filter ran. Twenty pre-filter survivors typically yields
         * ~10-15 actually-downloaded DONE rows — enough to fill a 6-slot mix
         * slot AND leave a queue buffer for tomorrow's refresh. The fallback
         * is a viability gate, not a precise capacity match.
         */
        private const val MIN_DISCOVERY_POOL_AFTER_FILTER = 20

        /** Skip-event ban threshold: this many "early" skips in the time window → ban. */
        private const val DISCOVERY_SKIP_BAN_MIN_COUNT = 3

        /** Skip-event ban time window in milliseconds. */
        private val DISCOVERY_SKIP_BAN_WINDOW_MS = TimeUnit.DAYS.toMillis(90)

        /** Skip-position cutoff: skips in the first this-many ms count as rejection. */
        private const val DISCOVERY_SKIP_BAN_MAX_POSITION_MS = 30_000L

        /** Timeout for the TAG_GRAPH fallback Last.fm call. */
        private const val SEED_FALLBACK_TIMEOUT_MS = 30_000L

        // ── #287: day-fresh Daily Discover ────────────────────────────────
        /**
         * "Yesterday's listening" window that leads the seed chain. Two days,
         * not one — a calendar-day boundary would empty the window every
         * morning before the user plays anything.
         */
        private val RECENT_LISTENING_LOOKBACK_MS = TimeUnit.HOURS.toMillis(48)

        /** Guaranteed newest slice of the rotated survivor window. */
        private const val FRESH_HEAD_RATIO = 0.3f

        /** Upper bound on the DONE backlog considered for rotation. */
        private const val SURVIVOR_POOL_LIMIT = 500

        private val DAY_MS = TimeUnit.DAYS.toMillis(1)

        /**
         * #287: rotate the discovery-survivor window across the recipe's whole
         * accepted backlog instead of pinning the newest [cap] forever — the
         * old newest-first `.take(cap)` meant a refresh with no new DONE rows
         * recomputed the identical list and the idempotency backstop skipped
         * the write, so "Refresh" visibly did nothing.
         *
         * [pool] arrives newest-first. The newest ~[FRESH_HEAD_RATIO] of the
         * window is always kept (new finds must surface); the rest is a
         * seeded shuffle of the remaining backlog. Same [seed] → same window:
         * manual refreshes pass a per-tap seed (every tap rotates), periodic
         * runs pass a day bucket (rotates daily, stable within the day so
         * same-day re-runs still no-op).
         */
        internal fun rotateSurvivorWindow(pool: List<Long>, cap: Int, seed: Long): List<Long> {
            if (cap <= 0) return emptyList()
            val rnd = kotlin.random.Random(seed)
            // Small pools rotate their ORDER too — a static order is the same
            // invisibility bug at smaller scale.
            if (pool.size <= cap) return pool.shuffled(rnd)
            val freshHead = (cap * FRESH_HEAD_RATIO).roundToInt().coerceIn(0, cap)
            val head = pool.take(freshHead)
            val rest = pool.drop(freshHead).shuffled(rnd)
            // The freshness guarantee is MEMBERSHIP, not position: pinning the
            // newest finds at the top froze positions 0..N (and the cover
            // mosaic = first 4 positions) across refreshes — users watched the
            // one part of the mix that never moved and read Refresh as a
            // no-op. Shuffle the final window so position 0 and the art
            // rotate every seed while new finds stay guaranteed present.
            return (head + rest.take(cap - head.size)).shuffled(rnd)
        }

        /**
         * #287: recent listening leads, older taste backfills — dedup'd,
         * capped at [limit]. Keeps discovery pointed at what the user played
         * yesterday without collapsing to a single-artist echo chamber when
         * the recent window is thin.
         */
        internal fun <T> blendRecentFirst(recent: List<T>, fallback: List<T>, limit: Int): List<T> =
            (recent + fallback.filterNot { it in recent }).take(limit)

        /**
         * Input-data key for [enqueueOneTime] (single-recipe overload).
         * When present and > 0, [doWork] only refreshes that one recipe
         * instead of iterating every active builtin. Backs the manual
         * "Refresh this mix" action surfaced from the Home long-press
         * menu.
         */
        const val KEY_RECIPE_ID = "stash_mix_refresh_recipe_id"

        /**
         * Input-data key for [enqueueOneTime]'s materialize-only overload.
         * When true, [doWork] LINKS already-drained discovery stubs into the
         * mix playlists but does NOT re-queue discovery candidates and does
         * NOT re-kick the discovery drain. This is the post-drain re-link
         * path ([com.stash.core.data.sync.workers.StashDiscoveryWorker] fires
         * it when it materializes a stub) — running it in full mode formed a
         * runaway refresh⇄drain loop that continuously cleared + reinserted
         * every mix (the user-visible "load 40 tracks, then repopulate with
         * different tracks" churn on multi-genre mixes).
         */
        const val KEY_MATERIALIZE_ONLY = "stash_mix_refresh_materialize_only"

        /**
         * Schedule the periodic refresh. Default 24-hour cadence with no
         * constraints — the library-only path works offline and fast
         * enough to not care. Discovery is opportunistic and tolerates
         * being skipped when the device is offline.
         */
        suspend fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val work = PeriodicWorkRequestBuilder<StashMixRefreshWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            )
                .setConstraints(constraints)
                .build()
            // 2026-05-11: UPDATE (not KEEP) so existing installs reschedule against
            // the current worker spec on the next cold start. KEEP previously meant
            // constraint changes / class changes were ignored across upgrades —
            // a credible cause of "periodic refresh hasn't fired in 3 days" reports.
            // UPDATE can't revive a FAILED job, hence the reviving enqueue.
            WorkManager.getInstance(context).enqueueUniquePeriodicWorkReviving(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                work,
            )
        }

        /**
         * Fire a one-shot refresh immediately — used on first app launch
         * after seeding defaults so users see populated mixes without
         * waiting 24 hours for the periodic schedule, and by the
         * manual-refresh button on the Home Stash Mixes card.
         */
        fun enqueueOneTime(context: Context, materializeOnly: Boolean = false) {
            val builder = OneTimeWorkRequestBuilder<StashMixRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
            if (materializeOnly) {
                builder.setInputData(androidx.work.workDataOf(KEY_MATERIALIZE_ONLY to true))
            }
            // REPLACE within each mode: a newer request supersedes a pending
            // one of the same kind (a re-link restarted by a fresh stub picks
            // that stub up too). Across modes the names differ, so a re-link
            // never cancels a full refresh.
            WorkManager.getInstance(context).enqueueUniqueWork(
                oneShotWorkName(materializeOnly),
                androidx.work.ExistingWorkPolicy.REPLACE,
                builder.build(),
            )
        }

        /**
         * Fire a one-shot refresh for a single recipe. Used by the Home
         * long-press "Refresh this mix" action. Distinct unique-work name
         * per recipe id so refreshing mix A doesn't clobber a still-pending
         * refresh of mix B; REPLACE means a rapid double-tap on the same
         * mix coalesces into one job.
         */
        fun enqueueOneTime(context: Context, recipeId: Long) {
            val data = androidx.work.workDataOf(KEY_RECIPE_ID to recipeId)
            val work = OneTimeWorkRequestBuilder<StashMixRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${ONE_SHOT_WORK_NAME}_$recipeId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                work,
            )
        }
    }

    override suspend fun doWork(): Result {
        // Safety net: make sure default recipes exist. Normally seeded at
        // app startup; running here too means a fresh-install user gets
        // their first mixes even if the startup hook is racy with the
        // first WorkManager tick.
        StashMixDefaults.seedIfNeeded(recipeDao)

        val materializeOnly = inputData.getBoolean(KEY_MATERIALIZE_ONLY, false)
        val targetId = inputData.getLong(KEY_RECIPE_ID, -1L)
        val active = if (targetId > 0L) {
            val one = recipeDao.getById(targetId)?.takeIf { it.isActive && it.name != "Recommended by Last.fm" }
            if (one == null) {
                Log.d(TAG, "single-recipe refresh: recipe $targetId not found or inactive")
                return Result.success()
            }
            listOf(one)
        } else {
            recipeDao.getActive().filterNot { it.name == "Recommended by Last.fm" }
        }
        if (active.isEmpty()) {
            Log.d(TAG, "no active recipes")
            return Result.success()
        }
        Log.i(
            TAG,
            "refreshing ${active.size} Stash Mix(es)" +
                if (targetId > 0L) " (single: ${active.first().name})" else "",
        )
        // v0.9.21: diagnostic snapshot of every active recipe so we can rule
        // out duplicate-recipe / mismatched-id pathologies when a mix
        // mysteriously fails to link survivors.
        recipeDao.getActive().forEach { r ->
            Log.i(
                TAG,
                "  active recipe: id=${r.id} name='${r.name}' " +
                    "playlistId=${r.playlistId} ratio=${r.discoveryRatio} " +
                    "target=${r.targetLength} seed=${r.seedStrategy}",
            )
        }

        val now = System.currentTimeMillis()
        // v0.9.20: pre-sort by dedup priority so the most-restrictive
        // recipes claim their natural picks before more-permissive ones
        // see them. excludeIds accumulates library + discovery ids
        // across iterations and is threaded into both generate() and
        // materializeMix() — guarantees no track appears in two
        // playlists in a single refresh run.
        val orderedRecipes = active.sortedBy { recipeDedupPriority(it) }
        val excludeIds = mutableSetOf<Long>()

        // #287: manual single-recipe refresh rotates on every tap; periodic
        // batch runs rotate once per day (stable within the day so the
        // idempotency backstop still suppresses same-day no-op flashes).
        val rotationSeed = if (targetId > 0L) now else now / DAY_MS

        // v0.9.20 follow-up: single-recipe path needs explicit seeding from the
        // OTHER mixes' current playlist contents. The batch-mode loop accumulates
        // excludeIds naturally as it iterates; the single-element loop has nothing
        // to accumulate, so we seed it manually from the materialized state of the
        // other builtin mixes. Effect: manual refresh of one mix no longer overlaps
        // with whatever is currently in the others.
        if (targetId > 0L) {
            val otherPlaylistIds = recipeDao.getActive()
                .filter { it.id != targetId && it.playlistId != null }
                .mapNotNull { it.playlistId }
            if (otherPlaylistIds.isNotEmpty()) {
                excludeIds += playlistDao.getTrackIdsForPlaylists(otherPlaylistIds)
            }
        }

        for (recipe in orderedRecipes) {
            // Snapshot to an immutable Set per iteration so callees can't
            // observe (or accidentally mutate) the accumulator, and so
            // tests / future tracing can see exactly what was excluded
            // at the moment of the call.
            val excludeSnapshot = excludeIds.toSet()
            val tracks = mixGenerator.generate(recipe.copy(discoveryRatio = 0f), excludeSnapshot)

            // Empty-tracks skip is only safe for library-only recipes
            // (discoveryRatio == 0). Pure-discovery recipes like "Stash
            // Discover" (ratio = 1.0) produce an empty generator result
            // by design — the playlist gets its content from the
            // discovery re-link pass inside materializeMix + the async
            // StashDiscoveryWorker. Skipping them here would keep stale
            // pre-retune content in the playlist forever.
            if (tracks.isEmpty() && recipe.discoveryRatio == 0f) {
                Log.d(TAG, "'${recipe.name}' produced 0 tracks and has no discovery — skipping materialize")
                continue
            }

            // #287: serialize materialization across worker instances — the
            // manual single-recipe refresh and the chained batch pass can run
            // concurrently (separate WorkManager unique chains), and the
            // clear-then-reinsert membership write is not atomic. Observed on
            // device: two runs 10s apart interleaved and left Daily Discover
            // with 25 of 39 rows (and a wrong count) — a torn playlist.
            // Only this write is locked, not the whole run: a full refresh
            // spends most of its time on network calls, and a "Refresh this
            // mix" tap shouldn't wait for them. `recipe` was read before the
            // lock, so materializeMix also finds the playlist by its source id.
            val result = materializeMutex.withLock {
                materializeMix(recipe, tracks, now, excludeSnapshot, rotationSeed)
            }
            recipeDao.setPlaylistId(recipe.id, result.playlistId)
            recipeDao.setLastRefreshedAt(recipe.id, now)

            // v0.9.20: accumulate so the next recipe in the ordering doesn't re-pick these.
            excludeIds += tracks.map { it.id }
            excludeIds += result.discoveryIds


        }

        // v0.9.38 — hydrate fresh stream-only stubs with art + duration.
        // StashDiscoveryWorker lands those rows bare; without this chain
        // they'd render empty in the mix until the player resolves a
        // queue window at play time (conversation 2026-05-28). Fire-and-
        // forget — backfill failure must never block a successful refresh,
        // so the enqueue itself is guarded (e.g. WorkManager not yet
        // initialised in a unit-test JVM throws IllegalStateException).
        runCatching { ArtBackfillWorker.enqueueFromMixRefresh(applicationContext) }
            .onFailure { Log.w(TAG, "art-backfill enqueue failed; refresh still succeeded", it) }

        return Result.success()
    }

    /**
     * Output of [materializeMix]. Carries the just-created/updated
     * playlist id (existing return) AND the discovery-survivor track ids
     * the materialization inserted, so [doWork]'s outer cross-mix dedup
     * loop can add them to its accumulating excludeIds set.
     */
    private data class MaterializeResult(
        val playlistId: Long,
        val discoveryIds: List<Long>,
    )

    /**
     * Cross-mix track-dedup priority. Most-restrictive recipes go first
     * so they claim their natural picks; most-permissive last so it
     * picks up the leftovers.
     *
     *  - First Listen (1.0 discovery, library-blind) — has no opinion on
     *    the library pool; claims TAG_GRAPH survivors first.
     *  - Recommended by Last.fm (1.0 discovery, library-blind; issue #255)
     *    — same reasoning as First Listen: a pure-discovery recipe claims
     *    its TRACK_SIMILAR survivors before library-mixed recipes see them.
     *  - Deep Cuts (0.85 discovery) — claims TRACK_SIMILAR survivors and
     *    a sparse library slice next.
     *  - Daily Discover (0.85 discovery) — most permissive on the
     *    library slice; claims ARTIST_SIMILAR survivors last.
     *  - Non-builtins / unknown — last (99).
     *
     * Keyed by name rather than id because builtin id values aren't
     * stable across reseeds.
     */
    private fun recipeDedupPriority(recipe: StashMixRecipeEntity): Int = when (recipe.name) {
        "First Listen" -> 1
        "Deep Cuts" -> 3
        "Daily Discover" -> 4
        else -> 99
    }

    /**
     * Find-or-create a playlist row for this recipe, then replace its
     * tracklist with [tracks] in correct order. Returns a
     * [MaterializeResult] with the playlist_id and the discovery-survivor
     * track ids actually inserted, so the caller can fold those into the
     * cross-mix excludeIds accumulator for subsequent recipes.
     */
    private suspend fun materializeMix(
        recipe: StashMixRecipeEntity,
        tracks: List<TrackEntity>,
        now: Long,
        excludeIds: Set<Long>,
        rotationSeed: Long,
    ): MaterializeResult {
        // Existing playlist: verify it's still there (could have been
        // deleted by the user). If gone, fall through to re-create.
        // Else find the mix's row by source_id. Two cases leave the recipe
        // without the id of a row that exists: two runs that started together
        // (both read the recipe before either created the playlist; on first
        // launch the one-shot and the daily refresh do exactly that), and a
        // run that created the row but died before recipeDao.setPlaylistId.
        // Inserting it again would throw on the UNIQUE source_id index, and a
        // periodic run that throws is FAILED for good.
        val sourceId = "stash_mix_${recipe.id}"
        val existing = recipe.playlistId
            ?.let { playlistDao.getById(it) }
            ?.takeIf { it.type == PlaylistType.STASH_MIX }
            ?: playlistDao.findBySourceId(sourceId)?.takeIf { it.type == PlaylistType.STASH_MIX }

        // Keep the existing playlist and its stable ID, but materialize only
        // local library results. Previously discovered songs already in the
        // user's library remain available as normal local tracks.
        val finalOrderedIds = tracks.map { it.id }
        val discoveryTrackIds = emptyList<Long>()
        if (existing != null) {
            val current = playlistDao.getOrderedTrackIdsForPlaylist(existing.id)
            if (current == finalOrderedIds) {
                playlistDao.updateName(existing.id, recipe.name)
                return MaterializeResult(existing.id, discoveryTrackIds)
            }
        }

        // Rewrite membership ATOMICALLY (audit: the old clear → reinsert →
        // count ran as separate autocommits, so a concurrent track delete —
        // user delete or the orphan sweep — hit insertCrossRef with FK 787
        // and left the mix a torn prefix, whose unlinked survivors the next
        // sweep then garbage-collected). Inside one transaction the same
        // race rolls the whole rewrite back: the mix keeps its previous
        // membership and the next refresh simply retries. totalCount ==
        // finalOrderedIds.size, so the count lands inside the txn too.
        val nowInstant = Instant.ofEpochMilli(now)
        val playlistId = if (existing != null) {
            playlistDao.replaceMixMembership(
                playlistId = existing.id,
                orderedTrackIds = finalOrderedIds,
                name = recipe.name,
                addedAt = nowInstant,
            )
            existing.id
        } else {
            val firstArt = tracks.firstNotNullOfOrNull { it.albumArtUrl }
            val newPlaylist = PlaylistEntity(
                name = recipe.name,
                source = MusicSource.BOTH,
                sourceId = sourceId,
                type = PlaylistType.STASH_MIX,
                trackCount = tracks.size,
                artUrl = firstArt,
                syncEnabled = true,
                isActive = true,
            )
            playlistDao.createMixWithMembership(newPlaylist, finalOrderedIds, nowInstant)
        }

        // v0.9.40: build an album mosaic from the FULL linked track set
        // (library slice + stream-only discovery survivors), not just the
        // library `tracks` — otherwise 100%-discovery mixes (every custom mix
        // and First Listen) get a blank cover even though their stream-only
        // tracks carry album art. Up to 4 distinct arts, "|"-joined: the mix
        // card renders a tile mosaic, and single-image call sites take the
        // first (PlaylistMapper). Recomputed every refresh, so the mosaic
        // fills in once stub art is backfilled. Cosmetic, so it deliberately
        // stays OUTSIDE the membership transaction, reading the committed rows.
        val coverArtUrls = playlistDao.getCoverArtUrlsForPlaylist(playlistId, limit = 4)
        if (coverArtUrls.isNotEmpty()) {
            playlistDao.updateArtUrl(playlistId, coverArtUrls.joinToString("|"))
        }
        return MaterializeResult(playlistId, discoveryTrackIds)
    }

}
