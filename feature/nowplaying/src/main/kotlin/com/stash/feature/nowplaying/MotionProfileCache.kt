package com.stash.feature.nowplaying

import android.content.Context
import com.stash.core.media.output.MusicMotionFeatures
import kotlin.math.abs

/**
 * Versioned, bounded local cache. Entries are priors, NOT prerecorded
 * beat events. Motion reactivity always requires current live PCM.
 */
internal class MotionProfileCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sonora_motion_v1", Context.MODE_PRIVATE)

    fun read(key: String): MusicMotionFeatures? {
        val raw = prefs.getString(key, null) ?: return null
        val p = raw.split("|")
        if (p.size != 7 || p[0] != "1") return null
        val numbers = p.drop(1).map { it.toFloatOrNull() ?: return null }
        if (numbers.any { !it.isFinite() || it !in 0f..1f }) return null
        return MusicMotionFeatures(numbers[0], numbers[1], numbers[2],
            numbers[3], 0f, numbers[4], true, 120)
    }

    fun write(key: String, features: MusicMotionFeatures) {
        if (!features.valid || features.analyzedWindows < 120 || key.isBlank()) return
        val values = listOf(features.flow, features.elasticity, features.tension,
            features.regularity, features.evolution)
        if (values.any { !it.isFinite() || it !in 0f..1f }) return
        // Keep the cache bounded without storing album images, audio or identifiers in logs.
        val all = prefs.all.keys
        val editor = prefs.edit()
        if (all.size >= 256 && !prefs.contains(key)) {
            // SharedPreferences has no access-order map; evict a deterministic old entry.
            all.sorted().take(all.size - 254).forEach(editor::remove)
        }
        editor.putString(key, "1|" + values.joinToString("|") + "|0")
        editor.apply()
    }
}
