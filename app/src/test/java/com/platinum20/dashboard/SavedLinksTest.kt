package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SavedLinksTest {
    @Test
    fun mediaRoundTripsFromNoteText() {
        val item = ResumeItem(ResumeKind.SPOTIFY, "spotify:track:abc", "Bohemian Rhapsody", "Queen", positionMs = 90_000, at = 1L)
        val text = SavedLinks.noteText(SavedLink.Media(item))
        val found = SavedLinks.find(text) as SavedLink.Media
        assertEquals("spotify:track:abc", found.item.key)
        assertEquals(ResumeKind.SPOTIFY, found.item.kind)
        // Le canzoni salvate ripartono da capo.
        assertEquals(0L, found.item.positionMs)
        val note = Note(1, text)
        assertEquals("Bohemian Rhapsody", note.title)
        assertEquals("Queen", note.body)
    }

    @Test
    fun articleRoundTrips() {
        val link = SavedLink.Article("https://example.com/a?b=1&c=2", "Il Post", "Titolo & altro")
        assertEquals(link, SavedLinks.find(SavedLinks.noteText(link)))
    }

    @Test
    fun plainNotesHaveNoLink() {
        assertNull(SavedLinks.find("Spesa\nlatte, pane"))
    }
}
