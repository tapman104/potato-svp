package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class MpvSurfaceView(
    context: Context,
    attrs: AttributeSet?,
    private val engine: MpvEngine
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    /** Secondary constructor for use from Compose [AndroidView] — no AttributeSet available. */
    constructor(context: Context, engine: MpvEngine) : this(context, null, engine)

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        engine.onSurfaceCreated(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        engine.onSurfaceDestroyed()
    }

    fun detachCallback() {
        holder.removeCallback(this)
    }
}
