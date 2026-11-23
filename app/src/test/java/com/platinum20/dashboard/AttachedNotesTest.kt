package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.notes.AttachedNotes
import com.platinum20.dashboard.notes.LineReminder
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotePlace
import com.platinum20.dashboard.notes.NotePlaces
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.work.MorningGroup
import com.platinum20.dashboard.work.MorningText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Gruppo B di note-integrate (Antonio, 2026-11-23): appunti su eventi e abitudini, notifiche, Ricordamelo, posti. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AttachedNotesTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun eventKeys() {
        assertEquals("ev:12", AttachedNotes.event(12))
        assertEquals("ev:-99", AttachedNotes.event(-99, 1_000L))
        val begin = LocalDate.of(2026, 11, 29).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val key = AttachedNotes.event(12, begin, recurring = true, zone = zone)
        assertEquals("ev:12@${LocalDate.of(2026, 11, 29).toEpochDay()}", key)
        assertEquals(12L, AttachedNotes.eventId(key))
        assertNull(AttachedNotes.eventId("habit:3"))
        // La volta prima dell'evento intero.
        val notes = listOf(Note(1, "a", about = "ev:12"), Note(2, "b", about = key))
        assertEquals(2L, AttachedNotes.forEvent(notes, 12, begin, zone)?.id)
        assertEquals(1L, AttachedNotes.forEvent(notes, 12, begin + 86_400_000L, zone)?.id)
    }

    @Test
    fun openLines() {
        assertEquals(listOf("Portare badge", "Chiedere a Marco"), AttachedNotes.open("Portare badge\n\nChiedere a Marco").map { it.text })
        // Con le voci da spuntare contano solo quelle aperte.
        val body = "## Prima\n[x] Badge\n[ ] Caricatore\nnota libera"
        assertEquals(listOf("Caricatore"), AttachedNotes.open(body).map { it.text })
        assertEquals("📝 Caricatore", AttachedNotes.line(body))
        assertNull(AttachedNotes.line("[x] fatto"))
        assertTrue(AttachedNotes.openChecks("Portare badge").isEmpty())
    }

    @Test
    fun tickFromNotification() {
        assertEquals("[x] Portare badge\nAltro", AttachedNotes.tick("Portare badge\nAltro", 0))
        assertEquals("[ ] a\n[x] b", AttachedNotes.tick("[ ] a\n[ ] b", 1))
        assertEquals("[x] a\n[x] b", AttachedNotes.tickAll("[ ] a\n[ ] b"))
        assertEquals("[x] a", AttachedNotes.withoutOpenChecks("[x] a\n[ ] b\n[ ] c"))
        assertEquals("2 cose aperte", AttachedNotes.leftCount(2))
        assertEquals("1 cosa aperta", AttachedNotes.leftCount(1))
    }

    @Test
    fun morningLine() {
        assertNull(AttachedNotes.morning(listOf("[x] fatto")))
        assertEquals("📝 Oggi: badge, cambio con Marco", AttachedNotes.morning(listOf("badge", "[ ] cambio con Marco")))
        assertEquals("📝 Oggi: a, b, c, …", AttachedNotes.morning(listOf("a\nb\nc\nd")))
        val groups = listOf(MorningGroup(null, listOf("Max 12°")), MorningGroup("Oggi", listOf("14:00  Turno")))
        assertEquals(listOf("14:00  Turno", "📝 Oggi: badge"), MorningText.withMemo(groups, "📝 Oggi: badge")[1].lines)
        assertEquals(groups, MorningText.withMemo(groups, null))
        assertEquals("Oggi", MorningText.withMemo(groups.take(1), "x").last().header)
    }

    @Test
    fun attachedNotesStayOffAppunti() {
        val note = Note(5, "Turno · sab 29 nov\nPortare badge", about = "ev:-7")
        assertTrue(SavedLinks.livesElsewhere(note))
        val back = NotesStore.parse(NotesStore.serialize(NotesData(notes = listOf(note))))
        assertEquals("ev:-7", back.notes.single().about)
        assertEquals("Portare badge", AttachedNotes.body(back.notes.single()))
        assertEquals("Turno\nx", AttachedNotes.text(" Turno ", "x\n"))
    }

    @Test
    fun lineReminder() {
        val morning = LocalDateTime.of(2026, 11, 23, 10, 0)
        assertEquals(LocalDateTime.of(2026, 11, 23, 19, 0), LineReminder.tonight(morning))
        assertNull(LineReminder.tonight(LocalDateTime.of(2026, 11, 23, 18, 45)))
        assertEquals(LocalDateTime.of(2026, 11, 24, 9, 0), LineReminder.tomorrowMorning(morning))
        assertEquals("Comprare lampadina E14", LineReminder.lineAt("Cambiare filtro\n[ ] Comprare lampadina E14\nAltro", 20))
        assertNull(LineReminder.lineAt("", 0))
        val link = LineReminder.link("Casa [vecchia]", 42)
        assertEquals("[Casa (vecchia)](nota:42)", link)
        assertEquals(42L, LineReminder.noteOf(link))
        assertNull(LineReminder.noteOf("note libere"))
        val today = LocalDate.of(2026, 11, 23)
        assertEquals("stasera alle 19", LineReminder.whenText(LocalDateTime.of(2026, 11, 23, 19, 0), today))
        assertEquals("domani alle 9", LineReminder.whenText(LocalDateTime.of(2026, 11, 24, 9, 0), today))
        assertEquals("domani alle 9:30", LineReminder.whenText(LocalDateTime.of(2026, 11, 24, 9, 30), today))
    }

    @Test
    fun places() {
        val list = listOf(NotePlace(3, "Coop", 45.1, 9.2))
        assertEquals(list, NotePlaces.parse(NotePlaces.serialize(list)))
        assertTrue(NotePlaces.parse("rotto").isEmpty())
        assertEquals("3 da fare: latte, pane, caffè", NotePlaces.text("[ ] Latte\n[ ] Pane\n[x] Uova\n[ ] Caffè"))
        assertNull(NotePlaces.text("[x] Latte"))
    }
}
