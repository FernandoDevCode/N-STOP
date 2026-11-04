package com.stash.core.data.artists

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowedArtistsStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        context.getSharedPreferences("sonora_followed_artists_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun `follow survives store recreation and unfollow survives recreation`() {
        val first = FollowedArtistsStore(context)
        first.toggle("UC_artist", "Mothica")
        assertTrue(first.isFollowing("UC_artist"))
        assertEquals(listOf(FollowedArtist("UC_artist", "Mothica")), first.artists.value)

        val restored = FollowedArtistsStore(context)
        assertTrue(restored.isFollowing("UC_artist"))
        restored.toggle("UC_artist", "Mothica")
        assertFalse(FollowedArtistsStore(context).isFollowing("UC_artist"))
    }

    @Test fun `follows are keyed by stable id not display name`() {
        val store = FollowedArtistsStore(context)
        store.toggle("UC_a", "Same Name")
        store.toggle("UC_b", "Same Name")
        assertEquals(2, store.artists.value.size)
        store.toggle("UC_a", "Renamed")
        assertEquals(listOf("UC_b"), store.artists.value.map { it.id })
    }

    @Test fun `corrupt preferences fail closed without overwriting bookmarks`() {
        val bookmarks = context.getSharedPreferences("sonora_artist_bookmarks_v1",
            Context.MODE_PRIVATE)
        bookmarks.edit().putBoolean("artist:mothica", true).commit()
        context.getSharedPreferences("sonora_followed_artists_v1", Context.MODE_PRIVATE)
            .edit().putString("artists", "{garbage").commit()
        assertTrue(FollowedArtistsStore(context).artists.value.isEmpty())
        assertTrue(bookmarks.getBoolean("artist:mothica", false))
    }
}
