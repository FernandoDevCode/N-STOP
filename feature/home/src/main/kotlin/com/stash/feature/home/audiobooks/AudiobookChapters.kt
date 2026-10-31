package com.stash.feature.home.audiobooks

import java.net.URI

/** Chapter boundaries belong to a particular recording, never just to a book title. */
internal data class AudiobookChapter(
    val number: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    val endEstimated: Boolean,
)

internal data class ChapterImportResult(
    val chapters: List<AudiobookChapter> = emptyList(),
    val error: String? = null,
) {
    val valid: Boolean get() = error == null && chapters.isNotEmpty()
}

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val CHAPTER_LINE = Regex(
    """^(\d{1,3})\s*\|\s*(\d{1,3}:\d{2}(?::\d{2})?)\s*\|\s*(\d{1,3}:\d{2}(?::\d{2})?|\?)\s*\|\s*(.{1,160})$""",
)

/** Validate exact YouTube hosts and extract a stable ID: do not store arbitrary links. */
internal fun youtubeVideoId(raw: String): String? = runCatching {
    val uri = URI(raw.trim())
    if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port != -1) return null
    val host = uri.host?.lowercase() ?: return null
    val segments = uri.path.orEmpty().trim('/').split('/')
    val id = when (host) {
        "youtu.be", "www.youtu.be" -> segments.singleOrNull()
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when (segments.firstOrNull()) {
            "watch" -> uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                part.substringAfter("v=", "").takeIf { part.startsWith("v=") }
            }.singleOrNull()
            "shorts", "live" -> segments.getOrNull(1)?.takeIf { segments.size == 2 }
            else -> null
        }
        else -> null
    }
    id?.takeIf { VIDEO_ID.matches(it) }
}.getOrNull()

internal fun youtubeWatchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

/** Convert mm:ss or hh:mm:ss to milliseconds. Reject malformed minutes/seconds. */
internal fun chapterTimeMs(value: String): Long? {
    val numbers = value.split(':').map { it.toLongOrNull() ?: return null }
    if (numbers.size !in 2..3 || numbers.any { it < 0 }) return null
    val sec = numbers.last()
    if (sec >= 60) return null
    val min = numbers[numbers.lastIndex - 1]
    if (numbers.size == 3 && min >= 60) return null
    return if (numbers.size == 2) (min * 60 + sec) * 1000
    else (numbers[0] * 3600 + min * 60 + sec) * 1000
}

/**
 * Intentionally strict. The AI response is an untrusted proposed index:
 * never invent missing chapters, auto-correct overlaps or mark it as verified.
 * Format: 1 | 00:00:00 | 00:12:10 | Nome do capítulo (or ? for unknown end).
 */
internal fun parseChapterProposal(response: String): ChapterImportResult {
    if (response.length > 60_000) return ChapterImportResult(error = "Resposta extensa demais.")
    val lines = response.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val rows = mutableListOf<AudiobookChapter>()
    for (line in lines) {
        val value = line.removePrefix("- ").removePrefix("* ")
        val match = CHAPTER_LINE.matchEntire(value)
        if (match == null) {
            if (Regex("^\\d{1,3}\\s*\\|").containsMatchIn(value)) {
                return ChapterImportResult(error = "Linha de capítulo inválida: ${value.take(60)}")
            }
            continue
        }
        val number = match.groupValues[1].toInt()
        val start = chapterTimeMs(match.groupValues[2])
            ?: return ChapterImportResult(error = "Horário inicial inválido no item $number.")
        val rawEnd = match.groupValues[3]
        val end = if (rawEnd == "?") null else chapterTimeMs(rawEnd)
            ?: return ChapterImportResult(error = "Horário final inválido no item $number.")
        if (end != null && end <= start) {
            return ChapterImportResult(error = "Final anterior ao início no item $number.")
        }
        val title = match.groupValues[4].trim()
        if (title.isBlank()) return ChapterImportResult(error = "Título vazio no item $number.")
        rows += AudiobookChapter(number, title, start, end, rawEnd == "?")
        if (rows.size > 160) return ChapterImportResult(error = "Mais de 160 capítulos. Revise a resposta.")
    }
    if (rows.isEmpty()) {
        return ChapterImportResult(error = "Nenhum capítulo reconhecido. Use o formato solicitado no prompt.")
    }
    if (rows.first().number != 1 || rows.indices.any { rows[it].number != it + 1 }) {
        return ChapterImportResult(error = "Numeração descontínua. Verifique os capítulos ausentes.")
    }
    for (i in 1 until rows.size) {
        val previous = rows[i - 1]
        val current = rows[i]
        if (current.startMs <= previous.startMs) {
            return ChapterImportResult(error = "Inícios repetidos ou fora de ordem.")
        }
        if (previous.endMs != null && previous.endMs > current.startMs) {
            return ChapterImportResult(error = "Capítulos com horários sobrepostos.")
        }
    }
    // An unknown end can be estimated from the following chapter's start.
    val completed = rows.mapIndexed { index, chapter ->
        if (chapter.endMs == null && index < rows.lastIndex) {
            chapter.copy(endMs = rows[index + 1].startMs, endEstimated = true)
        } else chapter
    }
    return ChapterImportResult(chapters = completed)
}

/** Request a bounded, reproducible response; confirmation still requires user review. */
internal val youtubeChapterPrompt = """
Analise ESTE vídeo completo como um audiolivro.
1. Identifique cada capítulo efetivamente narrado, inclusive introdução/prólogo/epílogo.
2. Não deduza capítulos apenas pelo conhecimento do livro. Não invente marcações.
3. Compare com o sumário, se ele estiver disponível, e sinalize possíveis omissões.
4. Para cada capítulo, procure o início e o fim no vídeo; se não souber o fim, escreva ?.
5. Se não tiver acesso ao vídeo inteiro, explique a limitação.
6. Responda com uma linha POR capítulo, em ordem, exatamente neste formato:
1 | 00:00:00 | 00:12:34 | Título do capítulo
2 | 00:12:34 | ? | Título do capítulo
Use HH:MM:SS. Sem tabelas Markdown. Não chute horários.
""".trimIndent()
