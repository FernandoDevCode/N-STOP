package com.stash.feature.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SonoraTrackInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val song = SearchResultItem(
        videoId = "test_video",
        title = "Before I Forget",
        artist = "Slipknot",
        durationSeconds = 248.0,
    )

    @Test
    fun `search row has no always visible download or overflow buttons`() {
        compose.setContent {
            MaterialTheme {
                SongRow(
                    item = song,
                    isDownloading = false,
                    isDownloaded = false,
                    isPreviewLoading = false,
                    isPreviewPlaying = false,
                    onPlay = {},
                    onStopPreview = {},
                    onDownload = {},
                )
            }
        }
        compose.onNodeWithTag("SongRow").assertHasClickAction()
        compose.onNodeWithContentDescription("Download").assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun `long press reveals download and playlist options in a bottom sheet`() {
        compose.setContent {
            MaterialTheme {
                SongRow(
                    item = song,
                    isDownloading = false,
                    isDownloaded = false,
                    isPreviewLoading = false,
                    isPreviewPlaying = false,
                    onPlay = {},
                    onStopPreview = {},
                    onDownload = {},
                    onAddToPlaylist = {},
                )
            }
        }
        compose.onNodeWithTag("SongRow").performTouchInput { longClick() }
        compose.onNodeWithText("Baixar").assertExists()
        compose.onNodeWithText("Adicionar à playlist").assertExists()
        compose.onNodeWithText("Before I Forget").assertExists()
    }

    @Test
    fun `already downloaded music cannot offer a redundant download action`() {
        compose.setContent {
            MaterialTheme {
                SongRow(
                    item = song,
                    isDownloading = false,
                    isDownloaded = true,
                    isPreviewLoading = false,
                    isPreviewPlaying = false,
                    onPlay = {},
                    onStopPreview = {},
                    onDownload = {},
                )
            }
        }
        compose.onNodeWithTag("SongRow").performTouchInput { longClick() }
        compose.onNodeWithText("Baixada").assertExists()
        compose.onNodeWithText("Baixar").assertDoesNotExist()
    }
}
