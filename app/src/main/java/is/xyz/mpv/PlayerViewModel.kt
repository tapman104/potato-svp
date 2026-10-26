package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

import android.net.Uri
import `is`.xyz.mpv.ui.UriUtils

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isEngineReady: Boolean = false,
    val currentFileName: String? = null,
)

data class PlaybackProgress(
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
)

/** Ingredients PlayerActivity needs to resolve + apply orientation. Dumb data only. */
data class OrientationState(
    val mode: OrientationMode = OrientationMode.AUTO_BY_VIDEO,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoRotationDegrees: Int = 0,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class PlayerViewModel(private val appContext: Context) : ViewModel(),
    MPVLib.EventObserver {

    // -- engine is public so PlayScreen can hand it to MpvSurfaceView -------
    val engine: MpvEngine = MpvEngine(appContext)

    private val preferencesRepository = UserPreferencesRepository(appContext)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val _videoGeometry = MutableStateFlow(VideoGeometry())

    /**
     * Combined state for PlayerActivity's orientation collector.
     * ViewModel does NOT resolve policy here — just exposes the raw ingredients.
     * OrientationController (owned by the Activity) does the resolving.
     */
    val orientationState: StateFlow<OrientationState> = combine(
        preferencesRepository.orientationMode,
        _videoGeometry,
    ) { mode, geometry ->
        OrientationState(
            mode = mode,
            videoWidth = geometry.width,
            videoHeight = geometry.height,
            videoRotationDegrees = geometry.rotationDegrees,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrientationState(),
    )

    init {
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(object : MPVLib.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) {
                Log.i("mpv-native", "[$prefix] $text".trimEnd())
            }
        })
        engine.onVideoGeometryChanged = { geometry ->
            Log.i(TAG, "Geometry changed: ${geometry.width}x${geometry.height}, rot=${geometry.rotationDegrees}")
            _videoGeometry.update { geometry }
        }
        engine.onPlaybackProgressChanged = { pos, dur ->
            _playbackProgress.update { PlaybackProgress(positionSec = pos, durationSec = dur) }
        }
        initEngine()
    }

    private fun initEngine() {
        viewModelScope.launch {
            try {
                engine.initialize(
                    configDir = appContext.filesDir.path,
                    cacheDir  = appContext.cacheDir.path,
                )
                MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)
                Log.i(TAG, "Engine init success")
                _uiState.update { it.copy(isEngineReady = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Engine init failed", e)
            }
        }
    }

    fun loadUri(uri: Uri) {
        val displayName = UriUtils.getDisplayName(appContext, uri)
        val playablePath = UriUtils.resolvePlayablePath(appContext, uri)
        loadFile(playablePath, displayName)
    }

    fun loadFile(path: String, displayName: String? = null) {
        val name = displayName ?: UriUtils.getDisplayNameFromPath(path)
        Log.i(TAG, "loadFile: path=$path, name=$name")
        _uiState.update { it.copy(currentFileName = name) }
        _playbackProgress.value = PlaybackProgress()
        engine.playFile(path)
    }

    fun togglePlayPause() {
        val paused = MPVLib.getPropertyBoolean("pause") ?: return
        MPVLib.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(positionSec: Double) {
        Log.i(TAG, "seekTo: $positionSec")
        MPVLib.command(arrayOf("seek", positionSec.toString(), "absolute"))
    }

    fun setOrientationMode(mode: OrientationMode) {
        viewModelScope.launch {
            preferencesRepository.setOrientationMode(mode)
        }
    }

    // -- MPVLib.EventObserver ------------------------------------------------

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Long) {
        engine.onPropertyLong(property, value)
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            _uiState.update { it.copy(isPlaying = !value) }
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (property == "media-title" && _uiState.value.currentFileName.isNullOrBlank()) {
            _uiState.update { it.copy(currentFileName = value) }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        engine.onPropertyDouble(property, value)
    }

    override fun event(eventId: Int) {}

    // -- Lifecycle -----------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        MPVLib.removeObserver(this)
        engine.destroy()
    }

    // -- Factory -------------------------------------------------------------

    companion object {
        private const val TAG = "PlayerViewModel"

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlayerViewModel(appContext.applicationContext) as T
            }
    }
}
