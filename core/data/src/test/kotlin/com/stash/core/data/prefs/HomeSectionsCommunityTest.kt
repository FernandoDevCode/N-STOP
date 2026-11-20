package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSectionsCommunityTest {
    private lateinit var context: Context

    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.homeSectionsDataStore.edit { it.clear() }
    }

    @Test fun `old saved opt-in cannot reconnect to the retired community`() = runTest {
        context.homeSectionsDataStore.edit { it[booleanPreferencesKey("community_on")] = true }
        val pref = HomeSectionsPreference(context)
        assertFalse(pref.communityOn.first())
        assertFalse(HomeSection.COMMUNITY in pref.visibleSections.first())

        pref.setCommunityOn(true)
        assertFalse(pref.communityOn.first())
        assertFalse(HomeSection.COMMUNITY in pref.visibleSections.first())
    }

    @Test fun `other Home preferences keep working with community disabled`() = runTest {
        val pref = HomeSectionsPreference(context)
        pref.setShowLikedOnHome(true)
        assertTrue(pref.showLikedOnHome.first())
        pref.setCommunityOn(false)
        assertFalse(HomeSection.COMMUNITY in pref.visibleSections.first())
    }
}
