package com.stash.feature.community

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.community.CommunityPost
import org.junit.Test

class CommunityRowsTest {
    private val day = 86_400_000L
    private val now = 10 * day

    private fun post(kind: String = "playlist", mine: Boolean = false, age: Long = 3 * day) = CommunityPost(
        id = "AAAAAAAA", kind = kind, title = "sad boy hours", name = "Maya", count = 42,
        artist = "Death Plus".takeIf { kind == "song" }, createdAt = now - age, up = 18, down = 0, mine = mine,
    )

    @Test fun `rows say what the post is and who posted it`() {
        assertThat(post().subtitle(withAge = false, now)).isEqualTo("Playlist · 42 músicas · Maya")
        assertThat(post(kind = "mix").subtitle(withAge = true, now)).isEqualTo("Mix · 42 músicas · Maya · há 3 dias")
        // TalkBack gets the age spelled out.
        assertThat(post(kind = "mix").subtitle(withAge = true, now, longAge = true)).isEqualTo("Mix · 42 músicas · Maya · há 3 dias")
        assertThat(post(kind = "song").subtitle(withAge = false, now)).isEqualTo("Música · Death Plus · Maya")
        // Your own post is tagged YOU, so its age takes the name's place.
        assertThat(post(mine = true, age = 30_000).subtitle(withAge = false, now)).isEqualTo("Playlist · 42 músicas · agora")
    }

    @Test fun `ages read short in lists and long on a post`() {
        assertThat(ago(now - 5 * 60_000, now)).isEqualTo("há 5 min")
        assertThat(ago(now - 4 * 3_600_000, now)).isEqualTo("há 4 h")
        assertThat(ago(now - day, now, long = true)).isEqualTo("ontem")
        assertThat(ago(now - 3 * day, now, long = true)).isEqualTo("há 3 dias")
    }

    @Test fun `a song's cover is its art, a playlist's its covers`() {
        assertThat(post(kind = "song").copy(art = "https://i.ytimg.com/a").coverUrls()).containsExactly("https://i.ytimg.com/a")
        assertThat(post().copy(covers = listOf("https://i.scdn.co/1", "https://i.scdn.co/2")).coverUrls()).hasSize(2)
    }
}
