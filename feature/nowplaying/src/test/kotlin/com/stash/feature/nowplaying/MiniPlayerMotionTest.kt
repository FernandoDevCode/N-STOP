package com.stash.feature.nowplaying

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun removalOfMotionAndVisibilityStopFrameClock() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        var visible by mutableStateOf(true)
        var playing by mutableStateOf(true)
        lateinit var friction: MiniFrictionState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val seek = remember { MiniPlayerSeekState() }
            friction = rememberMiniPlayerFriction(seek, playing = playing, buffering = false, visible = visible)
            Box(Modifier.width(360.dp)) {
                MiniPlayerSeekTarget(remember { mutableStateOf(50_000L) }, 100_000L, seek, true, {},
                    Modifier.miniPlayerFriction(remember { mutableStateOf(50_000L) }, 100_000L, seek, friction))
            }
        }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val first = friction.frame
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        assertTrue(friction.frame > first)
        // Emulate a missed draw: seed particles, pause, advance only the frame clock.
        compose.runOnIdle {
            friction.particles.step(.04f, androidx.compose.ui.geometry.Offset(100f, 23f), 1f, true, false, false, 0f)
            assertTrue(friction.particles.alive > 0)
            playing = false
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(0, friction.particles.alive)
        val pausedFrame = friction.frame
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals(pausedFrame, friction.frame)
        compose.runOnIdle { playing = true }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.runOnIdle {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            shadowOf(Looper.getMainLooper()).idle()
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertFalse(friction.motion)
        assertEquals(0, friction.particles.alive)
        val reducedFrame = friction.frame
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(reducedFrame, friction.frame)
        compose.runOnIdle {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            shadowOf(Looper.getMainLooper()).idle()
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertTrue(friction.motion)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val hiddenFrame = friction.frame
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertFalse(friction.motion)
        assertEquals(hiddenFrame, friction.frame)
    }
}
