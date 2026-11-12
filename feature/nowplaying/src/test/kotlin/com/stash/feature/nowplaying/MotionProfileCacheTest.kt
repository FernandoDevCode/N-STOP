package com.stash.feature.nowplaying

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.stash.core.media.output.MusicMotionFeatures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionProfileCacheTest {
    @Test fun `cached profile is versioned and only stores mature analysis`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = MotionProfileCache(context)
        val key = "sonora_test_unique_recording"
        val profile = MusicMotionFeatures(.60f,.25f,.15f,.75f,.2f,.3f,true,160)
        cache.write(key,profile)
        val restored = cache.read(key)
        assertNotNull(restored)
        assertEquals(profile.flow, restored!!.flow, .0001f)
        assertEquals(profile.tension, restored.tension, .0001f)
        assertEquals(0f, restored.attack, 0f)
        context.getSharedPreferences("sonora_motion_v1",Context.MODE_PRIVATE)
            .edit().remove(key).commit()
    }

    @Test fun `unfinished and invalid profiles are not persisted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = MotionProfileCache(context)
        val key = "sonora_test_immature"
        cache.write(key,MusicMotionFeatures(.6f,.2f,.2f,.4f,.2f,.3f,true,12))
        assertNull(cache.read(key))
    }
}
