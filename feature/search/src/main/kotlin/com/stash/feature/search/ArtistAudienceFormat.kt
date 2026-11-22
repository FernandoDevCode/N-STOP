package com.stash.feature.search

import java.text.NumberFormat
import java.util.Locale

/**
 * YouTube Music artist-header "monthlyListenerCount" is audience metadata,
 * not Spotify's monthly listeners. Keep the display honest and locale-native.
 * Never infer monthly audience from channel subscribers or Spotify followers.
 */
internal fun artistMonthlyAudienceLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val match = Regex(
        """^\s*([0-9][0-9.,]*)\s*(milhões|milhão|millions|million|billions|billion|thousand|mil|mi|bi|[kmb])?(?:\s|$)""",
        RegexOption.IGNORE_CASE,
    ).find(raw.trim()) ?: return null
    val unit = match.groupValues[2].lowercase(Locale.ROOT)
    val number = match.groupValues[1]
    val scale = when (unit) {
        "k", "mil", "thousand" -> 1_000.0
        "m", "mi", "milhão", "milhões", "million", "millions" -> 1_000_000.0
        "b", "bi", "billion", "billions" -> 1_000_000_000.0
        else -> 1.0
    }
    val value = if (scale == 1.0) {
        number.filter(Char::isDigit).toDoubleOrNull()
    } else number.replace(',', '.').toDoubleOrNull()?.times(scale)
        ?: return null
    if (value == null || !value.isFinite() || value < 1 || value > 1_000_000_000_000) return null
    val formatter = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    val text = when {
        value >= 1_000_000_000 -> "${formatter.format(value / 1_000_000_000)} bi"
        value >= 1_000_000 -> "${formatter.format(value / 1_000_000)} mi"
        value >= 1_000 -> "${formatter.format(value / 1_000)} mil"
        else -> formatter.format(value)
    }
    return "$text de público mensal"
}
