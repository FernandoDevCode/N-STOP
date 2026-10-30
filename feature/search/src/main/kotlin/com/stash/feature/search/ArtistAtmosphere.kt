package com.stash.feature.search

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.ui.ambient.AmbientPalette
import com.stash.core.ui.ambient.ambientPaletteFromBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped only to artist profiles; never changes the playing track's chrome. */
internal val ArtistNeutralAtmosphere = Color(0xFF1B232D)
internal val ArtistBaseInk = Color(0xFF0E1115)

/**
 * Pull one restrained background hue from the band's actual photograph.
 * Reuses the app's 32px palette extractor, with a neutral fallback for black
 * and white images or network failures. No brand-coloured controls or AI.
 */
internal suspend fun artistAtmosphereFromPhoto(
    context: Context,
    photoUrl: String?,
): Color {
    if (photoUrl.isNullOrBlank()) return ArtistNeutralAtmosphere
    return try {
        val request = ImageRequest.Builder(context)
            .data(ArtUrlUpgrader.upgrade(photoUrl))
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(context).execute(request)
        }
        if (result !is SuccessResult) return ArtistNeutralAtmosphere
        withContext(Dispatchers.Default) {
            val palette = ambientPaletteFromBitmap(result.image.toBitmap(), "artist:$photoUrl")
            if (palette.key == AmbientPalette.Default.key) {
                ArtistNeutralAtmosphere
            } else {
                // Dark enough for white labels, vivid enough to retain the
                // character of coloured artwork beneath the editorial list.
                palette.vib.copy(alpha = .26f)
                    .compositeOver(palette.dom)
                    .copy(alpha = .9f)
                    .compositeOver(ArtistBaseInk)
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        ArtistNeutralAtmosphere
    }
}
