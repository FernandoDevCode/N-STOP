package com.stash.data.download.lossless.qbdlx

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QobuzFollowedArtistReleasesDateTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test fun `only complete dates in the last 90 days are eligible`() {
        assertEquals(today, QobuzFollowedArtistReleases.recentDate("2026-11-03", today))
        assertEquals(today.minusDays(90),
            QobuzFollowedArtistReleases.recentDate("2026-08-05", today))
        assertNull(QobuzFollowedArtistReleases.recentDate("2026-08-04", today))
        assertNull(QobuzFollowedArtistReleases.recentDate("2026-11-04", today))
    }

    @Test fun `unknown, year-only, and invalid dates are excluded`() {
        assertNull(QobuzFollowedArtistReleases.recentDate(null, today))
        assertNull(QobuzFollowedArtistReleases.recentDate("2026", today))
        assertNull(QobuzFollowedArtistReleases.recentDate("2026-02-30", today))
        assertNull(QobuzFollowedArtistReleases.recentDate("unknown", today))
    }
}
