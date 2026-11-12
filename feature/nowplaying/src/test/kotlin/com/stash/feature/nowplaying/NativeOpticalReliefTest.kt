package com.stash.feature.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.stash.feature.nowplaying.ui.SonoraOpticalTimelineView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ensures the native optical View, rather than an obscured Compose overlay,
 *  draws the material while retaining a neutral future rail. */
@RunWith(RobolectricTestRunner::class)
class NativeOpticalReliefTest {
    @Test fun `native renderer draws played material but no relief ahead of thumb`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = SonoraOpticalTimelineView(context)
        view.configure(intArrayOf(0xffff8750.toInt(), 0xffb477fd.toInt()),
            floatArrayOf(0f, 1f), 0xff292735.toInt())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(96, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 600, 96)
        view.setFraction(.5f)
        val bitmap = Bitmap.createBitmap(600, 96, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            // The relief is bounded by the thumb. In the untouched future
            // region, only the thin neutral center line may remain.
            val played = 180
            val future = 430
            val y = 48
            fun coverage(x: Int): Int {
                var total = 0
                for (dy in -12..-2) {
                    total += (bitmap.getPixel(x, y + dy) ushr 24) and 255
                }
                return total
            }
            assertTrue("Played segment must show visible volume beyond the core", coverage(played) > coverage(future))
            assertEquals("Future must remain visually neutral", 0, bitmap.getPixel(future, y - 12) ushr 24)
        } finally {
            bitmap.recycle()
        }
    }
}
