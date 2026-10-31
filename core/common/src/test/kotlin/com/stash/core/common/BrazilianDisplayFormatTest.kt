package com.stash.core.common

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BrazilianDisplayFormatTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val utc = ZoneId.of("UTC")

    @Test fun `formats Brazilian day month year and timezone correctly`() {
        assertEquals("31/10/2026", BrazilianDisplayFormat.date(
            Instant.parse("2026-10-31T13:30:00Z").toEpochMilli(), sp))
        assertEquals("30/10/2026", BrazilianDisplayFormat.date(
            Instant.parse("2026-10-31T00:15:00Z").toEpochMilli(), sp))
    }

    @Test fun `parses standard English RSS date as Brazilian display date`() {
        assertEquals("31/10/2026",
            BrazilianDisplayFormat.rssDate("Sat, 31 Oct 2026 12:00:00 GMT", utc))
        assertEquals("31/10/2026", BrazilianDisplayFormat.rssDate("2026-10-31", utc))
        assertEquals("Data indisponível", BrazilianDisplayFormat.rssDate("not a date", utc))
    }

    @Test fun `relative labels are Portuguese and older entries use absolute Brazilian date`() {
        val now = Instant.parse("2026-10-31T16:00:00Z").toEpochMilli()
        assertEquals("agora", BrazilianDisplayFormat.relative(now - 5_000, now, utc))
        assertEquals("há 5 min", BrazilianDisplayFormat.relative(now - 300_000, now, utc))
        assertEquals("há 2 h", BrazilianDisplayFormat.relative(now - 7_200_000, now, utc))
        assertEquals("ontem", BrazilianDisplayFormat.relative(now - 86_400_000, now, utc))
        assertEquals("há 3 dias", BrazilianDisplayFormat.relative(now - 259_200_000, now, utc))
        assertEquals("20/10/2026", BrazilianDisplayFormat.relative(now - 11L * 86_400_000, now, utc))
    }
}
