package com.stash.feature.home

import android.content.Context
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.TrackSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.temporal.WeekFields
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** Private, bounded weekly snapshot: only public catalog IDs/metadata, no listening history. */
@Singleton
class SonoraWeeklyDiscoveryCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sonora_weekly_discovery_v1", Context.MODE_PRIVATE)

    fun read(libraryFingerprint: String): SonoraWeeklyDiscovery? {
        val raw = prefs.getString("snapshot", null) ?: return null
        return try {
            val json = JSONObject(raw)
            if (json.optString("week") != currentWeek() ||
                json.optString("library") != libraryFingerprint) return null
            val releases = json.optJSONArray("releases")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    val obj = arr.optJSONObject(index) ?: return@mapNotNull null
                    val id = obj.optString("id")
                    val title = obj.optString("title")
                    val artist = obj.optString("artist")
                    if (id.isBlank() || title.isBlank() || artist.isBlank()) return@mapNotNull null
                    AlbumSummary(id, title, artist, obj.stringOrNull("art"), obj.stringOrNull("year"),
                        AlbumSource.YOUTUBE)
                }
            }.orEmpty()
            fun tracks(key: String): List<TrackSummary> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { index ->
                    val obj = arr.optJSONObject(index) ?: return@mapNotNull null
                    val videoId = obj.optString("videoId")
                    val title = obj.optString("title")
                    val artist = obj.optString("artist")
                    if (videoId.isBlank() || title.isBlank() || artist.isBlank()) return@mapNotNull null
                    TrackSummary(videoId, title, artist, obj.stringOrNull("album"),
                        obj.optDouble("duration", 0.0), obj.stringOrNull("art"))
                }
            }
            val value = SonoraWeeklyDiscovery(
                releases, tracks("current"), tracks("forgotten"),
                json.stringOrNull("currentTag"), json.stringOrNull("forgottenTag"),
            )
            if (value.releases.isEmpty() && value.current.isEmpty() && value.forgotten.isEmpty()) null
            else value
        } catch (_: Exception) {
            null
        }
    }

    fun write(data: SonoraWeeklyDiscovery, libraryFingerprint: String) {
        if (data.releases.isEmpty() && data.current.isEmpty() && data.forgotten.isEmpty()) return
        try {
            val root = JSONObject().put("week", currentWeek())
                .put("library", libraryFingerprint)
            val releases = JSONArray()
            data.releases.take(10).forEach {
                releases.put(JSONObject().put("id", it.id).put("title", it.title)
                    .put("artist", it.artist).put("art", it.thumbnailUrl).put("year", it.year))
            }
            fun tracks(values: List<TrackSummary>): JSONArray = JSONArray().also { array ->
                values.take(10).forEach {
                    array.put(JSONObject().put("videoId", it.videoId).put("title", it.title)
                        .put("artist", it.artist).put("album", it.album)
                        .put("duration", it.durationSeconds).put("art", it.thumbnailUrl))
                }
            }
            root.put("releases", releases).put("current", tracks(data.current))
                .put("forgotten", tracks(data.forgotten))
                .put("currentTag", data.currentTag).put("forgottenTag", data.forgottenTag)
            prefs.edit().putString("snapshot", root.toString()).apply()
        } catch (_: Exception) {
            // Cache failure never hides live recommendations.
        }
    }

    private fun JSONObject.stringOrNull(key: String) =
        optString(key).takeUnless { it.isBlank() || it == "null" }

    private fun currentWeek(): String {
        val date = LocalDate.now()
        val iso = WeekFields.ISO
        return "${date.get(iso.weekBasedYear())}-${date.get(iso.weekOfWeekBasedYear())}"
    }
}
