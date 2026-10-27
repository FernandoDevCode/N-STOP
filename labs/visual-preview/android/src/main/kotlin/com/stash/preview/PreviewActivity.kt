package com.stash.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
        val page = intent.getStringExtra("page")?.let { value -> VisualPage.entries.firstOrNull { it.name.equals(value, true) } } ?: VisualPage.Home
        setContent {
            val resources = resources
            val bitmaps = remember { mapOf(
                "reference_home" to ImageBitmap.imageResource(resources, R.drawable.reference_home),
                "reference_explore" to ImageBitmap.imageResource(resources, R.drawable.reference_explore),
                "reference_search" to ImageBitmap.imageResource(resources, R.drawable.reference_search),
                "reference_library" to ImageBitmap.imageResource(resources, R.drawable.reference_library)) }
            VisualTheme { VisualBackground(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    VisualPreview(page, artwork = { key, modifier -> ReferenceArtwork(key, modifier) { bitmaps.getValue(it) } })
                }
            } }
        }
    }
}
