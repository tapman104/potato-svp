package `is`.xyz.mpv

import android.app.Activity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `is`.xyz.mpv.ui.AudioTrackDialog
import `is`.xyz.mpv.ui.PlayerBottomBar
import `is`.xyz.mpv.ui.PlayerTopBar
import `is`.xyz.mpv.ui.SubtitleTrackDialog
import `is`.xyz.mpv.ui.VideoSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class TrackDialogType {
    NONE,
    AUDIO,
    SUBTITLE
}

@Composable
fun PlayScreen(
    onOpenFileClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onControlsVisibilityChanged: ((Boolean) -> Unit)? = null,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            LocalContext.current
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val subFontSize by viewModel.subFontSize.collectAsState()
    val subPos by viewModel.subPos.collectAsState()
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var controlsVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf(TrackDialogType.NONE) }
    val coroutineScope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun resetHideTimer() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        onControlsVisibilityChanged?.invoke(controlsVisible)
    }

    LaunchedEffect(Unit) {
        resetHideTimer()
    }

    LaunchedEffect(activeDialog) {
        if (activeDialog != TrackDialogType.NONE) {
            hideJob?.cancel()
        }
    }

    val handleBack: () -> Unit = onBackClick ?: {
        if (backDispatcher?.hasEnabledCallbacks() == true) {
            backDispatcher.onBackPressed()
        } else {
            (context as? Activity)?.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Base layer: Fullscreen video surface
        VideoSurface(
            engine = viewModel.engine,
            modifier = Modifier.fillMaxSize()
        )

        // Mid layer: unified gesture surface.
        // playerGestureHandler uses detectTapGestures (requireUnconsumed = true) for taps/long-press
        // and awaitEachGesture (requireUnconsumed = false) for swipe/pinch, so interactive controls
        // drawn above (IconButton, Slider) still consume their own down events without interference.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestureHandler(
                    onSingleTap = {
                        if (activeDialog == TrackDialogType.NONE) {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) resetHideTimer()
                        }
                    },
                    onSeekBy = {
                        resetHideTimer()
                        viewModel.seekBy(it)
                    },
                    onSpeedChange = { viewModel.setSpeed(it.toDouble()) },
                    onZoomBy = {
                        resetHideTimer()
                        viewModel.zoomBy(it)
                    },
                    onDoubleTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) resetHideTimer()
                    },
                    getCurrentSpeed = { viewModel.getCurrentSpeed() },
                )
        )

        // Overlay: Center play/pause button
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        resetHideTimer()
                        viewModel.togglePlayPause()
                    },
                    enabled = uiState.isEngineReady && uiState.isPlaying != null
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .padding(14.dp)
                    ) {
                        when (uiState.isPlaying) {
                            true -> Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                            false -> Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                            null -> CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }

        // Overlay: Top app bar with back navigation and title
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            PlayerTopBar(
                title = uiState.currentFileName,
                isEngineReady = uiState.isEngineReady,
                onBackClick = {
                    resetHideTimer()
                    handleBack()
                },
                onOpenFileClick = onOpenFileClick?.let { onClick ->
                    {
                        resetHideTimer()
                        onClick()
                    }
                },
                onAudioTrackClick = {
                    hideJob?.cancel()
                    activeDialog = TrackDialogType.AUDIO
                },
                onSubtitleTrackClick = {
                    hideJob?.cancel()
                    activeDialog = TrackDialogType.SUBTITLE
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Overlay: Bottom app bar with seek bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            PlayerBottomBarHost(
                playbackProgressFlow = viewModel.playbackProgress,
                isEnabled = uiState.isEngineReady,
                onSeek = { pos ->
                    resetHideTimer()
                    viewModel.seekTo(pos)
                },
                onSeekStarted = {
                    hideJob?.cancel()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Track Selection Dialogs
        when (activeDialog) {
            TrackDialogType.AUDIO -> {
                AudioTrackDialog(
                    tracks = uiState.trackState.audioTracks,
                    selectedTrackId = uiState.trackState.selectedAudioId,
                    onSelect = { trackId ->
                        viewModel.selectAudioTrack(trackId)
                        activeDialog = TrackDialogType.NONE
                        resetHideTimer()
                    },
                    onDismissRequest = {
                        activeDialog = TrackDialogType.NONE
                        resetHideTimer()
                    }
                )
            }
            TrackDialogType.SUBTITLE -> {
                SubtitleTrackDialog(
                    tracks = uiState.trackState.subtitleTracks,
                    selectedTrackId = uiState.trackState.selectedSubtitleId,
                    onSelect = { trackId ->
                        viewModel.selectSubtitleTrack(trackId)
                        activeDialog = TrackDialogType.NONE
                        resetHideTimer()
                    },
                    onDismissRequest = {
                        activeDialog = TrackDialogType.NONE
                        resetHideTimer()
                    },
                    subFontSize = subFontSize,
                    subPos = subPos,
                    onFontSizeChange = { viewModel.previewSubFontSize(it) },
                    onFontSizeCommit = { viewModel.commitSubFontSize(it) },
                    onPosChange = { viewModel.previewSubPos(it) },
                    onPosCommit = { viewModel.commitSubPos(it) },
                )
            }
            TrackDialogType.NONE -> Unit
        }
    }
}

/**
 * Narrow collection point for high-frequency playback progress.
 * Only this wrapper (and PlayerBottomBar) recomposes on position ticks;
 * PlayScreen, VideoSurface, and PlayerTopBar do not.
 */
@Composable
private fun PlayerBottomBarHost(
    playbackProgressFlow: StateFlow<PlaybackProgress>,
    isEnabled: Boolean,
    onSeek: (Double) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress by playbackProgressFlow.collectAsState()
    PlayerBottomBar(
        isEnabled = isEnabled,
        positionSec = progress.positionSec,
        durationSec = progress.durationSec,
        onSeek = onSeek,
        onSeekStarted = onSeekStarted,
        modifier = modifier
    )
}