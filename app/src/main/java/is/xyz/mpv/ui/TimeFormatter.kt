package `is`.xyz.mpv.ui

import java.util.Locale

object TimeFormatter {
    /**
     * Formats seconds into mm:ss (or h:mm:ss if >= 1 hour).
     */
    fun formatTime(seconds: Double): String {
        if (seconds.isNaN() || seconds < 0) return "00:00"
        val totalSeconds = seconds.toLong()
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
