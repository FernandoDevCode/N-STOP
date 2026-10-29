package com.stash.core.data.artistphotos

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalDate

class CommonsCaptureDateTest {
    @Test fun acceptsOnlyExplicitCommonsFilePages() {
        assertThat(CommonsCaptureDate.fileTitle("https://commons.wikimedia.org/wiki/File:Band_2024.jpg"))
            .isEqualTo("File:Band 2024.jpg")
        assertThat(CommonsCaptureDate.fileTitle("https://example.com/wiki/File:Band_2024.jpg")).isNull()
        assertThat(CommonsCaptureDate.fileTitle("https://commons.wikimedia.org/wiki/Category:Band")).isNull()
    }

    @Test fun capturesExifDateWithoutMistakingUploadTimestamp() {
        val json = Json.parseToJsonElement("""
            {"query":{"pages":{"123":{"imageinfo":[{
              "timestamp":"2026-10-27T12:00:00Z",
              "metadata":[{"name":"DateTimeOriginal","value":"2024:09:17 18:02:30"}]
            }]}}}}
        """).jsonObject
        assertThat(CommonsCaptureDate.parseCaptureDate(json))
            .isEqualTo(LocalDate.parse("2024-09-17"))
    }

    @Test fun acceptsStrictCommonsCreationDateWhenExifMissing() {
        val json = Json.parseToJsonElement("""
            {"query":{"pages":{"123":{"imageinfo":[{
              "extmetadata":{"DateTimeOriginal":{"value":"2023-06-12 11:00:00"}}
            }]}}}}
        """).jsonObject
        assertThat(CommonsCaptureDate.parseCaptureDate(json))
            .isEqualTo(LocalDate.parse("2023-06-12"))
    }

    @Test fun refusesVagueOrHtmlFormattedClaims() {
        assertThat(CommonsCaptureDate.parseStrictDate("circa 2025")).isNull()
        assertThat(CommonsCaptureDate.parseStrictDate("<time>2025-08-12</time>")).isNull()
        assertThat(CommonsCaptureDate.parseStrictDate("2025-13-12")).isNull()
    }
}
