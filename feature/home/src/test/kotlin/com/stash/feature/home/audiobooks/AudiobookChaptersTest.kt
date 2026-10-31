package com.stash.feature.home.audiobooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookChaptersTest {
    @Test fun `only allow real https youtube video identifiers`() {
        val id = "hiFfn4mADiQ"
        assertEquals(id, youtubeVideoId("https://www.youtube.com/watch?v=$id&t=73s"))
        assertEquals(id, youtubeVideoId("https://youtu.be/$id"))
        assertEquals(id, youtubeVideoId("https://m.youtube.com/watch?v=$id"))
        assertNull(youtubeVideoId("http://youtube.com/watch?v=$id"))
        assertNull(youtubeVideoId("https://youtube.com.evil.tld/watch?v=$id"))
        assertNull(youtubeVideoId("https://www.youtube.com/playlist?list=$id"))
        assertNull(youtubeVideoId("https://www.youtube.com/watch?v=invalid"))
        assertNull(youtubeVideoId("https://person@www.youtube.com/watch?v=$id"))
    }

    @Test fun `parse consecutive chapters with unknown and exact boundaries`() {
        val result = parseChapterProposal("""
            1 | 00:00:00 | 00:09:15 | Introdução
            2 | 00:09:15 | ? | Capítulo primeiro
            3 | 00:21:30 | 00:31:50 | Capítulo segundo
        """.trimIndent())
        assertTrue(result.error.orEmpty(), result.valid)
        assertEquals(3, result.chapters.size)
        assertEquals(555000L, result.chapters[1].startMs)
        assertEquals(1290000L, result.chapters[1].endMs)
        assertTrue(result.chapters[1].endEstimated)
        assertFalse(result.chapters[2].endEstimated)
    }

    @Test fun `refuse missing and duplicated chapter numbering`() {
        assertFalse(parseChapterProposal("""
            1 | 00:00 | 05:00 | Primeira parte
            3 | 05:00 | 10:00 | Terceira parte
        """.trimIndent()).valid)
        assertFalse(parseChapterProposal("""
            1 | 00:00 | 05:00 | Primeira parte
            1 | 05:00 | 10:00 | Primeira parte repetida
        """.trimIndent()).valid)
    }

    @Test fun `refuse timestamps that reverse overlap or repeat`() {
        assertFalse(parseChapterProposal("""
            1 | 00:00 | 06:00 | Capítulo um
            2 | 05:50 | 11:00 | Capítulo dois
        """.trimIndent()).valid)
        assertFalse(parseChapterProposal("""
            1 | 00:20 | ? | Um
            2 | 00:20 | ? | Dois
        """.trimIndent()).valid)
        assertFalse(parseChapterProposal("1 | 01:89 | 02:00 | Inválido").valid)
    }

    @Test fun `unavailable last end remains unknown not invented`() {
        val result = parseChapterProposal("1 | 00:00:00 | ? | Capítulo único")
        assertTrue(result.valid)
        assertNull(result.chapters.single().endMs)
    }

    @Test fun `reject malformed response instead of silently skipping numbered line`() {
        val result = parseChapterProposal("""
            1 | 00:00:00 | 00:10:00 | Primeiro
            2 | sem horario | 00:20:00 | Segundo
        """.trimIndent())
        assertFalse(result.valid)
    }

    @Test fun `supports long recordings in hours without imposing a 59 minute limit`() {
        assertEquals(7200000L, chapterTimeMs("120:00"))
        assertEquals(7200000L, chapterTimeMs("02:00:00"))
        assertNull(chapterTimeMs("01:60:00"))
        assertNull(chapterTimeMs("12:60"))
    }
}
