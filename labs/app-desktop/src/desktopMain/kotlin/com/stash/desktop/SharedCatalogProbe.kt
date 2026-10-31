package com.stash.desktop

import com.stash.core.auth.model.AuthService
import com.stash.core.data.lastfm.LastFmCredentials
import com.stash.data.ytmusic.model.SearchResultSection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files

/** Live read-only check of the original Android client on the Desktop, with no saved sessions. */
internal fun probeSharedCatalog() = runBlocking {
    val temporary = Files.createTempDirectory("nonstop-shared-probe-")
    val accounts = DesktopAccounts(temporary.toFile(), initialLastFmCredentials = LastFmCredentials("", ""))
    try {
        accounts.ready.first { it }
        check(!accounts.tokens.isAuthenticated(AuthService.YOUTUBE_MUSIC))
        val result = accounts.youtube.searchAll("Megadeth Holy Wars")
        check(result.sections.any { it is SearchResultSection.Songs })
        println("Cliente YouTube original no Desktop: ${result.sections.size} seções públicas verificadas")
        val canonical = accounts.youtube.searchCanonicalMatch("Megadeth", "Holy Wars")
        check(canonical != null)
        check(!accounts.tokens.isAuthenticated(AuthService.YOUTUBE_MUSIC))
        println("Correspondência de faixa Spotify→YouTube pelo cliente original: verificada sem conta ou saída sonora")
    } finally { accounts.close(); Files.deleteIfExists(temporary) }
}
