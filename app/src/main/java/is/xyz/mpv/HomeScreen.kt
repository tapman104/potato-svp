package `is`.xyz.mpv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Minimal placeholder picker screen. Replace with a real browser/library
 * screen later (folder/video grid, recents, etc.) — this is scoped only to
 * unblock MainActivity's split from PlayerActivity.
 */
@Composable
fun HomeScreen(
    onOpenFileClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = onOpenFileClick) {
            Text("Open video")
        }
    }
}
