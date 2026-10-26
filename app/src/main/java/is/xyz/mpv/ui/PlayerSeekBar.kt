package `is`.xyz.mpv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerSeekBar(
    positionSec: Double,
    durationSec: Double,
    enabled: Boolean,
    onSeek: (Double) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val displayPosition = if (isDragging) dragPosition.toDouble() else positionSec
    val maxDuration = durationSec.coerceAtLeast(0.0).toFloat()
    val isSeekEnabled = enabled && maxDuration > 0f

    val sliderValue = if (maxDuration > 0f) {
        displayPosition.toFloat().coerceIn(0f, maxDuration)
    } else {
        0f
    }
    val sliderRange = if (maxDuration > 0f) 0f..maxDuration else 0f..1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = TimeFormatter.formatTime(displayPosition),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.widthIn(min = 40.dp)
        )
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                if (!isDragging) {
                    onSeekStarted?.invoke()
                }
                isDragging = true
                dragPosition = newValue
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toDouble())
                isDragging = false
            },
            valueRange = sliderRange,
            enabled = isSeekEnabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.Gray.copy(alpha = 0.4f),
                disabledInactiveTrackColor = Color.Gray.copy(alpha = 0.2f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(
            text = TimeFormatter.formatTime(durationSec),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.widthIn(min = 40.dp)
        )
    }
}
