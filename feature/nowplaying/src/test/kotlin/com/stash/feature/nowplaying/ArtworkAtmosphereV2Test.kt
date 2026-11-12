package com.stash.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkAtmosphereV2Test {
    @Test fun `artwork controls environment without procedural fog colors`() {
        fun solid(color: Int): Bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }
        val red = solid(Color.rgb(220, 36, 37))
        val blue = solid(Color.rgb(35, 65, 220))
        try {
            val a = ArtworkAtmosphereV2.render(red)
            val b = ArtworkAtmosphereV2.render(blue)
            assertEquals(ArtworkAtmosphereV2.WIDTH, a.width)
            assertEquals(ArtworkAtmosphereV2.HEIGHT, a.height)
            val mid = ArtworkAtmosphereV2.WIDTH * (ArtworkAtmosphereV2.HEIGHT / 2) +
                ArtworkAtmosphereV2.WIDTH / 2
            val ra = a.argb[mid]
            val bb = b.argb[mid]
            assertTrue(Color.red(ra) > Color.blue(ra))
            assertTrue(Color.blue(bb) > Color.red(bb))
            assertTrue(Color.red(ra) < Color.red(Color.rgb(220,36,37)))
            assertTrue(Color.blue(bb) < Color.blue(Color.rgb(35,65,220)))
        } finally {
            red.recycle()
            blue.recycle()
        }
    }
}
