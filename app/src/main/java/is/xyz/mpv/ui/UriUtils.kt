package `is`.xyz.mpv.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import `is`.xyz.mpv.Utils
import java.io.File

object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * Resolves a human-readable display name from a Uri or path.
     * Queries ContentResolver for OpenableColumns.DISPLAY_NAME on content:// URIs,
     * falls back to the last path segment or raw URI string.
     */
    fun getDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve display name from ContentResolver for $uri", e)
            }
        }

        // Fallback to last path segment or filename
        return uri.lastPathSegment?.substringAfterLast('/')
            ?: uri.path?.substringAfterLast('/')
            ?: uri.toString()
    }

    /**
     * Extracts a display name from a raw filesystem path or URL.
     */
    fun getDisplayNameFromPath(path: String): String {
        return path.substringAfterLast('/')
    }

    /**
     * Translates a URI to a string that libmpv can consume:
     * 1. file:// -> raw filesystem path.
     * 2. content:// -> attempts to find the real path via /proc/self/fd/N (via Utils.findRealPath).
     *    If that fails (scoped storage / document provider), returns uri.toString() because
     *    FFmpeg in this build includes native 'android_content' protocol support.
     * 3. Network or other schemes -> data string as-is.
     */
    fun resolvePlayablePath(context: Context, uri: Uri): String {
        return when (uri.scheme) {
            "file" -> uri.path ?: uri.toString()
            "content" -> translateContentUri(context, uri)
            else -> uri.toString()
        }
    }

    private fun translateContentUri(context: Context, uri: Uri): String {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val realPath = Utils.findRealPath(pfd.fd)
                if (realPath != null) {
                    Log.d(TAG, "Resolved content URI $uri to real path: $realPath")
                    return realPath
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open file descriptor for $uri, falling back to content URI", e)
        }

        // FFmpeg libavformat has built-in 'android_content' protocol support for content:// URIs
        Log.d(TAG, "Passing content URI directly to mpv/ffmpeg: $uri")
        return uri.toString()
    }
}
