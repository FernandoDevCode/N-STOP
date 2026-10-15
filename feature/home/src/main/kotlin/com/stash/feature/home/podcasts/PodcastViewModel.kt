package com.stash.feature.home.podcasts

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.stash.core.media.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import androidx.work.WorkManager

data class PodcastUiState(
    val query: String = "",
    val results: List<PodcastShow> = emptyList(),
    val followed: List<PodcastShow> = emptyList(),
    val selected: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val playingId: String? = null,
    val playingTitle: String = "",
    val isPlaying: Boolean = false,
    val foreground: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val downloaded: Set<String> = emptySet(),
    val downloading: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: PodcastCatalog,
    private val musicPlayer: PlayerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PodcastUiState(followed = catalog.followed.value))
    val state: StateFlow<PodcastUiState> = _state
    private val downloadJobs = mutableMapOf<String, Job>()
    private val _openRequests = MutableStateFlow(0)
    val openRequests: StateFlow<Int> = _openRequests

    fun requestOpenFromChrome() {
        _openRequests.value += 1
    }
    private var searchJob: Job? = null
    private var listJob: Job? = null
    private var controller: MediaController? = null
    private var pendingPlay: PodcastEpisode? = null
    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java)),
    ).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshPlayback()
    }

    init {
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get()
                controller?.addListener(listener)
                refreshPlayback()
                pendingPlay?.let { pending ->
                    pendingPlay = null
                    play(pending)
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Falha ao conectar o player") }
            }
        }, ContextCompat.getMainExecutor(context))
        viewModelScope.launch {
            catalog.followed.collect { shows -> _state.update { it.copy(followed = shows) } }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                refreshPlayback()
            }
        }
        // A music track taking over must hide/pause the podcast transport.
        viewModelScope.launch {
            musicPlayer.playerState.map { it.playWhenReady }.distinctUntilChanged()
                .collect { musicStarted ->
                    if (musicStarted) {
                        controller?.pause()
                        _state.update { it.copy(foreground = false) }
                    }
                }
        }
    }

    fun setQuery(value: String) { _state.update { it.copy(query = value) } }

    fun search() {
        searchJob?.cancel()
        val q = state.value.query.trim()
        if (q.length < 2) return
        searchJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, results = emptyList(), selected = null) }
            try {
                val items = catalog.search(q)
                _state.update { it.copy(results = items, busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Não foi possível buscar", busy = false) }
            }
        }
    }

    fun open(show: PodcastShow) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _state.update { it.copy(selected = show, episodes = emptyList(), busy = true, error = null) }
            try {
                val (resolved, episodes) = catalog.episodes(show)
                val downloaded = withContext(Dispatchers.IO) {
                    episodes.filter { PodcastFiles.downloaded(context, it.id) != null }.map { it.id }.toSet()
                }
                _state.update {
                    it.copy(selected = resolved, episodes = episodes, downloaded = downloaded, busy = false)
                }
                episodes.forEach { observeDownload(it.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Falha ao carregar RSS") }
            }
        }
    }

    fun back() {
        listJob?.cancel()
        _state.update { it.copy(selected = null, episodes = emptyList(), error = null, busy = false) }
    }

    fun toggleFollow() { state.value.selected?.let(catalog::toggleFollow) }

    fun play(episode: PodcastEpisode) {
        val player = controller
        if (player == null) {
            pendingPlay = episode
            return
        }
        viewModelScope.launch {
            musicPlayer.pause() // mutually exclusive audio, with independent histories
            val local = withContext(Dispatchers.IO) { PodcastFiles.downloaded(context, episode.id) }
            val uri = local?.let { Uri.fromFile(it) }
                ?: Uri.parse(episode.audioUrl.takeIf(::validPodcastUrl) ?: return@launch)
            val meta = MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(state.value.selected?.title ?: "Podcast")
                .apply {
                    episode.imageUrl?.takeIf(::validPodcastUrl)?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()
            val item = MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(uri)
                .setMediaMetadata(meta)
                .build()
            val position = context.getSharedPreferences("nonstop_podcasts", Context.MODE_PRIVATE)
                .getLong("position_${episode.id}", 0L)
            player.setMediaItem(item, position)
            player.prepare()
            player.play()
            _state.update { it.copy(foreground = true) }
            refreshPlayback()
        }
    }

    fun togglePause() {
        val player = controller ?: return
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        } else {
            viewModelScope.launch {
                musicPlayer.pause()
                player.play()
                _state.update { it.copy(foreground = true) }
                refreshPlayback()
            }
        }
        refreshPlayback()
    }
    fun jump(ms: Long) {
        controller?.let { it.seekTo((it.currentPosition + ms).coerceAtLeast(0L)) }
        refreshPlayback()
    }

    fun seek(fraction: Float) {
        val player = controller ?: return
        if (player.duration <= 0 || player.duration == C.TIME_UNSET) return
        player.seekTo((fraction.coerceIn(0f, 1f) * player.duration).toLong())
        refreshPlayback()
    }

    private fun refreshPlayback() {
        val player = controller ?: return
        _state.update {
            it.copy(
                playingId = player.currentMediaItem?.mediaId,
                playingTitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: 0,
            )
        }
    }

    fun download(episode: PodcastEpisode) {
        val id = episode.id
        if (id in state.value.downloaded || id in state.value.downloading) return
        runCatching { PodcastFiles.enqueue(context, episode) }
            .onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Falha ao iniciar download") }
                return
            }
        _state.update { it.copy(downloading = it.downloading + (id to 0)) }
        observeDownload(id)
    }

    /**
     * WorkManager owns transfers and survives process death. Reattach to the
     * UNIQUE work by episode ID whenever a show's episodes are opened.
     * A stale ViewModel never determines success; the saved file does.
     */
    private fun observeDownload(id: String) {
        if (downloadJobs[id]?.isActive == true) return
        downloadJobs[id] = viewModelScope.launch(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            var notFound = 0
            while (isActive) {
                val file = PodcastFiles.downloaded(context, id)
                if (file != null) {
                    _state.update { it.copy(downloaded = it.downloaded + id, downloading = it.downloading - id) }
                    break
                }
                val works = runCatching {
                    manager.getWorkInfosForUniqueWork(PodcastFiles.workName(id)).get()
                }.getOrDefault(emptyList())
                val work = works.firstOrNull { !it.state.isFinished } ?: works.lastOrNull()
                if (work == null) {
                    notFound += 1
                    if (notFound > 3) {
                        _state.update { it.copy(downloading = it.downloading - id) }
                        break
                    }
                } else {
                    when (work.state) {
                        WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _state.update {
                                it.copy(downloading = it.downloading - id,
                                    error = if (id in it.downloading) "Download não concluído." else it.error)
                            }
                            break
                        }
                        else -> {
                            val percent = work.progress.getInt("percent", 0)
                            _state.update { it.copy(downloading = it.downloading + (id to percent)) }
                        }
                    }
                }
                delay(1_000)
            }
        }
    }

    fun removeDownload(episode: PodcastEpisode) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = episode.id
            WorkManager.getInstance(context).cancelUniqueWork(PodcastFiles.workName(id)).result.get()
            if (state.value.playingId == id) {
                withContext(Dispatchers.Main) {
                    controller?.stop()
                    controller?.clearMediaItems()
                    _state.update { it.copy(foreground = false) }
                }
            }
            PodcastFiles.remove(context, id)
            downloadJobs[id]?.cancel()
            _state.update { it.copy(downloaded = it.downloaded - id, downloading = it.downloading - id) }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
