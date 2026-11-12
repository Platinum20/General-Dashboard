package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.photoLine
import com.platinum20.dashboard.ui.screens.timelineEntries
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 1.482 (Antonio, scelta B): video e brani in Media, articoli in News, nel calendario solo note e foto. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaterPlacesTest {
    private val video = SavedLinks.noteText(SavedLink.Media(ResumeItem(ResumeKind.YOUTUBE, "AAAAAAAAAAA", "Motore a razzo", "Veritasium", "img", at = 1)))
    private val song = SavedLinks.noteText(SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, "spotify:track:x", "Notturno", "", at = 1)))
    private val article = SavedLinks.noteText(SavedLink.Article("https://ilpost.it/a", "Il Post", "Il ponte"))
    private val notes = listOf(
        Note(1, video, createdAt = 100),
        Note(2, article, createdAt = 200),
        Note(3, "https://youtu.be/BBBBBBBBBBB", createdAt = 300),
        Note(4, "Idea regalo per Marta", createdAt = 400),
        Note(5, "Scontrino lavatrice\nscade a marzo 2028", createdAt = 500, photo = "/foto.jpg"),
        Note(6, song, createdAt = 600),
        // Un testo lungo che cita un video resta una nota.
        Note(7, "Ricetta\nfarina 200 g\nuova 2\nhttps://youtu.be/CCCCCCCCCCC", createdAt = 700),
    )

    @Test fun piuTardiHaVideoBraniELinkIncollati() {
        val later = SavedLinks.later(notes)
        assertEquals(listOf("spotify:track:x", "BBBBBBBBBBB", "AAAAAAAAAAA"), later.map { it.item.key })
        assertEquals("https://i.ytimg.com/vi/BBBBBBBBBBB/hqdefault.jpg", later[1].item.image)
    }

    @Test fun daLeggereHaGliArticoli() {
        assertEquals(listOf("Il ponte"), SavedLinks.toRead(notes).map { it.article.title })
        assertTrue(SavedLinks.livesElsewhere(notes[1]))
        assertFalse(SavedLinks.livesElsewhere(notes[6]))
    }

    @Test fun ilCalendarioTieneSoloNoteEFoto() {
        val zone = ZoneId.of("Europe/Rome")
        val groups = dueDays(emptyList(), emptyList(), emptyList(), now = LocalDateTime.of(2026, 11, 12, 9, 0), zone = zone, withToday = true, notes = notes)
        assertEquals(listOf("today", "note-7", "note-5", "note-4"), groups.first().rows.map { it.key })
        val photo = timelineEntries(groups, groups.first().day!!, zone).first { it.second.note?.id == 5L }.first
        assertEquals("/foto.jpg", photo.image)
        assertEquals("Foto · scade a marzo 2028", photo.detail)
    }

    @Test fun rigaDellaFoto() {
        assertEquals("Foto", photoLine(Note(1, "", photo = "/f.jpg")))
        assertNull(photoLine(Note(2, "Testo")))
    }

    @Test fun paroleDelTasto() {
        val a = SavedLink.Article("u", "s", "t")
        assertEquals("Leggi dopo", SavedLinks.saveLabel(a, saved = false))
        assertEquals("Togli da Più tardi", SavedLinks.saveLabel(SavedLink.Media(ResumeItem(ResumeKind.RADIO, "r", "R", at = 1)), saved = true))
    }
}
