package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of video geometry, as last reported by mpv.
 * All zero/default until the first file has loaded and reported dimensions.
 */
data class VideoGeometry(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
)

/**
 * Explicit lifecycle status for the native mpv engine.
 */
sealed interface EngineStatus {
    data object NotStarted : EngineStatus
    data object Starting : EngineStatus
    data object Ready : EngineStatus
    data class Failed(val reason: String) : EngineStatus
}

open class MpvEngine(private val appContext: Context) {

    /** Called whenever mpv reports a change to width, height, or rotation. */
    var onVideoGeometryChanged: ((VideoGeometry) -> Unit)? = null

    /** Called whenever mpv reports a change to position or duration. */
    var onPlaybackProgressChanged: ((positionSec: Double, durationSec: Double) -> Unit)? = null

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.NotStarted)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    val isReady: Boolean get() = _status.value is EngineStatus.Ready

    private val lock = Any()
    private var currentGeometry = VideoGeometry()
    private var currentPositionSec: Double = 0.0
    private var currentDurationSec: Double = 0.0

    private var isSurfaceAttached: Boolean = false
    private var pendingSurface: Surface? = null
    private var pendingSurfaceSize: Pair<Int, Int>? = null
    private var filePath: String? = null
    private var voInUse: String = "gpu"

    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        synchronized(lock) {
            if (_status.value is EngineStatus.Ready || _status.value is EngineStatus.Starting) {
                Log.w(TAG, "initialize called when engine is already ${_status.value}")
                return
            }
            _status.value = EngineStatus.Starting

            try {
                MPVLib.create(appContext.applicationContext)

                /* set normal options (user-supplied config can override) */
                MPVLib.setOptionString("config", "yes")
                MPVLib.setOptionString("config-dir", configDir)
                for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir")) {
                    MPVLib.setOptionString(opt, cacheDir)
                }
                initOptions()

                MPVLib.init()

                /* set hardcoded options */
                postInitOptions()
                // could mess up VO init before surfaceCreated() is called
                MPVLib.setOptionString("force-window", "no")
                // need to idle at least once for playFile() logic to work
                MPVLib.setOptionString("idle", "once")

                observeProperties()
                observeGeometryProperties()
                observeProgressProperties()
                observeTrackProperties()

                _status.value = EngineStatus.Ready
                Log.i(TAG, "libmpv initialized successfully")
                flushPendingLocked()
            } catch (e: Throwable) {
                val reason = e.message ?: "Unknown initialization error"
                Log.e(TAG, "libmpv initialization failed: $reason", e)
                _status.value = EngineStatus.Failed(reason)
                throw e
            }
        }
    }

    private fun observeGeometryProperties() {
        MPVLib.observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("video-rotate", MPVLib.MpvFormat.MPV_FORMAT_INT64)
    }

    private fun observeProgressProperties() {
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
    }

    private fun observeTrackProperties() {
        MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NODE)
        MPVLib.observeProperty("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    private fun flushPendingLocked() {
        pendingSurfaceSize?.let { (w, h) ->
            MPVLib.setPropertyString("android-surface-size", "${w}x$h")
            pendingSurfaceSize = null
        }

        val surfaceToAttach = pendingSurface
        if (surfaceToAttach != null) {
            pendingSurface = null
            attachSurfaceLocked(surfaceToAttach)
        } else if (isSurfaceAttached && filePath != null) {
            val path = filePath as String
            filePath = null
            MPVLib.command(arrayOf("loadfile", path))
        }
    }

    private fun attachSurfaceLocked(surface: Surface) {
        Log.w(TAG, "attaching surface")
        isSurfaceAttached = true
        MPVLib.attachSurface(surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("force-window", "yes")

        // We disable video output when the context disappears, enable it back unconditionally before any file load
        MPVLib.setPropertyString("vo", voInUse)

        if (filePath != null) {
            val path = filePath as String
            filePath = null
            MPVLib.command(arrayOf("loadfile", path))
        }
    }

    private fun detachSurfaceInternal() {
        try {
            MPVLib.setPropertyString("vo", "null")
            MPVLib.setPropertyString("force-window", "no")

            // Wait for VO to deinitialize on the mpv core thread before releasing the native window.
            // In libmpv, getProperty queries synchronously block on the core thread.
            // vo-configured returns false once VO teardown completes.
            var attempts = 0
            while (attempts < 20 && MPVLib.getPropertyBoolean("vo-configured") == true) {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                attempts++
            }
            MPVLib.detachSurface()
        } catch (e: Exception) {
            Log.e(TAG, "Error detaching surface", e)
        }
    }

    /**
     * Feed a geometry-relevant property update in. Call this from the engine's
     * EventObserver.eventProperty(property, value: Long) override — see
     * PlayerViewModel for wiring.
     *
     * Returns true if the property was consumed (was a geometry property).
     */
    fun onPropertyLong(property: String, value: Long): Boolean {
        val updated = when (property) {
            "width" -> currentGeometry.copy(width = value.toInt())
            "height" -> currentGeometry.copy(height = value.toInt())
            "video-rotate" -> currentGeometry.copy(rotationDegrees = value.toInt())
            else -> return false
        }
        currentGeometry = updated
        onVideoGeometryChanged?.invoke(updated)
        return true
    }

    /**
     * Feed a progress-relevant property update in. Call this from the engine's
     * EventObserver.eventProperty(property, value: Double) override — see
     * PlayerViewModel for wiring.
     *
     * Returns true if the property was consumed.
     */
    fun onPropertyDouble(property: String, value: Double): Boolean {
        when (property) {
            "time-pos" -> currentPositionSec = value
            "duration" -> currentDurationSec = value
            else -> return false
        }
        onPlaybackProgressChanged?.invoke(currentPositionSec, currentDurationSec)
        return true
    }

    /** Reset geometry when a new file starts loading, so stale dims aren't briefly reused. */
    fun clearGeometry() {
        currentGeometry = VideoGeometry()
        onVideoGeometryChanged?.invoke(currentGeometry)
    }

    /** Reset progress when a new file starts loading. */
    fun clearProgress() {
        currentPositionSec = 0.0
        currentDurationSec = 0.0
        onPlaybackProgressChanged?.invoke(0.0, 0.0)
    }

    /**
     * Deinitialize libmpv.
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        synchronized(lock) {
            pendingSurface = null
            filePath = null
            pendingSurfaceSize = null

            if (isSurfaceAttached) {
                isSurfaceAttached = false
                if (isReady) {
                    detachSurfaceInternal()
                }
            }

            onVideoGeometryChanged = null
            onPlaybackProgressChanged = null

            if (isReady || _status.value is EngineStatus.Starting) {
                try {
                    MPVLib.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying MPVLib", e)
                }
            }
            _status.value = EngineStatus.NotStarted
        }
    }

    protected open fun initOptions() {}
    protected open fun postInitOptions() {}

    protected open fun observeProperties() {
        MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    /**
     * Set the file to be played. If the engine is ready and the surface is attached,
     * it loads immediately; otherwise it is held as pending until ready and attached.
     */
    fun playFile(filePath: String) {
        clearGeometry()
        clearProgress()
        synchronized(lock) {
            if (isReady && isSurfaceAttached) {
                this.filePath = null
                MPVLib.setPropertyString("vo", voInUse)
                MPVLib.command(arrayOf("loadfile", filePath))
            } else {
                this.filePath = filePath
            }
        }
    }

    /**
     * Toggle play/pause state. Guarded by [isReady].
     */
    fun togglePause() {
        synchronized(lock) {
            if (!isReady) return
            val paused = MPVLib.getPropertyBoolean("pause") ?: return
            MPVLib.setPropertyBoolean("pause", !paused)
        }
    }

    /**
     * Seek to an absolute position in seconds. Guarded by [isReady].
     */
    fun seekTo(positionSec: Double) {
        synchronized(lock) {
            if (!isReady) return
            MPVLib.command(arrayOf("seek", positionSec.toString(), "absolute"))
        }
    }

    /**
     * Fetch the current track list node from mpv. Guarded by [isReady].
     */
    fun getTrackList(): MPVNode? {
        synchronized(lock) {
            if (!isReady) return null
            return MPVLib.getPropertyNode("track-list")
        }
    }

    /**
     * Select audio track by ID. Guarded by [isReady].
     */
    fun selectAudioTrack(trackId: Int) {
        synchronized(lock) {
            if (!isReady) return
            MPVLib.setPropertyString("aid", if (trackId <= 0) "no" else trackId.toString())
        }
    }

    /**
     * Select subtitle track by ID, or null to turn subtitles off. Guarded by [isReady].
     */
    fun selectSubtitleTrack(trackId: Int?) {
        synchronized(lock) {
            if (!isReady) return
            MPVLib.setPropertyString("sid", if (trackId == null || trackId <= 0) "no" else trackId.toString())
        }
    }

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        synchronized(lock) {
            voInUse = vo
            if (isReady) {
                MPVLib.setOptionString("vo", vo)
                MPVLib.setPropertyString("vo", vo)
            }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        synchronized(lock) {
            if (isReady) {
                MPVLib.setPropertyString("android-surface-size", "${width}x$height")
            } else {
                pendingSurfaceSize = Pair(width, height)
            }
        }
    }

    fun onSurfaceCreated(surface: Surface) {
        synchronized(lock) {
            if (!isReady) {
                Log.i(TAG, "Surface created before engine is Ready; caching as pending")
                pendingSurface = surface
                return
            }
            attachSurfaceLocked(surface)
        }
    }

    fun onSurfaceDestroyed() {
        Log.w(TAG, "detaching surface")
        synchronized(lock) {
            pendingSurface = null
            if (!isSurfaceAttached) return
            isSurfaceAttached = false
            if (isReady) {
                detachSurfaceInternal()
            }
        }
    }

    companion object {
        private const val TAG = "mpv"
    }
}

