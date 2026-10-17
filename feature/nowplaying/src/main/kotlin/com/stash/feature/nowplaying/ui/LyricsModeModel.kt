package com.stash.feature.nowplaying.ui

import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.TtmlLyrics
import kotlin.math.max
import kotlin.math.min

/*
 * The pure half of Now Playing's lyrics mode: which body to draw for a [LyricsViewState], which
 * items its small menu offers, and the web player's type scale (stash-web
 * player/src/lib/ui/lyrics/lyrics.css + model.ts). Unit-tested in LyricsModeModelTest.
 */

/** What the lyrics area draws. */
internal sealed interface LyricsBody {
    data object Loading : LyricsBody

    /** Word-synced: the syllable renderer's sweep, lift and distance blur. */
    data class Words(val lyrics: TtmlLyrics) : LyricsBody

    /** Line-synced: the line renderer. */
    data class Lines(val lines: List<LrcLine>) : LyricsBody

    /** Unsynced text: a plain scroll. */
    data class Plain(val text: String) : LyricsBody

    /** Nothing to sing along to: a calm one-liner, with "Try again" where a retry can help. */
    data class Message(val text: String, val canRetry: Boolean) : LyricsBody
}

internal const val NO_LYRICS_MESSAGE = "No lyrics for this song"
internal const val INSTRUMENTAL_MESSAGE = "♪ Instrumental"
internal const val LYRICS_ERROR_MESSAGE = "Couldn't load lyrics"

internal fun lyricsBodyFor(state: LyricsViewState): LyricsBody = when (state) {
    LyricsViewState.Loading -> LyricsBody.Loading
    // A TTML body that parsed to no lines would draw nothing at all: fall back to the LRC lines.
    // Those carry their instrumental breaks as the view shows them (short gaps out, see
    // [linesWithBreaks]), the same list the live bar follows.
    is LyricsViewState.Synced -> state.syllables?.takeIf { it.lines.isNotEmpty() }
        ?.let { LyricsBody.Words(it) }
        ?: LyricsBody.Lines(linesWithBreaks(state.lines))
    is LyricsViewState.Plain -> LyricsBody.Plain(state.text)
    LyricsViewState.Instrumental -> LyricsBody.Message(INSTRUMENTAL_MESSAGE, canRetry = false)
    // "None" can be a stale miss (a source was down when it last looked), so it keeps a quiet retry.
    LyricsViewState.None -> LyricsBody.Message(NO_LYRICS_MESSAGE, canRetry = true)
    is LyricsViewState.Error -> LyricsBody.Message(LYRICS_ERROR_MESSAGE, canRetry = state.retryable)
}

/** The lyrics menu's items for the current state (the "Live line" switch is always there). */
internal data class LyricsMenuItems(
    /**
     * Offset: synced lyrics with somewhere to keep an offset: the song's lyrics row, or the output
     * playing now (so a stream, which has no row, can still calibrate the headphones).
     */
    val offset: Boolean,
    /** Save with song file: a downloaded track with lyrics on screen. */
    val saveToFile: Boolean,
)

internal fun lyricsMenuItemsFor(
    state: LyricsViewState,
    offsetMs: Long?,
    canSaveToFile: Boolean,
    outputKnown: Boolean = false,
): LyricsMenuItems = LyricsMenuItems(
    offset = state is LyricsViewState.Synced && (offsetMs != null || outputKnown),
    saveToFile = canSaveToFile && (state is LyricsViewState.Synced || state is LyricsViewState.Plain),
)

/** Side padding of the lyric column, the same 24dp margin the rest of Now Playing keeps. */
internal const val LYRICS_SIDE_PAD_DP = 24f

/** The web's ceiling on phones (`--lyr-max: 34px`). */
private const val LYRICS_MAX_FONT_SP = 34f

/** Lines this long or shorter keep the full size; the web's `fit` rule. */
private const val FIT_CHARS = 25

/** No line shrinks below this share of the full size. */
private const val FIT_MIN = 0.6f

/**
 * Lyric size for a column [widthDp] wide: `min(34, (width - 2 pad) / 14)`, so about 14 bold em fit
 * a line (~24sp on a 393dp Pixel 5).
 */
internal fun lyricFontSizeSp(widthDp: Float): Float =
    min(LYRICS_MAX_FONT_SP, max(0f, widthDp - 2 * LYRICS_SIDE_PAD_DP) / 14f)

/** A long line shrinks by 25 / its length, never below 60%: most lines then hold on one row. */
internal fun lyricLineFit(text: String): Float {
    val chars = text.codePointCount(0, text.length)
    return if (chars <= FIT_CHARS) 1f else max(FIT_MIN, FIT_CHARS.toFloat() / chars)
}

/** Where the live line's top sits in the lyrics area: `max(1.2 em, 16% of its height)`. */
internal fun lyricAnchorPx(fontPx: Float, viewportPx: Float): Float = max(1.2f * fontPx, 0.16f * viewportPx)

/** Opacity supplies most of the depth; the next two lines remain readable and sharp. */
internal fun lyricUpcomingAlpha(distance: Int): Float = when {
    distance <= 1 -> .84f
    distance == 2 -> .70f
    distance == 3 -> .56f
    distance == 4 -> .44f
    else -> .36f
}

/** A small, bounded softening only for distant lines, including at large font scales. */
internal fun lyricBlurDp(distance: Int, fontSp: Float): Float {
    if (distance <= 2) return 0f
    return (fontSp * if (distance == 3) .012f else .022f).coerceIn(0f, .75f)
}
