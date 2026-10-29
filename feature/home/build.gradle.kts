plugins {
    id("stash.android.feature")
}
android {
    namespace = "com.stash.feature.home"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Return Kotlin defaults from stubbed Android SDK methods so
            // android.util.Log calls in production code don't throw in JVM tests.
            isReturnDefaultValues = true
        }
    }
}
dependencies {
    // :core:auth was used by HomeViewModel for TokenManager / AuthState
    // to feed the SyncStatusCard's "Connect Spotify / YouTube" prompt.
    // Both moved to :feature:sync along with the card.
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    // Still required after the SyncStatusCard relocation: the lossless
    // retry/backfill banners on Home read MetadataBackfillState,
    // LosslessRetryWorker, KennyySource, QobuzSource, and the
    // AggregatorRateLimiter from this module.
    implementation(project(":data:download"))
    // v0.9.36: LyricsBackfillState snapshot for the LyricsBackfillBanner
    // on Home — mirrors the :data:download dependency for the v0.9.35
    // metadata banner.
    implementation(project(":data:lyrics"))
    // Qobuz discovery rows: AlbumSummary/PlaylistSummary + AlbumSource for the
    // Home discovery feed (HomeDiscoveryRepository results + album/playlist taps).
    implementation(project(":data:ytmusic"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // For LosslessRetryWorker enqueue from the deferred-banner "retry"
    // action (HomeViewModel.onRetryDeferredRequested).
    implementation(libs.work.runtime.ktx)
    // Podcast RSS directory, isolated Media3 session and bounded audio cache.
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.database)
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation("junit:junit:4.13.2")
    // Stage-3 integration captures exercise the actual Home and compact transport together.
    testImplementation(project(":feature:nowplaying"))
    testImplementation(project(":feature:library"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("com.google.truth:truth:1.4.4")
    // Matches the test harness in :feature:library — see MixOfflineTapGuardTest.
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(libs.robolectric)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
