package `is`.xyz.mpv

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PlayerViewModelTest {

    private fun createTestContext(): ContextWrapper {
        val testDir = File(System.getProperty("java.io.tmpdir"), "test_pv_${System.currentTimeMillis()}").apply { mkdirs() }
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testDir
            override fun getCacheDir(): File = testDir
            override fun getDataDir(): File = testDir
        }
    }

    @Before
    fun setUp() {
        MPVLib.clearObservers()
    }

    @Test
    fun testPlayFileResetsTrackStateOnNewFileLoad() {
        val context = createTestContext()
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = PlayerViewModel(context, coroutineScope = testScope)

        // Simulate loaded tracks and playback state from mpv
        MPVLib.eventProperty("pause", false)
        MPVLib.eventProperty("media-title", "Previous Video")
        assertEquals(true, viewModel.uiState.value.isPlaying)
        assertEquals("Previous Video", viewModel.uiState.value.currentFileName)

        // Switching to a new file via loadFile -> engine.playFile must reset track and pause state
        viewModel.loadFile("/path/to/next_video.mp4")

        val trackState = viewModel.trackState.value
        assertTrue(trackState.audioTracks.isEmpty())
        assertTrue(trackState.subtitleTracks.isEmpty())
        assertNull(trackState.selectedAudioId)
        assertNull(trackState.selectedSubtitleId)
        assertNull(trackState.mediaTitle)
        assertNull(trackState.isPaused)

        // isPlaying must also reset to null (undetermined) for the new file
        assertNull(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun testUiStateIsPlayingBeforeAndAfterPauseEvents() {
        val context = createTestContext()
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = PlayerViewModel(context, coroutineScope = testScope)

        // 1. Before any pause event has been received: uiState.isPlaying is null
        assertNull(viewModel.uiState.value.isPlaying)

        // 2. mpv reports pause = false: uiState.isPlaying is true
        MPVLib.eventProperty("pause", false)
        assertEquals(true, viewModel.uiState.value.isPlaying)

        // 3. mpv reports pause = true: uiState.isPlaying is false
        MPVLib.eventProperty("pause", true)
        assertEquals(false, viewModel.uiState.value.isPlaying)
    }
}

