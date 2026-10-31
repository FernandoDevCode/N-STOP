package com.stash.feature.nowplaying.ui

import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.TtmlGroup
import com.stash.data.lyrics.parser.TtmlLine
import com.stash.data.lyrics.parser.TtmlLyrics
import com.stash.data.lyrics.parser.TtmlSyllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModeModelTest {

    private val lines = listOf(LrcLine(0L, "one"), LrcLine(2_000L, "two"))
    private val ttml = TtmlLyrics(
        lines = listOf(
            TtmlLine(
                oppositeAligned = false,
                lead = TtmlGroup(listOf(TtmlSyllable("one", false, 0L, 500L)), 0L, 500L),
                background = emptyList(),
            ),
        ),
        startMs = 0L,
        endMs = 500L,
    )

    // ── body selection ───────────────────────────────────────────────────────────────────────

    @Test fun `loading stays loading`() {
        assertEquals(LyricsBody.Loading, lyricsBodyFor(LyricsViewState.Loading))
    }

    @Test fun `word-synced lyrics take the syllable renderer`() {
        assertEquals(LyricsBody.Words(ttml), lyricsBodyFor(LyricsViewState.Synced(lines, "", ttml)))
    }

    @Test fun `line-synced lyrics take the line renderer`() {
        assertEquals(LyricsBody.Lines(lines), lyricsBodyFor(LyricsViewState.Synced(lines, "")))
    }

    @Test fun `line-synced lyrics carry long breaks and drop short ones`() {
        val withBreaks = listOf(
            LrcLine(0L, "one"),
            LrcLine(2_000L, ""), // a breath before "two": dropped
            LrcLine(3_000L, "two"),
            LrcLine(5_000L, ""), // a 15 s solo: the ♪ row
            LrcLine(20_000L, "three"),
        )
        assertEquals(
            LyricsBody.Lines(listOf(LrcLine(0L, "one"), LrcLine(3_000L, "two"), LrcLine(5_000L, ""), LrcLine(20_000L, "three"))),
            lyricsBodyFor(LyricsViewState.Synced(withBreaks, "")),
        )
    }

    @Test fun `an empty TTML body falls back to the lines rather than drawing nothing`() {
        val empty = TtmlLyrics(emptyList(), 0L, 0L)
        assertEquals(LyricsBody.Lines(lines), lyricsBodyFor(LyricsViewState.Synced(lines, "", empty)))
    }

    @Test fun `plain lyrics scroll as text`() {
        assertEquals(LyricsBody.Plain("words"), lyricsBodyFor(LyricsViewState.Plain("words")))
    }

    @Test fun `no lyrics is a calm message with a quiet retry`() {
        assertEquals(LyricsBody.Message(NO_LYRICS_MESSAGE, canRetry = true), lyricsBodyFor(LyricsViewState.None))
    }

    @Test fun `instrumental is a message without a retry`() {
        assertEquals(LyricsBody.Message(INSTRUMENTAL_MESSAGE, canRetry = false), lyricsBodyFor(LyricsViewState.Instrumental))
    }

    @Test fun `an error retries only when it is retryable`() {
        assertEquals(LyricsBody.Message(LYRICS_ERROR_MESSAGE, true), lyricsBodyFor(LyricsViewState.Error(retryable = true)))
        assertEquals(LyricsBody.Message(LYRICS_ERROR_MESSAGE, false), lyricsBodyFor(LyricsViewState.Error(retryable = false)))
    }

    // ── the menu ─────────────────────────────────────────────────────────────────────────────

    @Test fun `offset needs synced lyrics and a row to store it on`() {
        val synced = LyricsViewState.Synced(lines, "")
        assertTrue(lyricsMenuItemsFor(synced, offsetMs = 0L, canSaveToFile = false).offset)
        assertFalse(lyricsMenuItemsFor(synced, offsetMs = null, canSaveToFile = false).offset)
        assertFalse(lyricsMenuItemsFor(LyricsViewState.Plain("x"), offsetMs = 0L, canSaveToFile = false).offset)
    }

    @Test fun `offset is there for a stream once the output is known, to calibrate it`() {
        val synced = LyricsViewState.Synced(lines, "")
        assertTrue(lyricsMenuItemsFor(synced, offsetMs = null, canSaveToFile = false, outputKnown = true).offset)
        assertFalse(lyricsMenuItemsFor(LyricsViewState.Plain("x"), offsetMs = null, canSaveToFile = false, outputKnown = true).offset)
    }

    @Test fun `save with song file needs a download and lyrics on screen`() {
        assertTrue(lyricsMenuItemsFor(LyricsViewState.Plain("x"), null, canSaveToFile = true).saveToFile)
        assertTrue(lyricsMenuItemsFor(LyricsViewState.Synced(lines, ""), null, canSaveToFile = true).saveToFile)
        assertFalse(lyricsMenuItemsFor(LyricsViewState.Synced(lines, ""), null, canSaveToFile = false).saveToFile)
        assertFalse(lyricsMenuItemsFor(LyricsViewState.None, null, canSaveToFile = true).saveToFile)
        assertFalse(lyricsMenuItemsFor(LyricsViewState.Loading, null, canSaveToFile = true).saveToFile)
    }

    // ── type scale ───────────────────────────────────────────────────────────────────────────

    @Test fun `phone lyrics retain a readable shared type scale and cap on large screens`() {
        assertEquals((393f - 48f) / 13f, lyricFontSizeSp(393f), 0.001f)
        assertEquals(34f, lyricFontSizeSp(900f), 0.001f)
        assertEquals(0f, lyricFontSizeSp(10f), 0.001f)
    }

    @Test fun `long phrases wrap without reducing the editorial size by more than ten percent`() {
        assertEquals(1f, lyricLineFit("x".repeat(32)), 0f)
        assertEquals(32f / 35f, lyricLineFit("x".repeat(35)), 0.0001f)
        listOf("And you run, and you run to catch up with the sun", "x".repeat(80)).forEach {
            assertTrue(lyricLineFit(it) >= .9f)
        }
    }

    @Test fun `fit counts characters, not UTF-16 units`() {
        // 20 emoji are 40 UTF-16 units but 20 characters: still full size.
        assertEquals(1f, lyricLineFit("🎵".repeat(20)), 0f)
    }

    @Test fun `the live line anchors at 16 percent, or 1_2 em on a short area`() {
        assertEquals(160f, lyricAnchorPx(fontPx = 60f, viewportPx = 1000f), 0.001f)
        assertEquals(120f, lyricAnchorPx(fontPx = 100f, viewportPx = 500f), 0.001f)
    }

    @Test fun `current and next two lines stay sharp with bounded distant blur`() {
        assertEquals(0f, lyricBlurDp(0, 25f), 0f)
        assertEquals(0f, lyricBlurDp(1, 25f), 0f)
        val near = lyricBlurDp(2, 25f)
        val far = lyricBlurDp(3, 25f)
        assertEquals(0f, near, 0f)
        assertTrue(far > near)
        assertTrue(lyricBlurDp(9, 25f) <= .75f)
        assertTrue(lyricBlurDp(9, 100f) <= .75f)
    }

    @Test fun `future lines fade progressively while the next remains legible`() {
        assertTrue(lyricUpcomingAlpha(1) >= .88f)
        assertTrue(lyricUpcomingAlpha(2) >= .75f)
        assertTrue(lyricUpcomingAlpha(1) > lyricUpcomingAlpha(2))
        assertTrue(lyricUpcomingAlpha(2) > lyricUpcomingAlpha(3))
        assertTrue(lyricUpcomingAlpha(3) > lyricUpcomingAlpha(4))
        assertTrue(lyricUpcomingAlpha(4) > lyricUpcomingAlpha(5))
        assertEquals(lyricUpcomingAlpha(5), lyricUpcomingAlpha(100), 0f)
    }
}
