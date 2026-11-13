package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.LooseKind
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.looseItems
import com.platinum20.dashboard.ui.screens.looseCount
import com.platinum20.dashboard.ui.screens.archivedItems
import com.platinum20.dashboard.ui.screens.calendarMonthCount
import com.platinum20.dashboard.ui.screens.pending
import com.platinum20.dashboard.ui.screens.LooseItem
import com.platinum20.dashboard.ui.screens.withoutLoose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 2026-11-12 (Antonio): tutte le cose senza scadenza in fondo alla card Calendario, fuori dai giorni. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LooseItemsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 11, 12, 10, 0)
    private val today = now.toLocalDate()

    @Test fun leCoseSenzaDataEsconoDaiGiorni() {
        val groups = listOf(
            DueDay(today, false, listOf(
                DueRow("today", today, Long.MIN_VALUE, today = true),
                DueRow("task-1", today, Long.MAX_VALUE, task = TaskItem(1, "Comprare il latte")),
                DueRow("task-2", today, 5L, task = TaskItem(2, "Dentista", dueAt = 5L)),
                DueRow("note-3", today, Long.MAX_VALUE, note = Note(3, "Idea")),
                DueRow("parcel-4", today, Long.MAX_VALUE, parcel = Parcel(4, "Scarpe")),
                DueRow("parcel-5", today, 6L, parcel = Parcel(5, "Libro", expected = today)),
            )),
        )
        assertEquals(listOf("today", "task-2", "parcel-5"), groups.withoutLoose().single().rows.map { it.key })
    }

    @Test fun tuttoInsiemeNellOrdineGiusto() {
        val video = SavedLinks.noteText(SavedLink.Media(ResumeItem(ResumeKind.YOUTUBE, "AAAAAAAAAAA", "Motore a razzo", "Veritasium", "img", at = 1)))
        val article = SavedLinks.noteText(SavedLink.Article("https://ilpost.it/a", "Il Post", "Il ponte"))
        val notes = listOf(
            Note(10, video, createdAt = 300),
            Note(11, article, createdAt = 200),
            Note(12, "Idea regalo per Marta", createdAt = 100),
            Note(13, "Scontrino\nscade a marzo", createdAt = 400, photos = listOf("/f.jpg")),
            Note(14, "Codice del cancello", createdAt = 50, pinned = true),
        )
        val tasks = listOf(
            TaskItem(1, "Comprare il latte", createdAt = 1),
            TaskItem(2, "Chiamare Luca", createdAt = 2, done = true, doneAt = now.atZone(zone).toInstant().toEpochMilli() - 1000),
        )
        val groups = dueDays(tasks, emptyList(), emptyList(), now = now, zone = zone, withToday = true, notes = notes)
        val items = looseItems(groups, notes, today)
        assertEquals(
            // La nota fissata va in cima a tutto, sopra anche le cose da fare (2026-11-13).
            listOf("note-14", "task-1", "note-13", "note-12", "task-2"),
            items.map { it.key },
        )
        assertEquals(LooseKind.PHOTO, items[2].kind)
        assertEquals("Foto · scade a marzo", items[2].line)
        // Video e articoli salvati non sono in Per dopo: stanno in Media e News.
        assertTrue(items.none { it.key == "note-10" || it.key == "note-11" })
        assertTrue(items.last().done)
        // Ogni riga ha al massimo una riga sotto.
        assertTrue(items.all { it.line == null || '\n' !in it.line!! })
        assertEquals("5", looseCount(items.size))
    }

    @Test fun leCoseFatteVannoInArchivio() {
        val tasks = listOf(
            TaskItem(1, "Latte", createdAt = 1),
            TaskItem(2, "Chiamare Luca", createdAt = 2, done = true, doneAt = 100),
            TaskItem(3, "Banca", createdAt = 3, done = true, doneAt = 200),
            TaskItem(4, "Dentista", createdAt = 4, dueAt = 5L, done = true, doneAt = 300),
        )
        // Solo le cose senza data spuntate, dalla più recente; quelle con data passano da sole.
        assertEquals(listOf("task-3", "task-2"), archivedItems(tasks).map { it.key })
        val card = listOf(LooseItem("a", LooseKind.TODO, "A"), LooseItem("b", LooseKind.TODO, "B", done = true))
        assertEquals(listOf("a"), card.pending().map { it.key })
    }

    @Test fun tuttoSiFissaInAlto() {
        val tasks = listOf(
            TaskItem(1, "Comprare il latte", createdAt = 1),
            TaskItem(2, "Rinnovare la carta", createdAt = 2, pinned = true),
            TaskItem(3, "Banca", createdAt = 3, done = true, doneAt = 200),
            TaskItem(4, "Posta", createdAt = 4, done = true, doneAt = 100, pinned = true),
        )
        val parcels = listOf(Parcel(5, "Cuffie"), Parcel(6, "Libro", pinned = true))
        val notes = listOf(Note(7, "Idea", createdAt = 10))
        val groups = dueDays(tasks.take(2), parcels, emptyList(), now = now, zone = zone, withToday = true, notes = notes)
        val items = looseItems(groups, notes, today)
        assertEquals(listOf("task-2", "parcel-6", "task-1", "parcel-5", "note-7"), items.map { it.key })
        assertTrue(items[0].pinned && items[1].pinned && !items[2].pinned)
        // Anche nell'archivio la fissata sta in cima, anche se spuntata prima.
        assertEquals(listOf("task-4", "task-3"), archivedItems(tasks).map { it.key })
        // E resta fissata dopo il salvataggio.
        val data = com.platinum20.dashboard.notes.NotesStore.parse(
            com.platinum20.dashboard.notes.NotesStore.serialize(com.platinum20.dashboard.notes.NotesData(tasks = tasks, parcels = parcels)),
        )
        assertEquals(listOf(false, true, false, true), data.tasks.map { it.pinned })
        assertEquals(listOf(false, true), data.parcels.map { it.pinned })
    }

    @Test fun ilNumeroDelCalendarioContaFinoAFineMese() {
        val rows = listOf(
            DueRow("today", today, Long.MIN_VALUE, today = true),
            DueRow("a", today, 1L),
            DueRow("b", today.plusDays(1), 2L),
            DueRow("c", today.withDayOfMonth(30), 3L),
            DueRow("d", today.plusMonths(1), 4L),
            DueRow("e", today.minusDays(1), 5L),
        )
        assertEquals(3, calendarMonthCount(rows, today))
    }
}
