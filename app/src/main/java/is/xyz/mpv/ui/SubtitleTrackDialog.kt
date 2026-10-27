package `is`.xyz.mpv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.Track

@Composable
fun SubtitleTrackDialog(
    tracks: List<Track>,
    selectedTrackId: Int?,
    onSelect: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = {
            Text(
                text = "Subtitle Track",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Dismiss")
            }
        }
    )
}
