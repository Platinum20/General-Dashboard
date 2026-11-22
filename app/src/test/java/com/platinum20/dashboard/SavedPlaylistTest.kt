package com.platinum20.dashboard

import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.ui.screens.savedCountLine
import com.platinum20.dashboard.ui.screens.savedPlayable
import org.junit.Assert.assertEquals
import org.junit.Test

/** La playlist "Salvati" in Media: la riga sotto il nome e cosa parte con Riproduci. */
class SavedPlaylistTest {
    @Test
    fun rigaDelConteggio() {
        assertEquals("5 video", savedCountLine(5, video = true))
        assertEquals("1 salvato", savedCountLine(1, video = false))
        assertEquals("8 salvati", savedCountLine(8, video = false))
    }

    @Test
    fun riproduciPrendeSoloQuelloCheEntraInCoda() {
        val brano = ResumeItem(ResumeKind.SPOTIFY, "spotify:track:1", "Brano", "", "", at = 1L)
        val album = ResumeItem(ResumeKind.SPOTIFY, "spotify:album:2", "Album", "", "", at = 2L)
        val video = ResumeItem(ResumeKind.YOUTUBE, "https://www.youtube.com/watch?v=abc", "Video", "", "", at = 3L)
        val telefono = ResumeItem(ResumeKind.VIDEO, "content://x", "Del telefono", "", "", at = 4L)
        assertEquals(listOf(brano, video), savedPlayable(listOf(brano, album, video, telefono)))
    }
}
