package com.platinum20.dashboard

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.widget.TasksWidget
import com.platinum20.dashboard.widget.AgendaRow
import com.platinum20.dashboard.widget.AgendaRows
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.widget.WidgetBrief
import com.platinum20.dashboard.widget.WidgetRows
import com.platinum20.dashboard.work.MorningGroup
import com.platinum20.dashboard.work.MorningText
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.ui.screens.dueDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Il widget Calendario: lo stesso Oggi del calendario dell'app (niente nascosto), poi i prossimi giorni. */
class AgendaRowsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int = 0, day: Int = 9) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(12)
    private val today = LocalDate.of(2026, 11, 9)
    private fun task(id: Long, text: String, due: Long?) = TaskItem(id, text, createdAt = 0, dueAt = due)
    private fun items(rows: List<AgendaRow>) = rows.filterIsInstance<AgendaRow.Item>()
    private fun days(
        tasks: List<TaskItem> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        notes: List<Note> = emptyList(),
    ) = dueDays(tasks, emptyList(), events, now = LocalDateTime.of(2026, 11, 9, 12, 0), zone = zone, withToday = true, notes = notes)

    @Test fun oggiComeNelCalendario() {
        val riunione = CalendarEvent(9, "Riunione", ms(10), ms(11), false, "", 0xFF3550E8.toInt())
        val turno = CalendarEvent(-3, "Turno", ms(7), ms(9), false, "", 0)
        val rows = AgendaRows.build(
            days(
                tasks = listOf(
                    task(1, "Chiamare Marco", ms(15)),
                    task(3, "Comprare il pane", null),
                    TaskItem(5, "Latte", createdAt = 0, done = true, doneAt = ms(8)),
                    TaskItem(4, "Compleanno di Marta", createdAt = 0, dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true, repeat = Repeat.YEARLY),
                ),
                events = listOf(riunione, turno),
                notes = listOf(Note(7, "Idea regalo", createdAt = 1)),
            ),
            now, zone, heightDp = 800,
        )
        assertEquals(AgendaRow.Day("Oggi"), rows.first())
        val titles = items(rows).map { it.title }
        // Il turno finito stamattina resta; le cose senza data (da fare, spuntate, note) stanno in Per dopo (2026-11-13).
        assertTrue(titles.any { it.contains("Turno") })
        assertTrue(titles.none { it == "Latte" || it == "Idea regalo" || it == "Comprare il pane" })
        assertTrue(titles.any { it.contains("Chiamare Marco") })
        assertEquals("10:00", items(rows).first { it.title.contains("Riunione") }.time)
    }

    @Test fun poiIProssimiGiorni() {
        val rows = AgendaRows.build(days(listOf(task(1, "Dentista", ms(9, day = 10)), task(2, "Meccanico", ms(8, day = 12)))), now, zone, heightDp = 600)
        assertEquals(AgendaRow.Day("Oggi"), rows[0])
        assertEquals(AgendaRow.Note("Niente in programma oggi"), rows[1])
        assertEquals(AgendaRow.Day("Domani"), rows[2])
        assertTrue(rows.contains(AgendaRow.Day("Gio 12")))
        assertEquals(AgendaRow.Note("Settimana libera"), AgendaRows.build(days(), now, zone, 300).last())
    }

    @Test fun nelloSpazioCheCe() {
        val tasks = (1..10L).map { task(it, "Cosa $it", ms(13 + (it % 8).toInt())) }
        val small = AgendaRows.build(days(tasks), now, zone, heightDp = 200)
        // 200 - 76 - 26 (Oggi) = 98dp: una riga e "+ altre 9".
        assertEquals(1, items(small).size)
        assertEquals(AgendaRow.Note("+ altre 9 cose oggi"), small.last())
    }

    @Test fun widgetOggiUsaLoStessoOggi() {
        val brief = listOf(
            BriefLine(BriefKind.RAIN, "Pioggia dalle 17"),
            // Le vecchie righe di Adesso con impegni e promemoria non entrano più: c'è Oggi del calendario.
            BriefLine(BriefKind.EVENT, "Tra 20 min: Riunione"),
        )
        val items = listOf(
            AgendaRow.Item(AgendaRow.Kind.EVENT, 1, "Riunione", "10:00"),
            AgendaRow.Item(AgendaRow.Kind.TASK, 2, "Latte", null, done = true),
            AgendaRow.Item(AgendaRow.Kind.NOTE, 3, "Idea", null),
        )
        assertEquals(
            listOf(WidgetBrief("Pioggia dalle 17"), WidgetBrief("10:00  Riunione"), WidgetBrief("✓ Latte"), WidgetBrief("Idea")),
            WidgetRows.todayEntries(brief, items, max = 5),
        )
        assertEquals(WidgetBrief("+ altre 2 cose oggi"), WidgetRows.todayEntries(brief, items, max = 3).last())
        assertEquals(
            listOf(MorningGroup("Oggi", listOf("10:00  Riunione"))),
            MorningText.groups(null, null, brief.drop(1), emptyList(), 0L, calendar = listOf("10:00  Riunione")),
        )
    }
}

/** La spunta dal widget usa lo stesso NotesStore di Note. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TasksWidgetActionTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        NotesStore.init(context)
    }

    private fun tap(id: Long) = TasksWidget().onReceive(
        context,
        Intent(context, TasksWidget::class.java).setAction(TasksWidget.ACTION_DONE).putExtra(TasksWidget.EXTRA_TASK, id),
    )

    @Test fun ilCerchioSegnaFattoIlPromemoriaGiusto() {
        val now = System.currentTimeMillis()
        val a = NotesStore.addTaskAt("Pagare la luce", now - 60_000)
        val b = NotesStore.addTaskAt("Chiamare Marco", now + 60_000)
        tap(a.id)
        assertTrue(NotesStore.task(a.id)!!.done)
        assertFalse(NotesStore.task(b.id)!!.done)
        assertTrue(NotesStore.data.value.openTasks.none { it.id == a.id })
    }

    @Test fun unRicorrentePassaAllaVoltaDopo() {
        val now = System.currentTimeMillis()
        val t = NotesStore.addTaskAt("Medicina", now - 3_600_000)
        NotesStore.setTaskRepeat(t.id, Repeat.DAILY)
        tap(t.id)
        val after = NotesStore.task(t.id)!!
        assertFalse(after.done)
        assertTrue(after.dueAt!! > now)
    }

    @Test fun senzaIdNonSuccedeNulla() {
        val before = NotesStore.data.value
        TasksWidget().onReceive(context, Intent(context, TasksWidget::class.java).setAction(TasksWidget.ACTION_DONE))
        assertEquals(before, NotesStore.data.value)
    }
}
