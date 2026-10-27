package com.stash.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.junit.rules.TemporaryFolder

class NativeAppTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private var controller: AppController? = null
    @After fun closeController() { controller?.close() }

    @Test fun accountsDialogCancelsWithoutSavingAndSyncRequiresConnectedService() {
        val app = AppController(LibraryStore(folder.newFolder())).also { controller = it }
        compose.setContent { DesktopApp(app, initialPage = "accounts") }
        compose.waitUntil(5000) { app.accounts.ready.value }
        compose.onAllNodes(hasText("Connect") or hasText("Conectar"))[0].performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Cookie sp_dc")).performTextInput("synthetic-not-submitted")
        compose.onNodeWithText("Cancelar").performClick()
        Assert.assertTrue(app.accounts.tokens.spotifyAuthState.value is com.stash.core.auth.model.AuthState.NotConnected)
        Assert.assertEquals(0, app.accounts.client.dispatcher.runningCallsCount())
        compose.onNodeWithText("Abrir sincronização").performScrollTo().performClick()
        compose.onNodeWithText("Sincronizar agora").assertIsNotEnabled()
    }

    @Test fun originalNavigationOpensLibraryAndPersistsNewPlaylist() {
        val store = LibraryStore(folder.newFolder())
        val app = AppController(store).also { controller = it }
        compose.setContent { DesktopApp(app) }
        compose.onNodeWithTag("visual-tab-Library").performClick()
        compose.onNodeWithText("Sua Biblioteca").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nova playlist").performClick()
        compose.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Playlist Desktop")
        compose.onNodeWithText("Criar", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { app.library.value.playlists.any { it.name == "Playlist Desktop" } }
        compose.onNodeWithText("Playlist Desktop").assertIsDisplayed()
        Assert.assertEquals("Playlist Desktop", store.load().playlists.single().name)
    }

    @Test fun originalTrackRowsSupportLocalSearchLikesAndPlaylistMembership() {
        val store = LibraryStore(folder.newFolder())
        store.save(LibrarySnapshot(listOf(SavedTrack(8, "Faixa de teste", "Artista de teste", duration = 60000)),
            listOf(SavedPlaylist(1, "Favoritas de teste", "LOCAL", "CUSTOM"))))
        val app = AppController(store).also { controller = it }
        compose.setContent { DesktopApp(app) }
        compose.onNodeWithTag("visual-tab-Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Faixa de teste")
        compose.onNodeWithText("Na sua biblioteca").assertIsDisplayed()
        compose.onNode(hasText("Faixa de teste") and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Curtir / descurtir na biblioteca local").performClick()
        compose.waitUntil(5000) { app.library.value.tracks.single().liked }
        Assert.assertTrue(store.load().tracks.single().liked)
        compose.waitUntil(5000) { app.busy.value == null }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Adicionar à playlist").performClick()
        compose.onNodeWithText("Favoritas de teste").performClick()
        compose.waitUntil(5000) { app.library.value.playlists.single().trackIds == listOf(8L) }
        Assert.assertEquals(listOf(8L), store.load().playlists.single().trackIds)
    }
}
