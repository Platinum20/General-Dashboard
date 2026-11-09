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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Il widget Calendario: oggi per primo (scaduti, eventi, promemoria, date, senza data), poi i prossimi giorni. */
class AgendaRowsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int = 0, day: Int = 9) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(12)
    private val today = LocalDate.of(2026, 11, 9)
    private fun task(id: Long, text: String, due: Long?) = TaskItem(id, text, createdAt = 0, dueAt = due)
    private fun items(rows: List<AgendaRow>) = rows.filterIsInstance<AgendaRow.Item>()

    @Test fun oggiInOrdine() {
        val event = CalendarEvent(9, "Riunione", ms(10), ms(11), false, "", 0xFF3550E8.toInt())
        val rows = AgendaRows.build(
            listOf(
                task(1, "Chiamare Marco", ms(15)),
                task(2, "Bollette", ms(9, day = 7)),
                task(3, "Comprare il pane", null),
                TaskItem(4, "Compleanno di Marta", createdAt = 0, dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true, repeat = Repeat.YEARLY),
            ),
            mapOf(today to listOf(event)),
            now, zone, heightDp = 600,
        )
        assertEquals(listOf("Bollette", "Compleanno di Marta", "Riunione", "Chiamare Marco", "Comprare il pane"), items(rows).map { it.title })
        val late = items(rows).first()
        assertTrue(late.late)
        assertEquals("sab 7 nov", late.time)
        assertEquals(AgendaRow.Kind.DATE, items(rows)[1].kind)
        assertEquals("10:00", items(rows)[2].time)
    }

    @Test fun poiIProssimiGiorni() {
        val rows = AgendaRows.build(
            listOf(task(1, "Dentista", ms(9, day = 10)), task(2, "Meccanico", ms(8, day = 12))),
            emptyMap(), now, zone, heightDp = 600,
        )
        assertEquals(AgendaRow.Note("Niente in programma oggi"), rows.first())
        assertEquals(AgendaRow.Day("DOMANI"), rows[1])
        assertTrue(rows.contains(AgendaRow.Day("GIO 12 NOV")))
        assertEquals(AgendaRow.Note("Settimana libera"), AgendaRows.build(emptyList(), emptyMap(), now, zone, 300).single())
    }

    @Test fun nelloSpazioCheCe() {
        val tasks = (1..10L).map { task(it, "Cosa $it", ms(13 + (it % 8).toInt())) }
        val small = AgendaRows.build(tasks, emptyMap(), now, zone, heightDp = 180)
        // 180 - 76 = 104dp: due righe e "+ altre".
        assertEquals(2, items(small).size)
        assertEquals(AgendaRow.Note("+ altre 8 cose oggi"), small.last())
        // Un compito ricorrente spuntato prima non entra: il widget usa openItems.
        assertTrue(AgendaRows.build(tasks.take(1), emptyMap(), now, zone, heightDp = 110).isNotEmpty())
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
