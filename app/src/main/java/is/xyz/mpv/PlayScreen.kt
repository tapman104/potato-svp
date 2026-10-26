package `is`.xyz.mpv

import android.app.Activity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import `is`.xyz.mpv.ui.PlayerBottomBar
import `is`.xyz.mpv.ui.PlayerTopBar
import `is`.xyz.mpv.ui.VideoSurface

@Composable
fun PlayScreen(
    onOpenFileClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            LocalContext.current
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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

        // Overlay: Top app bar with back navigation and title
        PlayerTopBar(
            title = state.currentFileName,
            onBackClick = handleBack,
            onOpenFileClick = onOpenFileClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Overlay: Bottom app bar with play/pause toggle and seek bar
        PlayerBottomBar(
            isPlaying = state.isPlaying,
            isEnabled = state.isEngineReady,
            positionSec = state.positionSec,
            durationSec = state.durationSec,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }
}
