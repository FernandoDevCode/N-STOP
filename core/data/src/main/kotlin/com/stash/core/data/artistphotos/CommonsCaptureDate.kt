package com.stash.core.data.artistphotos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Reads an explicit creation timestamp from a linked Wikimedia Commons file.
 *
 * Never infers photo date from image upload time, web publication time, or file
 * modification time. Only Commons links directly identifying a File page are
 * accepted; API requests go exclusively to commons.wikimedia.org.
 */
internal object CommonsCaptureDate {
    private val exactExif = Regex("""^(\d{4}):(\d{2}):(\d{2})(?:\s.*)?$""")
    private val exactIso = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[ T].*)?$""")

    fun fileTitle(source: String?): String? {
        if (source.isNullOrBlank()) return null
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "commons.wikimedia.org") return null
        val file = uri.rawPath?.removePrefix("/wiki/")?.takeIf { it.startsWith("File:") }
            ?: return null
        val decoded = runCatching { java.net.URLDecoder.decode(file, "UTF-8") }.getOrNull()
            ?: return null
        return decoded.replace('_', ' ').takeIf { it.length in 6..250 }
    }

    fun parseCaptureDate(payload: JsonObject): LocalDate? {
        val pages = payload["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        for (page in pages.values) {
            val info = (page as? JsonObject)?.get("imageinfo")?.jsonArray
                ?.firstOrNull()?.jsonObject ?: continue
            val exif = info["metadata"]?.jsonArray?.firstNotNullOfOrNull { item ->
                val property = item as? JsonObject ?: return@firstNotNullOfOrNull null
                if (property["name"]?.jsonPrimitive?.content != "DateTimeOriginal") null
                else property["value"]?.jsonPrimitive?.content
            }
            if (exif != null) return parseStrictDate(exif)
            val commonsDate = info["extmetadata"]?.jsonObject
                ?.get("DateTimeOriginal")?.jsonObject
                ?.get("value")?.jsonPrimitive?.content
            if (commonsDate != null) return parseStrictDate(commonsDate)
        }
        return null
    }

    fun parseStrictDate(value: String): LocalDate? {
        val match = exactExif.matchEntire(value) ?: exactIso.matchEntire(value) ?: return null
        return runCatching {
            LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        }.getOrNull()
    }

    fun fetchDate(client: OkHttpClient, source: String?): LocalDate? {
        val title = fileTitle(source) ?: return null
        val encoded = URLEncoder.encode(title, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&prop=imageinfo&format=json&iiprop=metadata%7Cextmetadata" +
            "&iilimit=1&titles=$encoded"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Sonora/0.9 (artist-photo-date; GitHub FernandoDevCode/N-STOP)")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            parseCaptureDate(Json.parseToJsonElement(body).jsonObject)
        }
    }
}
