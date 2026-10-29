package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
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
    val isPlaying: Boolean? = null,
    val currentFileName: String? = null,
    val engineStatus: EngineStatus = EngineStatus.NotStarted,
    val trackState: TrackState = TrackState(),
) {
    val isEngineReady: Boolean get() = engineStatus is EngineStatus.Ready
}

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

class PlayerViewModel(
    private val appContext: Context,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val eventHandler = EngineEventHandler()

    // -- engine is public so PlayScreen can hand it to MpvSurfaceView -------
    val engine: MpvEngine = MpvEngine(appContext, eventHandler)

    val trackState: StateFlow<TrackState> = eventHandler.trackState

    private val preferencesRepository = UserPreferencesRepository(appContext)

    private val _explicitFileName = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlayerUiState> = combine(
        _explicitFileName,
        engine.status,
        eventHandler.trackState,
    ) { explicitName, status, tracks ->
        // Precedence rule: An explicitly-set filename (e.g. from loadFile or loadUri)
        // takes precedence over mpv's reported media-title property.
        // mpv's mediaTitle is used as a fallback when explicitName is null or blank.
        val resolvedFileName = if (!explicitName.isNullOrBlank()) explicitName else tracks.mediaTitle

        PlayerUiState(
            isPlaying = tracks.isPaused?.let { !it },
            currentFileName = resolvedFileName,
            engineStatus = status,
            trackState = tracks,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerUiState(),
    )

    private val logObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            Log.i("mpv-native", "[$prefix] $text".trimEnd())
        }
    }

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
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrientationState(),
    )

    val subFontSize: StateFlow<Int> = preferencesRepository.subFontSize
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 55)
    val subPos: StateFlow<Int> = preferencesRepository.subPos
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 100)

    init {
        MPVLib.addLogObserver(logObserver)
        engine.onVideoGeometryChanged = { geometry ->
            Log.i(TAG, "Geometry changed: ${geometry.width}x${geometry.height}, rot=${geometry.rotationDegrees}")
            _videoGeometry.update { geometry }
        }
        engine.onPlaybackProgressChanged = { pos, dur ->
            _playbackProgress.update { PlaybackProgress(positionSec = pos, durationSec = dur) }
        }
        scope.launch {
            engine.status.collect { status ->
                if (status is EngineStatus.Ready) {
                    engine.setSubFontSize(subFontSize.value)
                    engine.setSubPos(subPos.value)
                }
            }
        }
        initEngine()
    }

    private fun initEngine() {
        scope.launch {
            try {
                engine.initialize(
                    configDir = appContext.filesDir.path,
                    cacheDir  = appContext.cacheDir.path,
                )
                Log.i(TAG, "Engine init success")
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
        _explicitFileName.value = name
        _playbackProgress.value = PlaybackProgress()
        engine.playFile(path)
    }

    fun togglePlayPause() {
        engine.togglePause()
    }

    fun seekTo(positionSec: Double) {
        Log.i(TAG, "seekTo: $positionSec")
        engine.seekTo(positionSec)
    }

    fun selectAudioTrack(id: Int) {
        engine.selectAudioTrack(id)
    }

    fun selectSubtitleTrack(id: Int?) {
        engine.selectSubtitleTrack(id)
    }

    fun previewSubFontSize(size: Int) {
        engine.setSubFontSize(size)
    }

    fun commitSubFontSize(size: Int) {
        scope.launch {
            preferencesRepository.setSubFontSize(size)
        }
    }

    fun previewSubPos(pos: Int) {
        engine.setSubPos(pos)
    }

    fun commitSubPos(pos: Int) {
        scope.launch {
            preferencesRepository.setSubPos(pos)
        }
    }

    fun setOrientationMode(mode: OrientationMode) {
        scope.launch {
            preferencesRepository.setOrientationMode(mode)
        }
    }

    // -- Lifecycle -----------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        MPVLib.removeLogObserver(logObserver)
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
