package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.calendar.CalendarDays
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.ComingKind
import com.platinum20.dashboard.today.WeekReview
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** "La tua settimana", passo 2: impegni della settimana nuova e ricorrenti spuntati. */
@RunWith(AndroidJUnit4::class)
class WeekReviewEventsTest {
    private val zone = ZoneId.of("Europe/Rome")
    // Lunedì 19 ottobre 2026 - domenica 25; la settimana nuova va dal 26 al 1° novembre.
    private val monday = LocalDate.of(2026, 10, 19)
    private val nextMonday = monday.plusWeeks(1)

    private fun ms(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun event(id: Long, title: String, day: LocalDate, hour: Int, minute: Int = 0, hours: Long = 1) =
        CalendarEvent(id, title, ms(day, hour, minute), ms(day, hour, minute) + hours * 3_600_000, false, "", 0)

    private fun allDay(id: Long, title: String, first: LocalDate, days: Long) = CalendarEvent(
        id, title,
        first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        first.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        true, "", 0,
    )

    private fun build(tasks: List<TaskItem> = emptyList(), events: List<CalendarEvent> = emptyList(), countdowns: List<Countdown> = emptyList()) =
        WeekReview.build(
            monday, tasks, emptyList(), emptyList(), emptyList(), emptyList(), countdowns, zone,
            events = CalendarDays.group(events, nextMonday, 7, zone),
        )

    @Test
    fun impegniDellaSettimanaNuovaInCosaArriva() {
        val r = build(
            events = listOf(
                event(1, "Dentista", nextMonday.plusDays(1), 9, 30),
                event(2, "Cena con Sara", nextMonday.plusDays(3), 20),
            ),
            countdowns = listOf(Countdown(1, "Compleanno Marco", LocalDate.of(1990, 10, 28), yearly = true)),
        )
        assertEquals(
            listOf("Mar 27 9:30 · Dentista", "Mer 28 · Compleanno Marco (36 anni)", "Gio 29 20:00 · Cena con Sara"),
            r.coming.map { it.text },
        )
        assertEquals(listOf(ComingKind.EVENT, ComingKind.DATE, ComingKind.EVENT), r.coming.map { it.kind })
    }

    @Test
    fun nelloStessoGiornoPrimaGliImpegni() {
        val r = build(
            events = listOf(event(1, "Riunione", nextMonday.plusDays(2), 11)),
            countdowns = listOf(Countdown(1, "Compleanno Marco", LocalDate.of(1990, 10, 28), yearly = true)),
        )
        assertEquals(listOf(ComingKind.EVENT, ComingKind.DATE), r.coming.map { it.kind })
    }

    @Test
    fun unEventoDiPiuGiorniCompareUnaVolta() {
        val r = build(events = listOf(allDay(1, "Ferie a Napoli", nextMonday.plusDays(3), 3)))
        assertEquals(listOf("Gio 29 · Ferie a Napoli"), r.coming.map { it.text })
    }

    @Test
    fun alMassimoSeiImpegni() {
        val events = (0L..9L).map { event(it, "Impegno $it", nextMonday.plusDays(it % 7), 8 + it.toInt()) }
        val lines = build(events = events).coming
        assertEquals(WeekReview.MAX_EVENTS, lines.size)
        // I primi in ordine di giorno: lunedì con gli impegni 0 e 7, poi martedì con 1 e 8.
        assertEquals("Lun 26 8:00 · Impegno 0", lines.first().text)
    }

    @Test
    fun impegniFuoriDallaSettimanaNuovaNonContano() {
        val events = mapOf(
            monday.plusDays(6) to listOf(event(1, "Domenica scorsa", monday.plusDays(6), 10)),
            nextMonday.plusWeeks(1) to listOf(event(2, "Fra due settimane", nextMonday.plusWeeks(1), 10)),
        )
        val r = WeekReview.build(monday, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zone, events = events)
        assertEquals(emptyList<String>(), r.coming.map { it.text })
    }

    @Test
    fun ricorrentiSpuntatiContanoNellaSettimana() {
        val gym = TaskItem(
            1, "Buttare la plastica", dueAt = ms(nextMonday, 20), repeat = Repeat.WEEKLY,
            doneTimes = listOf(ms(monday.minusDays(3), 21), ms(monday.plusDays(1), 21), ms(monday.plusDays(4), 8)),
        )
        val chiuso = TaskItem(2, "Pagare bollo", done = true, doneAt = ms(monday.plusDays(2), 12))
        val r = build(tasks = listOf(gym, chiuso))
        assertEquals(3, r.tasksDone)
    }

    @Test
    fun leSpuntePiuVecchieSiScartano() {
        var t = TaskItem(1, "Acqua alle piante", repeat = Repeat.DAILY)
        (1..TaskItem.MAX_DONE_TIMES + 5).forEach { t = t.withDoneTime(it.toLong()) }
        assertEquals(TaskItem.MAX_DONE_TIMES, t.doneTimes.size)
        assertEquals(6L, t.doneTimes.first())
    }

    @Test
    fun spuntareUnRicorrenteLasciaLaTraccia() {
        val task = NotesStore.addTaskAt("Buttare la carta", System.currentTimeMillis() - 3_600_000)
        NotesStore.setTaskRepeat(task.id, Repeat.WEEKLY)
        val before = System.currentTimeMillis()
        NotesStore.toggleTask(task.id)
        val after = NotesStore.data.value.tasks.first { it.id == task.id }
        NotesStore.deleteTask(task.id)
        // Non si chiude: passa alla settimana dopo, ma la spunta resta per "La tua settimana".
        assertEquals(false, after.done)
        assertEquals(1, after.doneTimes.size)
        assertEquals(true, after.doneTimes[0] >= before)
        assertEquals(true, after.dueAt!! > task.dueAt!!)
    }

    @Test
    fun leSpunteSiSalvano() {
        val data = NotesData(tasks = listOf(TaskItem(1, "Plastica", repeat = Repeat.WEEKLY, doneTimes = listOf(1_000L, 2_000L))))
        assertEquals(data, NotesStore.parse(NotesStore.serialize(data)))
        // I file vecchi senza "doneTimes" si leggono uguali.
        assertEquals(emptyList<Long>(), NotesStore.parse(NotesStore.serialize(NotesData(tasks = listOf(TaskItem(2, "x"))))).tasks[0].doneTimes)
    }
}
