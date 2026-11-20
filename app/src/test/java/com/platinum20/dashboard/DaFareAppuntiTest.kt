package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.CaptureDestination
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.archiveGroups
import com.platinum20.dashboard.ui.screens.archivedItems
import com.platinum20.dashboard.ui.screens.loosePageLine
import com.platinum20.dashboard.ui.screens.archivedNotes
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.jottings
import com.platinum20.dashboard.ui.screens.looseItems
import com.platinum20.dashboard.ui.screens.todos
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 1.532 (Antonio, 2026-11-20): due card, "Da fare" da spuntare e "Appunti", ognuna col suo archivio; il tipo nel "+" si corregge. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DaFareAppuntiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 11, 20, 15, 0)

    @Test fun dueCardSeparate() {
        val tasks = listOf(TaskItem(1, "Chiamare il commercialista", createdAt = 1))
        val notes = listOf(Note(2, "Idea regalo", createdAt = 10), Note(3, "Scontrino", createdAt = 20, photos = listOf("/f.jpg")))
        val items = looseItems(dueDays(tasks, emptyList(), emptyList(), now = now, zone = zone, withToday = true, notes = notes), notes, now.toLocalDate())
        assertEquals(listOf("task-1"), items.todos().map { it.key })
        assertEquals(listOf("note-3", "note-2"), items.jottings().map { it.key })
    }

    @Test fun gliAppuntiArchiviatiEsconoDallaCard() {
        val notes = listOf(Note(2, "Idea regalo", createdAt = 10), Note(3, "Vecchia", createdAt = 5, archivedAt = 100), Note(4, "Più vecchia", createdAt = 1, archivedAt = 50))
        val items = looseItems(dueDays(emptyList(), emptyList(), emptyList(), now = now, zone = zone, withToday = true, notes = notes), notes, now.toLocalDate())
        assertEquals(listOf("note-2"), items.jottings().map { it.key })
        // Nell'archivio, dall'ultima archiviata.
        assertEquals(listOf("note-3", "note-4"), archivedNotes(notes).map { it.key })
        // E restano archiviate dopo il salvataggio.
        val back = NotesStore.parse(NotesStore.serialize(NotesData(notes = notes)))
        assertEquals(listOf(null, 100L, 50L), back.notes.map { it.archivedAt })
    }

    /** 1.539: la pagina intera mette le vecchie sopra ADESSO, la più vecchia in cima, per giorno o per mese. */
    @Test fun laPaginaInteraDivideIlPassato() {
        val today = now.toLocalDate()
        fun at(daysAgo: Long) = now.minusDays(daysAgo).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            TaskItem(1, "Ieri", createdAt = 1, done = true, doneAt = at(1)),
            TaskItem(2, "Lunedì", createdAt = 1, done = true, doneAt = at(4)),
            TaskItem(3, "Ottobre", createdAt = 1, done = true, doneAt = at(48)),
            TaskItem(4, "Ancora da fare", createdAt = 1),
        )
        val days = archiveGroups(archivedItems(tasks), byMonth = false, today = today, zone = zone)
        assertEquals(listOf("3 ott", "Lun 16", "Ieri"), days.map { it.label })
        val notes = listOf(Note(5, "Vecchia", createdAt = 1, archivedAt = at(48)), Note(6, "Nuova", createdAt = 1, archivedAt = at(2)))
        val months = archiveGroups(archivedNotes(notes), byMonth = true, today = today, zone = zone)
        assertEquals(listOf("Ottobre", "Novembre"), months.map { it.label })
        assertEquals("4 da fare · 12 fatte", loosePageLine(true, 4, 12))
        assertEquals("1 appunto", loosePageLine(false, 1, 0))
        assertEquals("Niente da fare", loosePageLine(true, 0, 0))
    }

    @Test fun laChipAccesaDaSola() {
        assertEquals(CaptureDestination.TODO, CaptureDestination.of(CaptureMode.TASK, "comprare il latte", now))
        assertEquals(CaptureDestination.CALENDAR, CaptureDestination.of(CaptureMode.TASK, "domani alle 9 chiamare Luca", now))
        assertEquals(CaptureDestination.JOTTINGS, CaptureDestination.of(CaptureMode.NOTE, "idea", now))
        assertEquals(CaptureDestination.CALENDAR, CaptureDestination.of(CaptureMode.PARCEL, "scarpe", now))
        assertEquals(CaptureDestination.HABITS, CaptureDestination.of(CaptureMode.HABIT, "ogni giorno leggere", now))
    }

    @Test fun sceltaAManoVince() {
        assertEquals(CaptureMode.NOTE, CaptureDestination.modeFor(CaptureDestination.JOTTINGS, CaptureMode.TASK, "prova", now))
        assertEquals(CaptureMode.TASK, CaptureDestination.modeFor(CaptureDestination.TODO, CaptureMode.NOTE, "prova", now))
        // Già nel Calendario: resta il tipo capito; da una nota diventa un evento (se manca il quando, lo chiede).
        assertEquals(CaptureMode.PARCEL, CaptureDestination.modeFor(CaptureDestination.CALENDAR, CaptureMode.PARCEL, "scarpe", now))
        assertEquals(CaptureMode.EVENT, CaptureDestination.modeFor(CaptureDestination.CALENDAR, CaptureMode.NOTE, "prova", now))
        // "Da fare" su un testo con la data: si salva senza data.
        assertTrue(CaptureDestination.plainTodo(CaptureDestination.TODO, CaptureMode.TASK, "domani alle 9 chiamare Luca", now))
        assertFalse(CaptureDestination.plainTodo(CaptureDestination.TODO, CaptureMode.TASK, "comprare il latte", now))
        assertFalse(CaptureDestination.plainTodo(null, CaptureMode.TASK, "domani alle 9 chiamare Luca", now))
    }
}
