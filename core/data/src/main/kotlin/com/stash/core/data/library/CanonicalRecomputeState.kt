package com.stash.core.data.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Its own file, so a corrupt write can only reset this state, and a reset only means the
 * recompute runs once more (it changes nothing the second time).
 */
private val Context.canonicalRecomputeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "canonical_recompute",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Whether the one-time [CanonicalRecompute] has finished on this install, and where a stopped run
 * resumes. The gate that keeps it to once: startup enqueues the worker only while [isDone] is
 * false, and the worker returns at once when it is true.
 *
 * Re-armed ([rearm]) when a backup is restored or merged: a backup made before schema 55 brings
 * back rows in the old strict words.
 */
@Singleton
class CanonicalRecomputeState internal constructor(
    private val store: DataStore<Preferences>,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.canonicalRecomputeDataStore)

    /** True once a run reached the last row under the current [RULE_VERSION]. */
    suspend fun isDone(): Boolean = (store.data.first()[DONE_VERSION] ?: 0) >= RULE_VERSION

    /** The last row id a stopped run finished; 0 to start from the first row. */
    suspend fun cursor(): Long = store.data.first()[CURSOR] ?: 0L

    /** Records that every row up to [lastId] is done, so a stopped run resumes after it. */
    suspend fun saveCursor(lastId: Long) {
        store.edit { it[CURSOR] = lastId }
    }

    /** Marks the recompute finished for [RULE_VERSION]; the cursor is no longer needed. */
    suspend fun markDone() {
        store.edit {
            it[DONE_VERSION] = RULE_VERSION
            it.remove(CURSOR)
        }
    }

    /** Asks for a fresh run from the first row (a restored or merged backup may hold old rows). */
    suspend fun rearm() {
        store.edit {
            it.remove(DONE_VERSION)
            it.remove(CURSOR)
        }
    }

    companion object {
        /**
         * Bump to run the recompute again on every install, e.g. if [CanonicalRecompute] learns a
         * new legacy form to recognise. Never needed for rows written by the current rule.
         */
        const val RULE_VERSION = 1

        private val DONE_VERSION = intPreferencesKey("done_rule_version")
        private val CURSOR = longPreferencesKey("cursor_after_id")

        /** The state over the process-wide DataStore, for code that has a Context but no injection. */
        fun forContext(context: Context): CanonicalRecomputeState =
            CanonicalRecomputeState(context.applicationContext.canonicalRecomputeDataStore)
    }
}
