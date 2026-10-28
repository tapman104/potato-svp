package `is`.xyz.mpv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerBottomBar(
    isPlaying: Boolean?,
    isEnabled: Boolean,
    positionSec: Double,
    durationSec: Double,
    onPlayPauseClick: () -> Unit,
    onSeek: (Double) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            PlayerSeekBar(
                positionSec = positionSec,
                durationSec = durationSec,
                enabled = isEnabled,
                onSeek = onSeek,
                onSeekStarted = onSeekStarted,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    enabled = isEnabled && isPlaying != null,
                ) {
                    when (isPlaying) {
                        true -> Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pause",
                            tint = if (isEnabled) Color.White else Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        false -> Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isEnabled) Color.White else Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        null -> CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = if (isEnabled) Color.White else Color.Gray,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }
        }
    }
}
