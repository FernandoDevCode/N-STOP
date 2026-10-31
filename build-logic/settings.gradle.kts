pluginManagement {
    // kotlin-dsl plugin classpath needs Kotlin artifacts from Maven Central.
    // Gradle Plugin Portal alone cannot reliably resolve the complete BOM.
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
