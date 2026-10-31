package com.stash.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class DesktopPreferences(val dark: Boolean = true, val amoled: Boolean = false,
    val fontScale: Float = 1f, val volume: Float = 1f) {
    fun sanitized() = copy(fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(1f, 1.5f) ?: 1f,
        volume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f)
}
internal fun LibraryStore.loadPreferences(): DesktopPreferences {
    val file = File(directory, "settings.json")
    if (!file.exists()) return DesktopPreferences()
    return Json { ignoreUnknownKeys = true }.decodeFromString<DesktopPreferences>(file.readText()).sanitized()
}
internal fun LibraryStore.savePreferences(value: DesktopPreferences) {
    directory.mkdirs()
    val temporary = Files.createTempFile(directory.toPath(), "settings-", ".tmp")
    try {
        Files.writeString(temporary, Json.encodeToString(value.sanitized()))
        Files.move(temporary, File(directory, "settings.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }
}
