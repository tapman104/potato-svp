package `is`.xyz.mpv

import android.util.Log
import com.potato.player.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TrackType {
    Audio,
    Subtitle
}

data class Track(
    val id: Int,
    val type: TrackType,
    val lang: String? = null,
    val title: String? = null,
    val isSelected: Boolean = false,
)

data class TrackState(
    val audioTracks: List<Track> = emptyList(),
    val subtitleTracks: List<Track> = emptyList(),
    val selectedAudioId: Int? = null,
    val selectedSubtitleId: Int? = null,
    val isPaused: Boolean? = null,
    val mediaTitle: String? = null,
)

/**
 * Handles raw MPV property-change events for audio and subtitle tracks ("track-list", "aid", "sid"),
 * as well as playback pause and media-title events ("pause", "media-title"), translating them
 * into typed, observable [TrackState].
 *
 * Precedence note on mediaTitle:
 * EngineEventHandler acts solely as an event translation layer for MPV native events and does not
 * track user-chosen filenames or enforce UI display precedence. The precedence rule ("an explicitly-set
 * filename takes priority over mpv's reported media-title property") is preserved and enforced in
 * PlayerViewModel when combining UI-provided file names with this state.
 */
class EngineEventHandler : MPVLib.EventObserver {

    private val _trackState = MutableStateFlow(TrackState())
    val trackState: StateFlow<TrackState> = _trackState.asStateFlow()

    override fun eventProperty(property: String, value: MPVNode) {
        if (property == "track-list") {
            handleTrackListNode(value)
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            Log.d(TAG, "Playback pause state updated from mpv: isPaused=$value")
            _trackState.update { it.copy(isPaused = value) }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "aid" -> handleAid(value)
            "sid" -> handleSid(value)
            "media-title" -> {
                _trackState.update { it.copy(mediaTitle = value) }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "aid" -> handleAid(value.toString())
            "sid" -> handleSid(value.toString())
        }
    }

    fun reset() {
        Log.d(TAG, "Resetting track and playback state")
        _trackState.value = TrackState()
    }

    fun handleTrackListNode(node: MPVNode) {
        val rawTracks = parseTrackList(node)
        val audio = rawTracks.filter { it.type == TrackType.Audio }
        val subs = rawTracks.filter { it.type == TrackType.Subtitle }

        // FIX #6: Gate per-track Log.i loop behind BuildConfig.DEBUG
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Parsed ${audio.size} audio tracks, ${subs.size} subtitle tracks from track-list node")
            for (track in audio) {
                Log.i(TAG, "  Audio track: id=${track.id}, lang=${track.lang}, title=${track.title}, selected=${track.isSelected}")
            }
            for (track in subs) {
                Log.i(TAG, "  Subtitle track: id=${track.id}, lang=${track.lang}, title=${track.title}, selected=${track.isSelected}")
            }
        }

        _trackState.update { current ->
            val resolvedAudioId = if (current.selectedAudioId != null && audio.any { it.id == current.selectedAudioId }) {
                current.selectedAudioId
            } else {
                audio.firstOrNull { it.isSelected }?.id
            }

            val resolvedSubId = if (current.selectedSubtitleId != null && subs.any { it.id == current.selectedSubtitleId }) {
                current.selectedSubtitleId
            } else {
                subs.firstOrNull { it.isSelected }?.id
            }

            val finalAudio = audio.map { track ->
                track.copy(isSelected = (resolvedAudioId != null && track.id == resolvedAudioId))
            }
            val finalSubs = subs.map { track ->
                track.copy(isSelected = (resolvedSubId != null && track.id == resolvedSubId))
            }

            current.copy(
                audioTracks = finalAudio,
                subtitleTracks = finalSubs,
                selectedAudioId = resolvedAudioId,
                selectedSubtitleId = resolvedSubId,
            )
        }
    }

    private fun handleAid(value: String) {
        val audioId = value.toIntOrNull() // "no" or invalid becomes null
        Log.i(TAG, "Audio track changed (aid): raw='$value', resolvedId=$audioId")
        _trackState.update { current ->
            val updatedAudio = current.audioTracks.map { track ->
                track.copy(isSelected = (audioId != null && track.id == audioId))
            }
            current.copy(
                audioTracks = updatedAudio,
                selectedAudioId = audioId,
            )
        }
    }

    private fun handleSid(value: String) {
        val subId = value.toIntOrNull() // "no" or invalid becomes null
        Log.i(TAG, "Subtitle track changed (sid): raw='$value', resolvedId=$subId")
        _trackState.update { current ->
            val updatedSubs = current.subtitleTracks.map { track ->
                track.copy(isSelected = (subId != null && track.id == subId))
            }
            current.copy(
                subtitleTracks = updatedSubs,
                selectedSubtitleId = subId,
            )
        }
    }

    companion object {
        private const val TAG = "EngineEventHandler"

        /**
         * Parses the raw "track-list" [MPVNode] into a list of [Track].
         * Filters to audio and subtitle tracks; video or unknown types are ignored.
         */
        fun parseTrackList(node: MPVNode): List<Track> {
            val items: List<MPVNode> = node.asArray()?.toList()
                ?: (0 until node.size()).mapNotNull { node.get(it) }

            return items.mapNotNull { item ->
                val typeStr = item.get("type")?.asString()
                val type = when (typeStr) {
                    "audio" -> TrackType.Audio
                    "sub" -> TrackType.Subtitle
                    else -> return@mapNotNull null
                }

                val id = item.get("id")?.asInt()?.toInt()
                    ?: item.get("id")?.asString()?.toIntOrNull()
                    ?: return@mapNotNull null

                val lang = item.get("lang")?.asString()?.takeIf { it.isNotBlank() }
                val title = item.get("title")?.asString()?.takeIf { it.isNotBlank() }
                val isSelected = item.get("selected")?.asBoolean()
                    ?: (item.get("selected")?.asString() == "yes")
                    ?: false

                Track(
                    id = id,
                    type = type,
                    lang = lang,
                    title = title,
                    isSelected = isSelected,
                )
            }
        }
    }
}
