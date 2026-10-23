package com.platinum20.dashboard

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.widget.TasksWidget
import com.platinum20.dashboard.widget.TasksWidgetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** Voce 19: il widget "Da fare", righe scelte e spunta dalla schermata Home. */
class TasksWidgetRowsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int = 0, day: Int = 23) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(12)
    private fun task(id: Long, text: String, due: Long?) = TaskItem(id, text, createdAt = 0, dueAt = due)

    @Test fun scadutiPrimaPoiOggiPoiSenzaScadenza() {
        val c = TasksWidgetRows.build(
            listOf(
                task(1, "Spesa", null),
                task(2, "Palestra", ms(18, 30)),
                task(3, "Bollette", ms(9)),
                task(4, "Dentista", ms(9, day = 24)),
                task(5, "Passaporto", ms(10, day = 19)),
            ),
            now, zone, maxRows = 6,
        )
        assertEquals(listOf(5L, 3L, 2L, 1L), c.rows.map { it.id })
        assertEquals(listOf("lun 19 ott", "09:00", "18:30", null), c.rows.map { it.time })
        assertEquals(listOf(true, true, false, false), c.rows.map { it.late })
        assertEquals(0, c.more)
        assertNull(c.empty)
    }

    @Test fun ieriSiChiamaIeri() {
        val c = TasksWidgetRows.build(listOf(task(1, "Bollette", ms(20, day = 22))), now, zone, 3)
        assertEquals("ieri", c.rows.single().time)
    }

    @Test fun quelliCheNonStannoDiventanoAltri() {
        val tasks = (1L..5L).map { task(it, "Cosa $it", ms(13 + it.toInt())) }
        val c = TasksWidgetRows.build(tasks, now, zone, maxRows = 2)
        assertEquals(listOf(1L, 2L), c.rows.map { it.id })
        assertEquals(3, c.more)
        assertEquals("+ altri 3 in Note", TasksWidgetRows.moreText(c.more))
        assertEquals("+ un altro in Note", TasksWidgetRows.moreText(1))
        assertNull(TasksWidgetRows.moreText(0))
    }

    @Test fun maiPiuDelleRigheDelLayout() {
        val tasks = (1L..10L).map { task(it, "Cosa $it", null) }
        assertEquals(TasksWidgetRows.MAX_ROWS, TasksWidgetRows.build(tasks, now, zone, maxRows = 20).rows.size)
        assertEquals(1, TasksWidgetRows.build(tasks, now, zone, maxRows = 0).rows.size)
    }

    @Test fun vuotoConIlProssimoPromemoria() {
        assertEquals("Niente da fare oggi", TasksWidgetRows.build(emptyList(), now, zone, 4).empty)
        val tomorrow = TasksWidgetRows.build(listOf(task(1, "Dentista", ms(9, day = 24)), task(2, "Meccanico", ms(8, day = 27))), now, zone, 4)
        assertTrue(tomorrow.rows.isEmpty())
        assertEquals("Niente da fare oggi · prossimo: Dentista, domani alle 09:00", tomorrow.empty)
        val later = TasksWidgetRows.build(listOf(task(2, "Meccanico", ms(8, day = 27))), now, zone, 4)
        assertEquals("Niente da fare oggi · prossimo: Meccanico, mar 27 ott alle 08:00", later.empty)
    }

    @Test fun righeSecondoLAltezza() {
        assertEquals(1, TasksWidgetRows.rowsForHeight(110))
        assertEquals(3, TasksWidgetRows.rowsForHeight(180))
        assertEquals(TasksWidgetRows.MAX_ROWS, TasksWidgetRows.rowsForHeight(600))
        assertEquals(1, TasksWidgetRows.rowsForHeight(0))
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
