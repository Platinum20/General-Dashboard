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
        // Con la foto dell'articolo, per l'anteprima nelle note.
        val withImage = link.copy(image = "https://example.com/foto.jpg?w=600&h=400")
        assertEquals(withImage, SavedLinks.find(SavedLinks.noteText(withImage)))
    }

    @Test
    fun plainNotesHaveNoLink() {
        assertNull(SavedLinks.find("Spesa\nlatte, pane"))
    }

    @Test
    fun alreadySavedIsFoundByUrlOrMediaKey() {
        val article = SavedLink.Article("https://example.com/a/", "Open", "Titolo")
        val song = SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, "spotify:track:abc", "Brano", "Artista", positionMs = 5_000, at = 1L))
        val notes = listOf(
            Note(1, SavedLinks.noteText(article.copy(title = "Titolo cambiato"))),
            Note(2, SavedLinks.noteText(song)),
            Note(3, "Una nota qualsiasi"),
        )
        // Stesso indirizzo (anche senza la barra finale): è già salvato.
        assertEquals(listOf(1L), SavedLinks.notesWith(notes, article.copy(url = "https://example.com/a")).map { it.id })
        // Stesso brano, altro punto di ascolto: è lo stesso.
        assertEquals(listOf(2L), SavedLinks.notesWith(notes, SavedLink.Media(song.item.copy(positionMs = 60_000))).map { it.id })
        assertEquals(emptyList<Note>(), SavedLinks.notesWith(notes, SavedLink.Article("https://example.com/b", "Open", "Altro")))
    }

    @Test
    fun shareTextHasAnOpenableLink() {
        val video = SavedLink.Media(ResumeItem(ResumeKind.YOUTUBE, "xyz", "Video", "Canale", at = 1L))
        assertEquals("Video\nCanale\nhttps://youtu.be/xyz", SavedLinks.shareText(video))
        val song = SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, "spotify:track:abc", "Brano", "Artista", at = 1L))
        assertEquals("Brano\nArtista\nhttps://open.spotify.com/track/abc", SavedLinks.shareText(song))
        assertEquals("Titolo\nhttps://example.com/a", SavedLinks.shareText(SavedLink.Article("https://example.com/a", "Open", "Titolo")))
    }
}
