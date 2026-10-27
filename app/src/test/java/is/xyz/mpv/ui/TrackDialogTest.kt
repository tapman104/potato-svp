package `is`.xyz.mpv.ui

import `is`.xyz.mpv.Track
import `is`.xyz.mpv.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDialogTest {

    @Test
    fun testTrackDisplayNameFallbackHierarchy() {
        // 1. Prefer title if present
        val trackWithBoth = Track(
            id = 2,
            type = TrackType.Audio,
            lang = "eng",
            title = "English Stereo"
        )
        assertEquals("English Stereo", getTrackDisplayName(trackWithBoth))

        // 2. Fall back to lang if title is null
        val trackWithLangOnly = Track(
            id = 3,
            type = TrackType.Audio,
            lang = "jpn",
            title = null
        )
        assertEquals("jpn", getTrackDisplayName(trackWithLangOnly))

        // 3. Fall back to lang if title is blank
        val trackWithBlankTitle = Track(
            id = 3,
            type = TrackType.Audio,
            lang = "jpn",
            title = "   "
        )
        assertEquals("jpn", getTrackDisplayName(trackWithBlankTitle))

        // 4. Fall back to generic "Track {id}" if both are null
        val trackWithNeither = Track(
            id = 7,
            type = TrackType.Subtitle,
            lang = null,
            title = null
        )
        assertEquals("Track 7", getTrackDisplayName(trackWithNeither))

        // 5. Fall back to generic "Track {id}" if both are blank
        val trackWithBlanks = Track(
            id = 9,
            type = TrackType.Audio,
            lang = "",
            title = "  "
        )
        assertEquals("Track 9", getTrackDisplayName(trackWithBlanks))
    }
}
