plugins {
    id("com.android.application") version "8.9.1"
    kotlin("android") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
}

android {
    namespace = "com.stash.core.ui"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.stash.visualpreview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main") {
        res.srcDirs("../res", "src/main/res")
    }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.getByName("main").kotlin.apply {
        srcDirs("src/main/kotlin", "../kotlin", "../../../core/ui/src/main/kotlin")
        include("com/stash/core/ui/visual/**", "com/stash/preview/**",
            "com/stash/core/ui/theme/Type.kt", "com/stash/core/ui/theme/Fonts.android.kt")
    }
}

// The same checked-in font bytes, without copying the entire core/ui resource tree.
val previewFonts = tasks.register<Copy>("previewFonts") {
    from("../../../core/ui/src/main/res/font")
    into(layout.buildDirectory.dir("generated/preview-res/font"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/preview-res"))
tasks.named("preBuild").configure { dependsOn(previewFonts) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
}
