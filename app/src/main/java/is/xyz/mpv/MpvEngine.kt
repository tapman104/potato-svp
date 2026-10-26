package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface

/**
 * Snapshot of video geometry, as last reported by mpv.
 * All zero/default until the first file has loaded and reported dimensions.
 */
data class VideoGeometry(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
)

open class MpvEngine(private val appContext: Context) {

    /** Called whenever mpv reports a change to width, height, or rotation. */
    var onVideoGeometryChanged: ((VideoGeometry) -> Unit)? = null

    /** Called whenever mpv reports a change to position or duration. */
    var onPlaybackProgressChanged: ((positionSec: Double, durationSec: Double) -> Unit)? = null

    private var currentGeometry = VideoGeometry()
    private var currentPositionSec: Double = 0.0
    private var currentDurationSec: Double = 0.0

    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(appContext.applicationContext)

        /* set normal options (user-supplied config can override) */
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
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
        MPVLib.destroy()
    }

    protected open fun initOptions() {}
    protected open fun postInitOptions() {}

    protected open fun observeProperties() {}

    private var isSurfaceAttached: Boolean = false
    private var filePath: String? = null

    /**
     * Set the file to be played. If the surface is already attached, it loads immediately.
     */
    fun playFile(filePath: String) {
        clearGeometry()
        clearProgress()
        if (isSurfaceAttached) {
            MPVLib.setPropertyString("vo", voInUse)
            MPVLib.command(arrayOf("loadfile", filePath))
        } else {
            this.filePath = filePath
        }
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
        MPVLib.setPropertyString("vo", vo)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    fun onSurfaceCreated(surface: Surface) {
        Log.w(TAG, "attaching surface")
        isSurfaceAttached = true
        MPVLib.attachSurface(surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("force-window", "yes")

        // We disable video output when the context disappears, enable it back unconditionally before any file load
        MPVLib.setPropertyString("vo", voInUse)

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        }
    }

    fun onSurfaceDestroyed() {
        Log.w(TAG, "detaching surface")
        isSurfaceAttached = false
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        // Note that before calling detachSurface() we need to be sure that libmpv
        // is done using the surface.
        // FIXME: There could be a race condition here, because I don't think
        // setting a property will wait for VO deinit.
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "mpv"
    }
}
