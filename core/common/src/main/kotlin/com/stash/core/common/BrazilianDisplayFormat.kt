package com.stash.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Presentation only: pt-BR dates and relative times.
 * Never use this for API serialization, database values or parsing track IDs.
 */
object BrazilianDisplayFormat {
    private val date = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.forLanguageTag("pt-BR"))

    fun date(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestampMs).atZone(zone).format(date)

    /** RSS dates are RFC 1123 (English month names in the wire format). */
    fun rssDate(raw: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val parsed = runCatching { ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .recoverCatching { Instant.parse(raw.trim()) }
            .getOrNull()
        if (parsed != null) return date(parsed.toEpochMilli(), zone)
        val local = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
        return local?.format(date) ?: "Data indisponível"
    }

    /** Keep short labels for feeds, use an actual Brazilian date for older entries. */
    fun relative(timestampMs: Long, nowMs: Long = System.currentTimeMillis(),
                 zone: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMs <= 0L) return "Data indisponível"
        val delta = (nowMs - timestampMs).coerceAtLeast(0L)
        return when {
            delta < TimeUnit.MINUTES.toMillis(1) -> "agora"
            delta < TimeUnit.HOURS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toMinutes(delta)} min"
            delta < TimeUnit.DAYS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toHours(delta)} h"
            delta < TimeUnit.DAYS.toMillis(2) -> "ontem"
            delta < TimeUnit.DAYS.toMillis(7) -> "há ${TimeUnit.MILLISECONDS.toDays(delta)} dias"
            else -> date(timestampMs, zone)
        }
    }
}
