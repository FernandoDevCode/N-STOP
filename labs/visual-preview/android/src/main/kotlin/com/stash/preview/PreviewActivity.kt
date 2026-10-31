package com.stash.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.stash.core.ui.R
import com.stash.core.ui.visual.*

/** Separate application ID, no permissions, services, accounts or production Application. */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = intent.getStringExtra("theme") ?: "dark"
        val bars = if (theme == "light") SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val page = intent.getStringExtra("page")?.let { value -> VisualPage.entries.firstOrNull { it.name.equals(value, true) } } ?: VisualPage.Home
        val scenario = VisualScenario.parse(intent.getStringExtra("scenario"))
        setContent {
            val resources = resources
            val bitmaps = remember { mapOf(
                "reference_home" to ImageBitmap.imageResource(resources, R.drawable.reference_home),
                "reference_explore" to ImageBitmap.imageResource(resources, R.drawable.reference_explore),
                "reference_search" to ImageBitmap.imageResource(resources, R.drawable.reference_search),
                "reference_library" to ImageBitmap.imageResource(resources, R.drawable.reference_library)) }
            VisualTheme(theme != "light", theme == "amoled") { VisualBackground(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                    VisualPreview(page, dark = theme != "light", amoled = theme == "amoled", scenario = scenario, artwork = { key, modifier -> ReferenceArtwork(key, modifier) { bitmaps.getValue(it) } })
                }
            } }
        }
    }
}
