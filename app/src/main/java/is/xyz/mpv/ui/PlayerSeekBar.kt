package `is`.xyz.mpv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    val interactionSource = remember { MutableInteractionSource() }

    val displayPosition = if (isDragging) dragPosition.toDouble() else positionSec
    val maxDuration = durationSec.coerceAtLeast(0.0).toFloat()
    val isSeekEnabled = enabled && maxDuration > 0f
    val trackAlpha = if (isSeekEnabled) 1f else 0.4f

    val progress = if (maxDuration > 0f) {
        (displayPosition.toFloat() / maxDuration).coerceIn(0f, 1f)
    } else {
        0f
    }

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
            value = progress.coerceIn(0f, 1f),
            onValueChange = { newFraction ->
                if (!isDragging) {
                    onSeekStarted?.invoke()
                }
                isDragging = true
                dragPosition = (newFraction * maxDuration).coerceIn(0f, maxDuration)
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toDouble())
                isDragging = false
            },
            enabled = isSeekEnabled,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor         = Color.Transparent,
                activeTrackColor   = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor    = Color.Transparent,
                inactiveTickColor  = Color.Transparent
            ),
            track = { sliderState ->
                val range = sliderState.valueRange
                val fraction = if (range.endInclusive > range.start) {
                    ((sliderState.value - range.start) /
                            (range.endInclusive - range.start)).coerceIn(0f, 1f)
                } else 0f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val r = h / 2f

                    drawRoundRect(
                        color        = Color.White.copy(alpha = 0.2f),
                        size         = Size(w, h),
                        cornerRadius = CornerRadius(r, r)
                    )

                    if (fraction > 0f) {
                        drawRoundRect(
                            color        = Color.White.copy(alpha = trackAlpha),
                            size         = Size(w * fraction, h),
                            cornerRadius = CornerRadius(r, r)
                        )
                    }
                }
            },
            thumb = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = trackAlpha))
                    )
                }
            },
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
