package `is`.xyz.mpv

import android.app.Activity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

        // Mid layer: Tap detector over empty video area.
        // Child interactive controls (IconButton, Slider) consume pointer down events
        // via their internal clickable/drag gestures. Because detectTapGestures relies on
        // awaitFirstDown(requireUnconsumed = true), it ignores already-consumed down events,
        // preventing button and seek bar taps from leaking through to toggle controlsVisible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (activeDialog == TrackDialogType.NONE) {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) resetHideTimer()
                            }
                        }
                    )
                }
        )

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

        // Overlay: Bottom app bar with play/pause toggle and seek bar
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
                isPlaying = uiState.isPlaying,
                isEnabled = uiState.isEngineReady,
                onPlayPauseClick = {
                    resetHideTimer()
                    viewModel.togglePlayPause()
                },
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
                    }
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
    isPlaying: Boolean,
    isEnabled: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Double) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress by playbackProgressFlow.collectAsState()
    PlayerBottomBar(
        isPlaying = isPlaying,
        isEnabled = isEnabled,
        positionSec = progress.positionSec,
        durationSec = progress.durationSec,
        onPlayPauseClick = onPlayPauseClick,
        onSeek = onSeek,
        onSeekStarted = onSeekStarted,
        modifier = modifier
    )
}

