package com.stash.core.data.artists

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Artist follow is distinct from the legacy name-based local bookmark. */
@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
)

@Singleton
class FollowedArtistsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sonora_followed_artists_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val mutableArtists = MutableStateFlow(read())
    val artists: StateFlow<List<FollowedArtist>> = mutableArtists.asStateFlow()

    fun isFollowing(id: String): Boolean =
        mutableArtists.value.any { it.id == id }

    fun toggle(id: String, name: String) {
        val safeId = id.trim()
        val safeName = name.trim()
        if (safeId.isEmpty() || safeName.isEmpty()) return
        synchronized(lock) {
            val old = mutableArtists.value
            val next = if (old.any { it.id == safeId }) {
                old.filterNot { it.id == safeId }
            } else {
                old + FollowedArtist(safeId, safeName)
            }
            // Commit before publishing the in-memory state, so failed persistence
            // cannot be presented to the user as a successful follow.
            if (prefs.edit().putString(KEY, json.encodeToString(next)).commit()) {
                mutableArtists.value = next
            }
        }
    }

    private fun read(): List<FollowedArtist> =
        runCatching {
            json.decodeFromString<List<FollowedArtist>>(prefs.getString(KEY, "[]") ?: "[]")
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "artists"
    }
}
