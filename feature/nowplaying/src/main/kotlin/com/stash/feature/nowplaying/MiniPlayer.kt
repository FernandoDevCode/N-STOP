package com.stash.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.stash.core.common.primaryArtist
import com.stash.core.media.listen.ListenTogetherState
import com.stash.core.model.RepeatMode
import com.stash.core.ui.components.ListenTogetherRole
import com.stash.core.ui.components.LocalListenTogetherRole
import com.stash.core.ui.components.sonoraMiniGlass
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import com.stash.feature.nowplaying.listen.MiniSessionFaces
import com.stash.feature.nowplaying.ui.hasNextInQueue
import com.stash.feature.nowplaying.ui.hasPreviousInQueue
import com.stash.feature.nowplaying.ui.rememberSwipeToSkip

/** NØNSTOP compact transport. Playback, likes and room ownership stay in the existing models. */
private val MiniHeight = 56.dp
private val MiniCornerRadius = MiniProgressCorner
private val MiniShape = RoundedCornerShape(MiniCornerRadius)
val MiniPlayerMargin = 14.dp

@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundVisible: Boolean = true,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    listen: ListenTogetherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept as a State and read only inside the progress drawing below: the progress line redraws per
    // 250ms tick without recomposing the card (which shows on every screen).
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    val together by listen.state.collectAsStateWithLifecycle()
    val room = together as? ListenTogetherState.InRoom

    AnimatedVisibility(
        visible = uiState.hasTrack,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        val press = remember { MutableInteractionSource() }
        val track = uiState.currentTrack
        // Swipe the song sideways to skip, with Now Playing's art threshold (rememberSwipeToSkip).
        // A listener can't skip the room's song; a host's skips go to the room, which decides.
        val isListener = LocalListenTogetherRole.current == ListenTogetherRole.LISTENER
        val seekEnabled = !isListener && uiState.isSeekable && uiState.durationMs > 0L
        val seek = rememberMiniPlayerSeekState(
            track?.let { Triple(it.id, it.youtubeId, uiState.currentIndex) }, position, seekEnabled,
        )
        val repeatAll = uiState.repeatMode == RepeatMode.ALL
        val canNext = room != null || hasNextInQueue(uiState.queue.size, uiState.currentIndex, repeatAll)
        val canPrevious = room != null || hasPreviousInQueue(uiState.queue.size, uiState.currentIndex, repeatAll)
        val swipe = rememberSwipeToSkip(
            trackKey = track?.let { it.id to uiState.currentIndex },
            enabled = track != null && !isListener,
            canNext = canNext,
            canPrevious = canPrevious,
            onNext = viewModel::onSkipNext,
            onPrevious = viewModel::onSwipePrevious,
        )
        val friction = rememberMiniPlayerFriction(
            seek, uiState.isPlaying, uiState.isBuffering && uiState.playWhenReady,
            backgroundVisible && uiState.hasTrack,
        )
        val cardCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
        val controlsCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
        val artworkCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
        val controlsBounds: () -> Rect = {
            val card = cardCoordinates.value
            val controls = controlsCoordinates.value
            if (card?.isAttached == true && controls?.isAttached == true)
                card.localBoundingBoxOf(controls, clipBounds = false) else Rect.Zero
        }
        val artworkBounds: () -> Rect = {
            val card = cardCoordinates.value
            val artwork = artworkCoordinates.value
            if (card?.isAttached == true && artwork?.isAttached == true)
                card.localBoundingBoxOf(artwork, clipBounds = false) else Rect.Zero
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = MiniPlayerMargin, end = MiniPlayerMargin, bottom = 10.dp)
                .onGloballyPositioned { cardCoordinates.value = it }
                .miniPlayerGestureInput(
                    seek = seek,
                    durationMs = uiState.durationMs,
                    cornerRadius = MiniCornerRadius,
                    enabled = seekEnabled,
                    onSeek = viewModel::onSeekTo,
                    onExpand = onExpand,
                    bodyInteractions = press,
                    controlsBounds = controlsBounds,
                    artworkBounds = artworkBounds,
                )
                .miniPlayerFriction(position, uiState.durationMs, seek, friction, controlsBounds)
                .sonoraMiniGlass(MiniShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MiniHeight)
                    .indication(press, ripple())
                    // Preserve swipe navigation for assistive technology as well.
                    .semantics {
                        onClick("Open player") { onExpand(); true }
                        if (!isListener) {
                            customActions = buildList {
                                if (canPrevious) add(CustomAccessibilityAction("Previous song") {
                                    viewModel.onSwipePrevious(); true
                                })
                                if (canNext) add(CustomAccessibilityAction("Next song") {
                                    viewModel.onSkipNext(); true
                                })
                            }
                        }
                    }
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The song: cover, title and artist. It takes the swipe and moves with it, clipped
                // to its own slot; the controls stay put, so a swipe can't start on play/pause, and
                // a tap here still opens Now Playing (the drag claims only a sideways move past
                // touch slop).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(swipe.gesture)
                        .clipToBounds(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(swipe.content),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val artModel = track?.albumArtPath ?: track?.albumArtUrl
                        val coverShape = RoundedCornerShape(6.dp)
                        val artworkModifier = Modifier
                            .size(37.dp)
                            .onGloballyPositioned { artworkCoordinates.value = it }
                            .clip(coverShape)
                        if (artModel != null) {
                            // Remembered per cover: a fresh request each recomposition makes Coil treat
                            // it as a new load.
                            val context = LocalContext.current
                            val artRequest = remember(artModel) {
                                ImageRequest.Builder(context).data(artModel).build()
                            }
                            AsyncImage(
                                model = artRequest,
                                contentDescription = "Album art",
                                contentScale = ContentScale.Crop,
                                modifier = artworkModifier,
                            )
                        } else {
                            Box(
                                artworkModifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Title (600 14) and artist (400 12, --wp-text-2).
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // primaryArtist() collapses collaboration credits
                            // ("Metro Boomin, Travis Scott" → "Metro Boomin").
                            // remember keeps the split off the hot path: this
                            // composable recomposes on every progress tick.
                            val artist = remember(track?.artist) { track?.artist?.primaryArtist() }
                            Text(
                                text = track?.title ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF6F5FA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = artist ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = Color(0xFFADB7CE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                room?.let { MiniSessionFaces(it) }

                // Real local-library like toggle; queue/skip stay in the expanded player.
                Row(
                    modifier = Modifier.onGloballyPositioned { controlsCoordinates.value = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniIconButton(onClick = viewModel::onLikeTap) {
                        Icon(
                            imageVector = if (track?.stashLikedAt != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (track?.stashLikedAt != null) "Unlike song" else "Like song",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp),
                        )
                    }

                    // Keep pause available during buffering.
                    val playPress = remember { MutableInteractionSource() }
                    val wantsToPlay = uiState.playWhenReady || uiState.isPlaying
                    val loading = uiState.isBuffering && wantsToPlay
                    val playAction = if (wantsToPlay) "Pause" else "Play"
                    val playState = when {
                        loading -> "Loading"
                        uiState.isPlaying -> "Playing"
                        else -> "Paused"
                    }
                    Box(
                        modifier = Modifier
                            .size(MiniTouchTarget)
                            .clickable(
                                interactionSource = playPress,
                                indication = null,
                                role = Role.Button,
                                onClick = viewModel::onPlayPauseClick,
                            )
                            .semantics {
                                contentDescription = playAction
                                stateDescription = playState
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = Color(0xFFF6F5FA),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = if (wantsToPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }


                }
            }
            MiniPlayerSeekTarget(
                position = position,
                durationMs = uiState.durationMs,
                seek = seek,
                enabled = seekEnabled,
                onSeek = viewModel::onSeekTo,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The minimum touch target for every mini player control (Material's 48dp). */
private val MiniTouchTarget = 48.dp

/** Uncontained controls with 48dp touch targets. */
@Composable
private fun MiniIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(MiniTouchTarget)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 18.dp),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}
