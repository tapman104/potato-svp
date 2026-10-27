package `is`.xyz.mpv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopBar(
    title: String?,
    onBackClick: () -> Unit,
    onOpenFileClick: (() -> Unit)? = null,
    onAudioTrackClick: (() -> Unit)? = null,
    onSubtitleTrackClick: (() -> Unit)? = null,
    isEngineReady: Boolean = true,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = if (!title.isNullOrBlank()) title else "Potato Player",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        actions = {
            if (onAudioTrackClick != null) {
                IconButton(
                    onClick = onAudioTrackClick,
                    enabled = isEngineReady
                ) {
                    Icon(
                        imageVector = Icons.Filled.Audiotrack,
                        contentDescription = "Audio track",
                        tint = if (isEngineReady) Color.White else Color.Gray
                    )
                }
            }
            if (onSubtitleTrackClick != null) {
                IconButton(
                    onClick = onSubtitleTrackClick,
                    enabled = isEngineReady
                ) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = "Subtitle track",
                        tint = if (isEngineReady) Color.White else Color.Gray
                    )
                }
            }
            if (onOpenFileClick != null) {
                IconButton(onClick = onOpenFileClick) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Open video file",
                        tint = Color.White
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

