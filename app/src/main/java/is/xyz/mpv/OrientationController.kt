package `is`.xyz.mpv

import android.content.pm.ActivityInfo

enum class OrientationMode {
    SYSTEM, PORTRAIT, LANDSCAPE, AUTO_BY_VIDEO
}

/** Ingredients OrientationController needs — sourced from PlayerViewModel. */
data class OrientationInput(
    val mode: OrientationMode,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoRotationDegrees: Int,
)

sealed interface OrientationResult {
    data object FollowSystem : OrientationResult
    data class Locked(val androidOrientation: Int) : OrientationResult
}

/**
 * Pure policy: given the current mode + video metadata, decide what orientation
 * the Activity should request. Holds no state, touches no Android lifecycle APIs
 * beyond the ActivityInfo constants (just int constants, safe to reference anywhere).
 */
class OrientationController {

    fun resolve(input: OrientationInput): OrientationResult {
        return when (input.mode) {
            OrientationMode.SYSTEM ->
                OrientationResult.FollowSystem

            OrientationMode.PORTRAIT ->
                OrientationResult.Locked(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

            OrientationMode.LANDSCAPE ->
                OrientationResult.Locked(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

            OrientationMode.AUTO_BY_VIDEO -> {
                if (input.videoWidth <= 0 || input.videoHeight <= 0) {
                    // No dimensions yet (e.g. file still loading) — don't force a guess.
                    OrientationResult.FollowSystem
                } else {
                    val isSwapped = input.videoRotationDegrees == 90 ||
                        input.videoRotationDegrees == 270
                    val effectiveW = if (isSwapped) input.videoHeight else input.videoWidth
                    val effectiveH = if (isSwapped) input.videoWidth else input.videoHeight

                    val orientation = if (effectiveW >= effectiveH)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                    OrientationResult.Locked(orientation)
                }
            }
        }
    }
}
