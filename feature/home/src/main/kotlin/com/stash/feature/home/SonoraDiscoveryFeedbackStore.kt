package com.stash.feature.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit negative feedback; no profile uploaded and no credentials stored. */
@Singleton
class SonoraDiscoveryFeedbackStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sonora_discovery_feedback_v1", Context.MODE_PRIVATE)

    fun rejectedVideoIds(): Set<String> =
        prefs.getStringSet("rejected", emptySet()).orEmpty().toSet()

    fun reject(videoId: String) {
        if (videoId.isBlank()) return
        val updated = (rejectedVideoIds().toList().takeLast(249) + videoId).toSet()
        prefs.edit().putStringSet("rejected", updated).apply()
    }
}
