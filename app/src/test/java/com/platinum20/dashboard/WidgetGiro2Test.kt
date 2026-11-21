package com.platinum20.dashboard

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.LooseItem
import com.platinum20.dashboard.ui.screens.LooseKind
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.widget.AgendaRows
import com.platinum20.dashboard.widget.TodoRow
import com.platinum20.dashboard.widget.TodoWidget
import com.platinum20.dashboard.widget.TodoWidgetRows
import com.platinum20.dashboard.widget.WidgetRows
import com.platinum20.dashboard.widget.WidgetSize
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

/** Widget, giro 2 (2026-11-21): Da fare nuovo, Oggi come Adesso, Calendario col titolo delle card. */
class WidgetGiro2Test {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 21)
    private val evening = LocalDateTime.of(2026, 11, 21, 18, 0)
    private fun ms(h: Int, day: Int = 21) = LocalDateTime.of(2026, 11, day, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun todo(id: Long, text: String, line: String? = null): LooseItem {
        val task = TaskItem(id, text, createdAt = 0)
        return LooseItem("task-$id", LooseKind.TODO, text, line, row = DueRow("task-$id", today, Long.MAX_VALUE, task = task))
    }

    @Test fun abitudiniInCimaPoiLeCoseDaFare() {
        val water = Habit(1, "Bere 2 litri d'acqua")
        val walk = Habit(2, "Camminare 30 minuti", done = setOf(today, today.minusDays(1)))
        val m = TodoWidgetRows.build(listOf(water, walk), listOf(todo(10, "Comprare il latte"), todo(11, "Dentista", "Chiedere per giovedì")), evening, heightDp = 400)
        assertEquals(listOf("Bere 2 litri d'acqua", "Camminare 30 minuti", "Comprare il latte", "Dentista"), m.rows.map { it.title })
        // Dalle 17 l'abitudine non fatta dice "Manca oggi" in arancione, come nella card.
        assertEquals("Manca oggi", m.rows[0].line)
        assertTrue(m.rows[0].urgent)
        assertFalse(m.rows[0].done)
        // Quella fatta resta, spenta fino a domani, con la sua serie.
        assertTrue(m.rows[1].done)
        assertEquals("Fatta oggi · torna domani", m.rows[1].line)
        assertEquals(2, m.rows[1].flame)
        assertEquals(TodoRow.Kind.TASK, m.rows[3].kind)
        assertEquals("Chiedere per giovedì", m.rows[3].line)
        // Il conto come la card: le cose aperte, l'abitudine fatta non conta.
        assertEquals(3, m.count)
        assertEquals(0, m.more)
    }

    @Test fun nelloSpazioCheCeLUltimaRigaDiceQuanteRestano() {
        val todos = (1..8L).map { todo(it, "Cosa $it") }
        val m = TodoWidgetRows.build(emptyList(), todos, evening, heightDp = 250)
        // 250 - 64 = 186dp: 4 righe intere non bastano per 8, quindi 3 righe e "+ altre 5".
        assertEquals(3, m.rows.size)
        assertEquals(5, m.more)
        assertEquals("+ altre 5", TodoWidgetRows.moreText(m.more))
        assertEquals("+ un'altra", TodoWidgetRows.moreText(1))
        assertEquals(0, TodoWidgetRows.build(emptyList(), emptyList(), evening, 250).rows.size)
    }

    @Test fun laProssimaCosaDelWidgetOggi() {
        val now = ms(12)
        val past = TaskItem(1, "Commercialista", createdAt = 0, dueAt = ms(9))
        val later = TaskItem(2, "Dentista", createdAt = 0, dueAt = ms(15))
        val tomorrow = TaskItem(3, "Turno", createdAt = 0, dueAt = ms(7, day = 22))
        fun days(vararg t: TaskItem) = dueDays(t.toList(), emptyList(), emptyList(), now = LocalDateTime.of(2026, 11, 21, 12, 0), zone = zone, withToday = true)
        // Oggi: la prima con un orario non ancora passata.
        assertEquals("15:00 · Dentista", AgendaRows.next(days(past, later, tomorrow), now, zone))
        // Finito oggi: la prima di domani, col giorno davanti.
        assertEquals("Domani 07:00 · Turno", AgendaRows.next(days(past, tomorrow), now, zone))
        assertNull(AgendaRows.next(days(past), now, zone))
    }

    @Test fun titoliEMisure() {
        assertEquals("SABATO 21 · ROMA", WidgetRows.todayLabel(today, "Roma"))
        assertEquals("SABATO 21", WidgetRows.todayLabel(today, null))
        // Basso: niente ore, solo la prossima cosa; alto: le ore secondo la larghezza.
        assertEquals(WidgetRows.TodayParts(0, true), WidgetRows.todayParts(WidgetSize(250, 130)))
        assertEquals(WidgetRows.TodayParts(6, true), WidgetRows.todayParts(WidgetSize(320, 200)))
        assertEquals(WidgetRows.TodayParts(0, false), WidgetRows.todayParts(WidgetSize(250, 110)))
    }

    @Test fun ilContoDelCalendarioComeLaCard() {
        val now = LocalDateTime.of(2026, 11, 21, 12, 0)
        val dated = listOf(
            TaskItem(1, "Dentista", createdAt = 0, dueAt = ms(15)),
            TaskItem(2, "Affitto", createdAt = 0, dueAt = ms(9, day = 28)),
            // Dicembre non conta, e le cose senza data stanno in Da fare.
            TaskItem(3, "Regali", createdAt = 0, dueAt = LocalDateTime.of(2026, 12, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()),
            TaskItem(4, "Latte", createdAt = 0),
        )
        val days = dueDays(dated, emptyList(), emptyList(), now = now, zone = zone, withToday = true)
        assertEquals(2, AgendaRows.monthCount(days, today))
        assertNull(AgendaRows.monthCount(dueDays(emptyList(), emptyList(), emptyList(), now = now, zone = zone, withToday = true), today))
    }
}

/** Il cerchio del widget Da fare usa lo stesso NotesStore della card. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TodoWidgetTickTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        NotesStore.init(context)
    }

    private fun tap(kind: TodoRow.Kind, id: Long) = TodoWidget().onReceive(
        context,
        Intent(context, TodoWidget::class.java).setAction(TodoWidget.ACTION_TICK)
            .putExtra(TodoWidget.EXTRA_ID, id).putExtra(TodoWidget.EXTRA_KIND, kind.name),
    )

    @Test fun spuntaLaCosaGiustaELAbitudine() {
        val a = NotesStore.addTask("Comprare il latte")!!
        val b = NotesStore.addTask("Chiamare il dentista")!!
        tap(TodoRow.Kind.TASK, a.id)
        assertTrue(NotesStore.task(a.id)!!.done)
        assertFalse(NotesStore.task(b.id)!!.done)
        // Un secondo tocco (il widget non l'ha ancora tolta) non la riapre.
        tap(TodoRow.Kind.TASK, a.id)
        assertTrue(NotesStore.task(a.id)!!.done)

        val h = NotesStore.saveHabit(null, "Bere acqua")!!
        tap(TodoRow.Kind.HABIT, h)
        assertTrue(NotesStore.data.value.habits.first { it.id == h }.isDone(LocalDate.now()))
        // Sull'abitudine il cerchio va e viene, come nella card.
        tap(TodoRow.Kind.HABIT, h)
        assertFalse(NotesStore.data.value.habits.first { it.id == h }.isDone(LocalDate.now()))
    }
}
