import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.security.MessageDigest

plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs) }
            resources.srcDir(layout.buildDirectory.dir("reference-assets"))
        }
    }
}

// Only the artwork rectangle is extracted. No reference UI/background is used by the renderer.
val prepareReferenceAssets by tasks.registering {
    val refs = rootDir.resolve("../../docs/player-v8/references")
    inputs.files(refs.resolve("MASTER_MEGADETH.png"), refs.resolve("MASTER_PINK_FLOYD.png"))
    outputs.dir(layout.buildDirectory.dir("reference-assets"))
    doLast {
        val dest = layout.buildDirectory.dir("reference-assets").get().asFile.apply { mkdirs() }
        data class Crop(val name: String, val hash: String, val x: Int, val y: Int, val w: Int, val h: Int)
        listOf(
            Crop("MEGADETH", "3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d", 70,154,724,718),
            Crop("PINK_FLOYD", "6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0",81,175,779,757)
        ).forEach { c ->
            val source = refs.resolve("MASTER_${c.name}.png")
            val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
            check(hash == c.hash) { "Approved reference hash mismatch: $source" }
            val original = ImageIO.read(source)
            val cropped = BufferedImage(c.w,c.h,BufferedImage.TYPE_INT_RGB)
            cropped.createGraphics().apply {
                drawImage(original,0,0,c.w,c.h,c.x,c.y,c.x+c.w,c.y+c.h,null); dispose()
            }
            ImageIO.write(cropped,"png",dest.resolve("${c.name}.png"))
        }
    }
}
tasks.named("desktopProcessResources") { dependsOn(prepareReferenceAssets) }

compose.desktop {
    application {
        mainClass = "nonstop.artlab.MainKt"
        jvmArgs += "-Dfile.encoding=UTF-8"
        nativeDistributions {
            packageName = "NonstopArtLab"
            packageVersion = "0.1.0"
            modules("java.desktop", "java.logging", "java.management", "jdk.unsupported")
        }
    }
}
compose.resources { packageOfResClass = "nonstop.artlab.resources" }
