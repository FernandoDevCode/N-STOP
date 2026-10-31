package com.stash.feature.home.audiobooks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * No undocumented YouTube AI endpoint, login scraping or audio download:
 * the user opens YouTube, copies the prompt, and explicitly imports the answer.
 * Every chapter remains *proposed* until checked against the recording.
 */
@Composable
fun AudiobookChapterSection() {
    val context = LocalContext.current
    val store = remember(context) { AudiobookChapterStore(context) }
    val scope = rememberCoroutineScope()

    var videoUrl by rememberSaveable { mutableStateOf("") }
    var bookTitle by rememberSaveable { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var proposal by remember { mutableStateOf<ChapterImportResult?>(null) }
    var displayed by remember { mutableStateOf<List<AudiobookChapter>>(emptyList()) }
    var saved by remember { mutableStateOf<List<SavedAudiobookRecord>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val videoId = youtubeVideoId(videoUrl)

    LaunchedEffect(store) {
        saved = withContext(Dispatchers.IO) { store.listSaved() }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Audiolivros", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(
            "Organize capítulos de audiolivros encontrados no YouTube. Esta etapa cria o índice; a reprodução e o timer serão integrados separadamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it; proposal = null; displayed = emptyList(); status = null },
            label = { Text("Link do vídeo do YouTube") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = videoUrl.isNotBlank() && videoId == null,
        )
        if (videoUrl.isNotBlank() && videoId == null) {
            Text("Cole um link HTTPS válido de vídeo do YouTube.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = bookTitle,
            onValueChange = { bookTitle = it },
            label = { Text("Nome do audiolivro (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (videoId != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeWatchUrl(videoId)))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                    runCatching { context.startActivity(intent) }
                        .onFailure { status = "Não foi possível abrir o YouTube neste aparelho." }
                }
            },
            enabled = videoId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(Icons.Default.OpenInNew, null)
            Spacer(Modifier.width(8.dp))
            Text("1 · Abrir vídeo no YouTube")
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Instruções de capítulos", youtubeChapterPrompt))
                status = "Prompt copiado. No YouTube, use «Perguntar» e copie a resposta."
            },
            enabled = videoId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("2 · Copiar pergunta para a IA do YouTube")
        }

        HorizontalDivider()
        Text("3 · Importar resposta", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(
            "Peça a resposta em linhas «1 | HH:MM:SS | HH:MM:SS ou ? | Título». Não compartilhe sua conta Google nem conceda acesso ao Sonora.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = aiResponse,
            onValueChange = { aiResponse = it; proposal = null; status = null },
            label = { Text("Resposta do Perguntar ao YouTube") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 12,
        )
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrBlank()) { aiResponse = text; proposal = null; status = "Texto colado." }
                else status = "Área de transferência vazia."
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("Colar resposta copiada")
        }
        Button(
            onClick = {
                proposal = parseChapterProposal(aiResponse)
                displayed = proposal?.chapters.orEmpty()
                status = null
            },
            enabled = videoId != null && aiResponse.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Analisar capítulos") }

        proposal?.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        if (displayed.isNotEmpty()) {
            Text("${displayed.size} marcações propostas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Não verificadas contra o áudio. Revise a sequência antes de salvar; tempos estimados não devem controlar o timer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            displayed.forEach { chapter ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${chapter.number}.", style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val end = chapter.endMs?.let(::formatChapterTime) ?: "?"
                        Text("${formatChapterTime(chapter.startMs)} — $end" +
                            if (chapter.endEstimated) " · fim estimado" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                enabled = videoId != null && !saving && proposal?.valid == true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val id = videoId ?: return@Button
                    val chapters = displayed.toList()
                    saving = true
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                store.save(id, bookTitle, chapters)
                                store.listSaved()
                            }
                        }
                        saving = false
                        result.onSuccess { list ->
                            saved = list
                            status = "Índice salvo no aparelho, vinculado a este vídeo."
                        }.onFailure {
                            status = "Não foi possível salvar os capítulos."
                        }
                    }
                },
            ) {
                androidx.compose.material3.Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Salvar índice neste aparelho")
            }
        }
        if (saved.isNotEmpty()) {
            HorizontalDivider()
            Text("Índices salvos", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            saved.forEach { item ->
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        videoUrl = youtubeWatchUrl(item.videoId)
                        bookTitle = item.title
                        aiResponse = ""
                        proposal = null
                        scope.launch {
                            val restored = withContext(Dispatchers.IO) {
                                store.loadChapters(item.videoId)
                            }
                            displayed = restored
                            status = "Índice salvo: ${restored.size} capítulos propostos."
                        }
                    },
                ) {
                    androidx.compose.material3.Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("${item.title} · ${item.chapterCount} capítulos",
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(20.dp))
    }
}

private fun formatChapterTime(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}
