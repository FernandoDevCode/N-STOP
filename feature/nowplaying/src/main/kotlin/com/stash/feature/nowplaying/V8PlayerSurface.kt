package com.stash.feature.nowplaying

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.stash.core.model.RepeatMode
import com.stash.core.ui.ambient.rememberReducedMotion
import com.stash.core.ui.theme.Inter
import kotlinx.coroutines.*
import nonstop.artlab.catalog.Album
import nonstop.artlab.catalog.ArtworkStatus
import nonstop.artlab.renderer.*

internal fun NowPlayingUiState.v8State(positionMs: Long) = PlayerState(
    progress = if (durationMs > 0) (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f) else 0f,
    playing = playWhenReady, liked = currentTrack?.stashLikedAt != null,
    shuffle = shuffleEnabled, repeat = repeatMode != RepeatMode.OFF,
)

@Composable
internal fun V8PlayerSurface(ui: NowPlayingUiState, position: State<Long>, viewModel: NowPlayingViewModel,
    onDismiss: () -> Unit, onMenu: () -> Unit, onFailure: () -> Unit,
    cover: @Composable (Modifier, @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current.applicationContext
    val source = ui.currentTrack?.albumArtPath ?: ui.currentTrack?.albumArtUrl
    var art by remember(source) { mutableStateOf(AndroidArtPipeline.fallback()) }
    val failure by rememberUpdatedState(onFailure)
    LaunchedEffect(source) {
        try { art = AndroidArtPipeline.load(context, source) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("PlayerV8", "Artwork initialization failed; using V7", e)
            failure()
        }
    }
    val reduced = rememberReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val fontScale = LocalDensity.current.fontScale
    GuardedPlayerSurface(onFailure, Modifier.fillMaxSize()) {
        val track = ui.currentTrack
        val state = ui.v8State(position.value)
        V8Renderer(
            album = Album(track?.artist.orEmpty(), track?.album.orEmpty(), track?.title,
                durationSeconds = (ui.durationMs / 1000).toInt().takeIf { it > 0 }, status = ArtworkStatus.OFFICIAL_COVER),
            cover = art.cover, atmosphere = art.atmosphere, recipe = art.recipe, state = state,
            onState = { changed ->
                when {
                    changed.playing != state.playing -> viewModel.onPlayPauseClick()
                    changed.liked != state.liked -> viewModel.onLikeTap()
                    changed.shuffle != state.shuffle -> viewModel.onToggleShuffle()
                    changed.repeat != state.repeat -> viewModel.onCycleRepeatMode()
                    changed.lyrics -> viewModel.onShowLyrics()
                    changed.progress != state.progress && ui.isSeekable && ui.durationMs > 0 ->
                        viewModel.onSeekTo((changed.progress * ui.durationMs).toLong())
                }
            },
            previous = viewModel::onSkipPrevious, next = viewModel::onSkipNext,
            onClose = onDismiss, onMenu = onMenu, font = Inter, fontScale = fontScale,
            production = true, seekEnabled = ui.isSeekable && ui.durationMs > 0,
            repeatOne = ui.repeatMode == RepeatMode.ONE, onInfo = viewModel::onTrackInfoTapped,
            coverContent = { modifier -> cover(modifier) {
                val bitmap = art.cover
                if (bitmap != null) Image(bitmap, "Capa de ${track?.album.orEmpty()}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else com.stash.core.ui.visual.VisualCover(null, Modifier.fillMaxSize())
            } }, modifier = Modifier.fillMaxSize(),
            microOptions = MicroOptions(reducedMotion = reduced, active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)),
        )
        if (ui.isBuffering) Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** Separate composition + Android drawing boundary: a renderer failure cannot kill the transport. */
@Composable
internal fun GuardedPlayerSurface(onFailure: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val latestContent by rememberUpdatedState(content)
    val latestFailure by rememberUpdatedState(onFailure)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val host = remember { GuardedPlayerView(context) { latestFailure() } }
    val job = remember { SupervisorJob(scope.coroutineContext[Job]) }
    val recomposer = remember {
        Recomposer(scope.coroutineContext + job + CoroutineExceptionHandler { _, error -> host.fail(error) })
    }
    DisposableEffect(recomposer) {
        val runner = scope.launch {
            try { recomposer.runRecomposeAndApplyChanges() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { host.fail(e) }
        }
        onDispose { host.player.disposeComposition(); recomposer.cancel(); runner.cancel(); job.cancel() }
    }
    AndroidView(modifier = modifier, factory = {
        host.apply {
            try {
                player.setParentCompositionContext(recomposer)
                player.setContent { Box(Modifier.fillMaxSize()) { latestContent() } }
            } catch (e: Exception) { fail(e) }
        }
    })
}

internal class GuardedPlayerView(context: Context, private val onFailure: () -> Unit) : FrameLayout(context) {
    val player = ComposeView(context)
    private var failed = false
    init { addView(player, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    fun fail(error: Throwable) {
        if (failed) return
        failed = true
        android.util.Log.e("PlayerV8", "Renderer failed; restoring V7", error)
        post { onFailure() }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try { super.onMeasure(widthMeasureSpec, heightMeasureSpec) }
        catch (e: Exception) { fail(e); setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec)) }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try { super.onLayout(changed, left, top, right, bottom) } catch (e: Exception) { fail(e) }
    }
    override fun dispatchDraw(canvas: Canvas) {
        try { super.dispatchDraw(canvas) } catch (e: Exception) { fail(e) }
    }
}
