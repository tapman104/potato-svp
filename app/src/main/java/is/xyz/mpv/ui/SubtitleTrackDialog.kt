package `is`.xyz.mpv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.Track

private enum class SubtitleDialogView {
    TRACKS,
    APPEARANCE
}

@Composable
fun SubtitleTrackDialog(
    tracks: List<Track>,
    selectedTrackId: Int?,
    onSelect: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
    subFontSize: Int,
    subPos: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontSizeCommit: (Int) -> Unit,
    onPosChange: (Int) -> Unit,
    onPosCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentView by remember { mutableStateOf(SubtitleDialogView.TRACKS) }
    var sliderFontSize by remember(subFontSize) { mutableFloatStateOf(subFontSize.toFloat()) }
    var sliderPos by remember(subPos) { mutableFloatStateOf(subPos.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentView == SubtitleDialogView.TRACKS) {
                    Text(
                        text = "Subtitle Track",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { currentView = SubtitleDialogView.APPEARANCE }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Subtitle Appearance"
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = { currentView = SubtitleDialogView.TRACKS }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Subtitle Tracks"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        },
        text = {
            when (currentView) {
                SubtitleDialogView.TRACKS -> {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        val isOffSelected = selectedTrackId == null || tracks.none { it.id == selectedTrackId }

                        // Explicit "Off" row - always present
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isOffSelected,
                                    onClick = {
                                        onSelect(null)
                                        onDismissRequest()
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isOffSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Off",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isOffSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isOffSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (tracks.isEmpty()) {
                            Text(
                                text = "No subtitle tracks available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                            )
                        } else {
                            tracks.forEach { track ->
                                val isSelected = !isOffSelected && (selectedTrackId == track.id)
                                val label = getTrackDisplayName(track)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = isSelected,
                                            onClick = {
                                                onSelect(track.id)
                                                onDismissRequest()
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!track.title.isNullOrBlank() && !track.lang.isNullOrBlank() && !track.title.trim().equals(track.lang.trim(), ignoreCase = true)) {
                                            Text(
                                                text = track.lang.trim(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SubtitleDialogView.APPEARANCE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Font size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = sliderFontSize.toInt().toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = sliderFontSize,
                            onValueChange = {
                                sliderFontSize = it
                                onFontSizeChange(it.toInt())
                            },
                            onValueChangeFinished = { onFontSizeCommit(sliderFontSize.toInt()) },
                            valueRange = 10f..120f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Position
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Position",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = sliderPos.toInt().toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = sliderPos,
                            onValueChange = {
                                sliderPos = it
                                onPosChange(it.toInt())
                            },
                            onValueChangeFinished = { onPosCommit(sliderPos.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Dismiss")
            }
        }
    )
}
