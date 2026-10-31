plugins {
    id("stash.android.library")
    id("stash.compose.library")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.stash.core.art"
}
kotlin {
    // Compile the SAME procedural implementation as Desktop, without gallery, fixtures or desktop code.
    // Kotlin sources need Kotlin filters; Android's Java excludes do not filter Kotlin compilation.
    sourceSets.getByName("main").kotlin.apply {
        srcDir("../../labs/art-lab/src/commonMain/kotlin")
        include("nonstop/artlab/features/**", "nonstop/artlab/direction/**",
            "nonstop/artlab/renderer/**", "nonstop/artlab/catalog/Album.kt")
    }
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation("junit:junit:4.13.2")
}
