plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.compose.hot-reload") version "1.0.0"
}

apply(from = "android-resources.gradle.kts")

// Compile the ORIGINAL Android-independent sources, never a copied UI fork.
// JVM sharing is intentional: Android and Desktop both support java.* used by models.
// This is not yet an iOS/Web-compatible commonMain migration.
val uiFiles = listOf(
    "theme/Color.kt", "theme/Theme.kt", "theme/Type.kt", "theme/Shape.kt",
    "theme/Motion.kt", "theme/Elevation.kt", "theme/UiScaleLocals.kt",
    "components/SectionHeader.kt", "components/StashSwitch.kt",
    "theme/NonstopDesign.kt", "components/NonstopNavIcon.kt", "components/GlassCard.kt",
    "components/CrispChipRow.kt", "components/AlbumSquareCard.kt", "components/FlacBadge.kt",
    "components/motion/PressScale.kt", "components/NonstopBottomNavigation.kt",
    "components/TrackListItem.kt", "components/TrackRowFit.kt", "components/TrackRowFitMeasure.kt",
    "components/SourceIndicator.kt", "components/SelectionRowSemantics.kt",
    "components/SaveToPlaylistSheet.kt", "ambient/AmbientPalette.kt", "ambient/WordmarkInk.kt",
)
val settingsFiles = listOf(
    "SettingsTheme.kt", "SettingsGroupCard.kt", "SettingsNavRow.kt",
    "SettingsToggleRow.kt", "SettingsSliderRow.kt",
    "SettingsSearchField.kt", "SettingsSegmented.kt", "SettingsSectionLabel.kt", "SupportBanner.kt",
    "BetaPill.kt",
    "AccountConnectionCard.kt", "AccountStatus.kt",
)
kotlin {
    jvm("desktop")
    jvmToolchain(21)
    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir("../../core/model/src/main/kotlin")
            kotlin.srcDir("../../core/ui/src/main/kotlin")
            kotlin.srcDir("../visual-preview/kotlin")
            kotlin.include("com/stash/core/ui/visual/**", "com/stash/preview/**")
            kotlin.srcDir("../../feature/settings/src/main/kotlin")
            kotlin.srcDir("../../feature/home/src/main/kotlin")
            kotlin.srcDir("../../feature/nowplaying/src/main/kotlin")
            kotlin.include("com/stash/feature/nowplaying/PlayerV7.kt", "com/stash/feature/nowplaying/NowPlayingUiState.kt",
                "com/stash/feature/nowplaying/ui/PlayerArtDirection.kt", "com/stash/feature/nowplaying/ui/GlowingProgressBar.kt",
                "com/stash/feature/nowplaying/ui/SeekBarMath.kt", "com/stash/feature/nowplaying/ui/NowPlayingInk.kt",
                "com/stash/feature/nowplaying/R.kt")
            kotlin.srcDir("../../core/common/src/main/kotlin")
            kotlin.srcDir("../../core/auth/src/main/kotlin")
            kotlin.srcDir("../../core/data/src/main/kotlin")
            kotlin.include("com/stash/core/data/lastfm/LastFmApiClient.kt", "com/stash/core/data/lastfm/LastFmRateLimitGate.kt",
                "com/stash/core/data/lastfm/LastFmApiException.kt", "com/stash/core/data/lastfm/LastFmCacheKey.kt",
                "com/stash/core/data/lastfm/LastFmTrackInfo.kt", "com/stash/core/data/db/dao/LastFmCacheDao.kt",
                "com/stash/core/data/db/entity/LastFmCacheEntity.kt", "com/stash/core/data/lastfm/LastFmArtistInfo.kt",
                "com/stash/core/data/lastfm/LastFmPeriod.kt", "com/stash/core/data/lastfm/LastFmReadKeySelector.kt")
            kotlin.srcDir("../../data/spotify/src/main/kotlin")
            kotlin.include("com/stash/core/auth/model/AuthModels.kt", "com/stash/core/auth/TokenManager.kt",
                "com/stash/core/auth/TokenManagerImpl.kt", "com/stash/core/auth/store/TokenStore.kt",
                "com/stash/core/auth/spotify/**", "com/stash/core/auth/youtube/YouTubeCookieHelper.kt",
                "com/stash/core/auth/discord/**", "com/stash/data/spotify/*.kt", "com/stash/data/spotify/model/**",
                "com/stash/data/ytmusic/InnerTubeClient.kt", "com/stash/data/ytmusic/YTMusicApiClient.kt",
                "com/stash/data/ytmusic/SearchUnavailableException.kt", "com/stash/data/ytmusic/potoken/PoTokenMinter.kt")
            kotlin.srcDir("../../data/ytmusic/src/main/kotlin")
            kotlin.srcDir("../../data/lyrics/src/main/kotlin")
            kotlin.include("com/stash/data/lyrics/parser/LrcParser.kt")
            kotlin.include("com/stash/data/lyrics/source/LyricsSource.kt", "com/stash/data/lyrics/source/LrclibApi.kt",
                "com/stash/data/lyrics/source/LrclibLyricsSource.kt", "com/stash/data/lyrics/di/LrclibBaseUrl.kt",
                "com/stash/core/common/AppVersionProvider.kt")
            kotlin.include("com/stash/data/ytmusic/model/**", "com/stash/data/ytmusic/*Parser.kt",
                "com/stash/data/ytmusic/ResponseParserHelpers.kt", "com/stash/data/ytmusic/JsonNavigation.kt")
            kotlin.include("com/stash/data/ytmusic/YouTubeCredentialUrl.kt")
            kotlin.srcDir(layout.buildDirectory.dir("generated/android-resources/kotlin"))
            kotlin.include("com/stash/core/ui/R.kt", "com/stash/feature/home/R.kt", "com/stash/feature/settings/R.kt", "com/stash/app/R.kt")
            kotlin.include("com/stash/core/common/ArtUrlUpgrader.kt")
            kotlin.include("com/stash/feature/home/NonstopHomeChrome.kt", "com/stash/feature/home/PersonalizeCard.kt")
            kotlin.include("com/stash/core/model/**")
            uiFiles.forEach { kotlin.include("com/stash/core/ui/$it") }
            settingsFiles.forEach { kotlin.include("com/stash/feature/settings/components/$it") }
            kotlin.include("desktop/**", "com/stash/desktop/**")
            resources.srcDir("../../core/ui/src/main/res/font")
            resources.srcDir("../visual-preview/res/drawable")
            resources.srcDir(layout.buildDirectory.dir("generated/android-resources/resources"))
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.coil-kt.coil3:coil-compose:3.1.0")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
                implementation("org.xerial:sqlite-jdbc:3.49.1.0")
                implementation("javax.inject:javax.inject:1")
                implementation("net.java.dev.jna:jna-platform:5.17.0")
                implementation("androidx.room:room-common:2.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
        val desktopTest by getting {
            kotlin.srcDir("../../core/model/src/test/kotlin")
            resources.srcDir("../../core/model/src/test/resources")
            kotlin.srcDir("../../data/ytmusic/src/test/kotlin")
            kotlin.srcDir("../../data/lyrics/src/test/kotlin")
            kotlin.srcDir("../../feature/nowplaying/src/test/kotlin")
            kotlin.srcDir("../../core/auth/src/test/kotlin")
            kotlin.srcDir("../../data/spotify/src/test/kotlin")
            kotlin.include("com/stash/core/model/**", "com/stash/desktop/**",
                "com/stash/data/ytmusic/FlatSearchResponseParserTest.kt", "com/stash/data/ytmusic/SongRowSubtitleParserTest.kt",
                "com/stash/data/lyrics/parser/LrcParserTest.kt", "com/stash/data/lyrics/source/LrclibLyricsSourceTest.kt",
                "com/stash/feature/nowplaying/ui/PlayerArtDirectionTest.kt", "com/stash/feature/nowplaying/ui/SeekBarMathTest.kt")
            kotlin.include("com/stash/core/auth/spotify/SpotifyClientVersionScrapeTest.kt",
                "com/stash/core/auth/spotify/SpotifyLibraryMutationHashTest.kt",
                "com/stash/data/spotify/SpotifyTrackParserTest.kt", "com/stash/data/spotify/SpotifyLibraryParserTest.kt")
            resources.srcDir("../../data/ytmusic/src/test/resources")
            resources.srcDir("../../data/spotify/src/test/resources")
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.uiTestJUnit4)
                implementation("com.google.truth:truth:1.4.4")
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}
compose.desktop {
    application {
        mainClass = "com.stash.desktop.MainKt"
        jvmArgs += "-Dfile.encoding=UTF-8"
        nativeDistributions {
            packageName = "NonstopAppDesktop"
            packageVersion = "0.1.0"
            modules("java.desktop", "java.logging", "java.management", "jdk.unsupported")
        }
    }
}
