package com.stash.feature.nowplaying

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class V8FailureBoundaryTest {
    @Test fun layoutFailureRequestsFunctionalFallbackOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var failures = 0
        val host = GuardedPlayerView(context) { failures++ }
        host.removeAllViews()
        host.addView(object : View(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                error("renderer initialization failure")
            }
        })
        val size = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        host.measure(size, size)
        host.fail(IllegalStateException("repeated failure"))
        // A detached View queues posts until attachment; attach via Activity for delivery.
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setContentView(host)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, failures)
    }
}
