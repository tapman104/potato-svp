package `is`.xyz.mpv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerBottomBar(
    isEnabled: Boolean,
    positionSec: Double,
    durationSec: Double,
    onSeek: (Double) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
    )
    Box(
        modifier = modifier.background(brush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PlayerSeekBar(
                positionSec = positionSec,
                durationSec = durationSec,
                enabled = isEnabled,
                onSeek = onSeek,
                onSeekStarted = onSeekStarted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
