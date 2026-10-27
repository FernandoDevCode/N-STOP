package com.stash.desktop

import com.stash.core.model.*
import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.LrcParser
import com.stash.data.lyrics.source.LrclibLyricsSource
import com.stash.data.lyrics.source.LyricsQuery
import com.stash.core.common.AppVersionProvider
import com.stash.core.auth.model.AuthService
import okhttp3.OkHttpClient
import com.stash.data.ytmusic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.time.Duration
import java.util.concurrent.TimeUnit

data class LyricsText(val plain: String, val lines: List<LrcLine>, val source: String)

class AppController(val store: LibraryStore = LibraryStore(), val audio: DesktopAudio = DesktopAudio(),
    val accounts: DesktopAccounts = DesktopAccounts(File(store.directory, "accounts")),
    private val spotifySyncSource: SpotifySyncSource? = null,
    private val youtubeSyncSource: YouTubeSyncSource? = null) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val message = MutableStateFlow<String?>(null)
    private val loaded = runCatching { store.load() }
    val library = MutableStateFlow(loaded.getOrElse {
        message.value = "Não foi possível ler os dados Desktop: ${it.message}. O arquivo original foi preservado."
        LibrarySnapshot()
    })
    val busy = MutableStateFlow<String?>(null)
    val search = MutableStateFlow<SearchAllResults?>(null)
    val preferences = MutableStateFlow(runCatching { store.loadPreferences() }.getOrElse {
        message.value = "Não foi possível ler as preferências Desktop: ${it.message}."
        DesktopPreferences()
    })
    val searchBusy = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val lyrics = MutableStateFlow<LyricsText?>(null)
    val lyricsBusy = MutableStateFlow(false)
    private val catalog = DesktopCatalog(accounts.youtube, accounts.innerTube)
    val syncState = MutableStateFlow(DesktopSyncState())
    private var syncJob: Job? = null
    private val mutex = Mutex()
    private val preferenceMutex = Mutex()
    private val operationMutex = Mutex()
    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private val lyricCache = mutableMapOf<String, LyricsText>()
    private val lrclib = LrclibLyricsSource(OkHttpClient(), object : AppVersionProvider {
        override val versionName = "0.9.114-desktop-dev"
        override val versionCode = 150
    })
    init {
        audio.volume = preferences.value.volume
        audio.resolveRemoteTrack = { track ->
            check(track.source != MusicSource.LOCAL) { "O arquivo local não está disponível neste PC. Adicione o arquivo novamente para reproduzir." }
            val match = accounts.youtube.searchCanonicalMatch(track.artist, track.title)
                ?: error("Não foi encontrada uma versão reproduzível desta faixa.")
            val resolved = track.copy(youtubeId = match.videoId, albumArtUrl = track.albumArtUrl ?: match.thumbnailUrl, streamOrigin = "youtube")
            if (track.spotifyUri != null) modify { current ->
                if (current.tracks.any { it.spotifyUri == track.spotifyUri }) mergeLibrary(current, LibrarySnapshot(listOf(resolved.saved()))) else current
            }
            resolved
        }
    }
    fun synchronize(options: DesktopSyncOptions, spotify: Boolean = true, youtube: Boolean = true) {
        if (syncJob?.isCompleted == false) return
        if (!options.liked && !options.playlists && !options.mixes && !options.albums) {
            syncState.value = DesktopSyncState(label = "Selecione o conteúdo que deseja sincronizar.")
            return
        }
        syncState.value = DesktopSyncState(running = true, label = "Preparando sincronização…")
        syncJob = scope.launch {
            try {
                accounts.ready.first { it }
                accounts.operationLock.withLock {
                    accounts.busy.value = "Sincronizando biblioteca…"
                    try {
                        val reader = DesktopLibrarySync()
                        var staged = LibrarySnapshot()
                        val warnings = mutableListOf<String>()
                        var services = 0
                        val progress: (String) -> Unit = { syncState.value = syncState.value.copy(label = it) }
                        if (spotify && (options.liked || options.playlists || options.mixes) && accounts.tokens.isAuthenticated(AuthService.SPOTIFY)) {
                            services++
                            try {
                                val result = reader.spotify(spotifySyncSource ?: OriginalSpotifySyncSource(accounts.spotify), options, progress)
                                staged = mergeLibrary(staged, result.snapshot); warnings.addAll(result.warnings)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { warnings.add("Spotify: o serviço não concluiu a leitura. Dados anteriores preservados.") }
                        }
                        if (youtube && accounts.tokens.isAuthenticated(AuthService.YOUTUBE_MUSIC)) {
                            services++
                            try {
                                accounts.innerTube.beginSyncSession()
                                val result = reader.youtube(youtubeSyncSource ?: OriginalYouTubeSyncSource(accounts.youtube), options, progress)
                                staged = mergeLibrary(staged, result.snapshot); warnings.addAll(result.warnings)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { warnings.add("YouTube Music: o serviço não concluiu a leitura. Dados anteriores preservados.") }
                            finally { accounts.innerTube.endSyncSession() }
                        }
                        if (services == 0) throw AccountInputException("Conecte e selecione Spotify ou YouTube Music antes de sincronizar.")
                        ensureActive()
                        syncState.value = syncState.value.copy(committing = true, label = "Salvando biblioteca…")
                        withContext(NonCancellable) { modify { mergeLibrary(it, staged) } }
                        syncState.value = DesktopSyncState(label = if (warnings.isEmpty()) "Sincronização concluída" else "Sincronização incompleta",
                            tracks = staged.tracks.size, playlists = staged.playlists.size, warnings = warnings.distinct())
                    } finally { accounts.busy.value = null }
                }
            } catch (_: CancellationException) {
                syncState.value = DesktopSyncState(label = "Sincronização cancelada; biblioteca preservada.")
            } catch (failure: AccountInputException) {
                syncState.value = DesktopSyncState(label = failure.message.orEmpty())
            } catch (_: Exception) {
                syncState.value = DesktopSyncState(label = "Não foi possível salvar a sincronização.", warnings = listOf("A operação não foi concluída."))
            }
        }
    }
    fun cancelSync() {
        if (!syncState.value.committing && syncJob?.isActive == true) {
            syncState.value = syncState.value.copy(label = "Cancelando sincronização…")
            syncJob?.cancel()
        }
    }
    fun updatePreferences(value: DesktopPreferences, persist: Boolean = true) {
        preferences.value = value.sanitized(); audio.volume = preferences.value.volume
        if (persist) scope.launch {
            try { preferenceMutex.withLock { store.savePreferences(preferences.value) } }
            catch (failure: Exception) { message.value = "Não foi possível salvar preferências: ${failure.message}" }
        }
    }

    private fun work(label: String, action: suspend () -> Unit) {
        scope.launch {
            operationMutex.withLock {
                busy.value = label
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message.value = failure.message ?: "A operação falhou." }
                finally { busy.value = null }
            }
        }
    }
    private suspend fun modify(action: (LibrarySnapshot) -> LibrarySnapshot) = mutex.withLock {
        check(loaded.isSuccess) { "Os dados Desktop não puderam ser lidos. Preserve library.json antes de reparar o arquivo." }
        val updated = action(library.value)
        store.save(updated)
        library.value = updated
    }
    fun importBackup(file: File) = work("Importando biblioteca do celular…") {
        val backup = store.readPhoneBackup(file)
        modify { mergeLibrary(it, backup) }
        message.value = "Biblioteca importada: ${backup.tracks.size} faixas e ${backup.playlists.size} playlists. Os arquivos de áudio do celular precisam estar neste PC ou ter uma origem online disponível."
    }
    fun importAudio(files: List<File>) = work("Importando arquivos de áudio…") {
        val imported = files.mapIndexed { index, file -> probeAudio(file, index.toLong() + 1) }
        modify { mergeLibrary(it, LibrarySnapshot(imported)) }
        message.value = "${imported.size} arquivos adicionados à biblioteca."
    }
    fun like(track: Track) = work("Salvando curtida…") {
        modify { current ->
            val saved = current.tracks.firstOrNull { it.id == track.id || (track.youtubeId != null && it.youtubeId == track.youtubeId) }
            if (saved == null) mergeLibrary(current, LibrarySnapshot(listOf(track.saved().copy(liked = true))))
            else current.copy(tracks = current.tracks.map { if (it.id == saved.id) it.copy(liked = !it.liked) else it })
        }
    }
    fun createPlaylist(name: String) = work("Criando playlist…") {
        require(name.isNotBlank()) { "Informe um nome para a playlist." }
        modify { it.copy(playlists = it.playlists + SavedPlaylist((it.playlists.maxOfOrNull { p -> p.id } ?: 0) + 1,
            name.trim(), "LOCAL", "CUSTOM")) }
    }
    fun addToPlaylist(track: Track, playlistId: Long) = work("Adicionando à playlist…") {
        modify { current ->
            val merged = mergeLibrary(current, LibrarySnapshot(listOf(track.saved())))
            val saved = merged.tracks.first { it.youtubeId == track.youtubeId && track.youtubeId != null || it.path == track.filePath && track.filePath != null || it.id == track.id }
            merged.copy(playlists = merged.playlists.map {
                if (it.id == playlistId) it.copy(trackIds = (it.trackIds + saved.id).distinct()) else it
            })
        }
    }
    fun download(track: Track) = work("Baixando ${track.title}…") {
        val id = track.youtubeId ?: error("Esta faixa não tem uma origem YouTube disponível para download no Desktop.")
        require(Regex("[A-Za-z0-9_-]{11}").matches(id))
        val target = File(store.directory, "audio").apply { mkdirs() }
        val process = ProcessBuilder("yt-dlp", "--no-playlist", "--no-warnings", "--socket-timeout", "20",
            "-x", "--audio-format", "opus", "-o", File(target, "$id.%(ext)s").absolutePath,
            "https://www.youtube.com/watch?v=$id").redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            check(process.waitFor(180, TimeUnit.SECONDS)) { "O download excedeu três minutos." }
            check(process.exitValue() == 0) { "O download falhou. Verifique yt-dlp/FFmpeg e tente novamente." }
            val output = File(target, "$id.opus")
            check(output.isFile) { "O download não produziu um arquivo de áudio." }
            modify { mergeLibrary(it, LibrarySnapshot(listOf(track.saved().copy(path = output.absolutePath, format = "opus")))) }
        } finally { process.destroyForcibly() }
    }
    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { search.value = null; searchBusy.value = false; return }
        searchBusy.value = true; searchError.value = null
        searchJob = scope.launch {
            try { val result = catalog.search(query); ensureActive(); search.value = result }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { search.value = null; searchError.value = failure.message }
            finally { if (isActive) searchBusy.value = false }
        }
    }
    suspend fun album(id: String) = catalog.album(id)
    suspend fun artist(id: String) = catalog.artist(id)
    suspend fun explore() = catalog.explore()
    fun fetchLyrics(track: Track) {
        lyricsJob?.cancel(); lyrics.value = null; lyricsBusy.value = true
        lyricsJob = scope.launch {
            try {
                val key = "${track.title}|${track.artist}|${track.album}|${track.durationMs}"
                val value = lyricCache[key] ?: run {
                    val sidecar = track.filePath?.let { File(it).resolveSibling(File(it).nameWithoutExtension + ".lrc") }
                    if (sidecar?.isFile == true) {
                        val text = sidecar.readText()
                        LyricsText(text, LrcParser.parse(text), "LRC local")
                    } else {
                        val result = lrclib.resolve(LyricsQuery(track.id, track.title, track.artist, track.album,
                            track.albumArtist, track.durationMs.takeIf { it > 0 }, track.youtubeId, interactive = true))
                        LyricsText(result?.plainText ?: if (result?.instrumental == true) "Instrumental" else "Letra não encontrada.",
                            result?.syncedLrc?.let(LrcParser::parse).orEmpty(), "LRCLIB")
                    }
                }
                ensureActive(); lyricCache[key] = value; lyrics.value = value
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { lyrics.value = LyricsText(failure.message ?: "Erro ao consultar a letra.", emptyList(), "") }
            finally { if (isActive) lyricsBusy.value = false }
        }
    }
    override fun close() { scope.cancel(); audio.close(); accounts.close() }
}

data class DesktopSyncState(val running: Boolean = false, val committing: Boolean = false,
    val label: String = "", val tracks: Int = 0, val playlists: Int = 0, val warnings: List<String> = emptyList())

fun Track.saved() = SavedTrack(id, title, artist, album, durationMs, filePath, albumArtUrl,
    fileFormat, source.name, youtubeId, spotifyUri, stashLikedAt != null)
fun TrackSummary.model() = Track(id = -(videoId.hashCode().toLong().and(0xffffffff)), title = title,
    artist = artist, album = album.orEmpty(), durationMs = (durationSeconds * 1000).toLong(),
    youtubeId = videoId, albumArtUrl = thumbnailUrl, source = MusicSource.YOUTUBE)

fun probeAudio(file: File, id: Long): SavedTrack {
    require(file.isFile) { "Arquivo não encontrado: ${file.name}" }
    val process = ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
        "format=duration:format_tags=title,artist,album", "-of", "json", file.absolutePath)
        .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    try {
        check(process.waitFor(15, TimeUnit.SECONDS)) { "A análise de ${file.name} excedeu 15 segundos." }
        check(process.exitValue() == 0) { "Não foi possível ler ${file.name}." }
        val result = Json.parseToJsonElement(process.inputStream.bufferedReader().readText()).jsonObject["format"]?.jsonObject
        val tags = result?.get("tags")?.jsonObject
        return SavedTrack(id, tags?.get("title")?.jsonPrimitive?.content ?: file.nameWithoutExtension,
            tags?.get("artist")?.jsonPrimitive?.content ?: "Artista desconhecido",
            tags?.get("album")?.jsonPrimitive?.content.orEmpty(),
            ((result?.get("duration")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) * 1000).toLong(),
            file.canonicalPath, format = file.extension.lowercase())
    } finally { process.destroyForcibly() }
}
