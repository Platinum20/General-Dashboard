package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.TodayTaskPicker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodayTasksTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val morning = LocalDateTime.of(2026, 10, 22, 10, 0)
    private val evening = LocalDateTime.of(2026, 10, 22, 19, 0)

    private fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun task(id: Long, due: LocalDateTime? = null) = TaskItem(id, "p$id", dueAt = due?.let(::at))

    private val overdue = task(1, morning.minusDays(1))
    private val today = task(2, morning.plusHours(3))
    private val tomorrow = task(3, morning.plusDays(1))
    private val nextWeek = task(4, morning.plusDays(7))
    private val undated = (10L..14L).map { task(it) }

    @Test fun mattinaScadutiEOggiPoiDueSenzaScadenza() {
        val pick = TodayTaskPicker.pick(listOf(today, overdue, tomorrow, nextWeek) + undated, morning, zone)
        assertEquals(listOf(1L, 2L, 10L, 11L), pick.shown.map { it.id })
        assertEquals(5, pick.more)
    }

    @Test fun dalleDiciottoAncheDomani() {
        val pick = TodayTaskPicker.pick(listOf(overdue, today, tomorrow, nextWeek), evening, zone)
        assertEquals(listOf(1L, 2L, 3L), pick.shown.map { it.id })
        assertEquals(1, pick.more)
    }

    @Test fun maiPiuDiCinqueRighe() {
        val many = (20L..29L).map { task(it, morning.plusMinutes(it)) } + undated
        val pick = TodayTaskPicker.pick(many, morning, zone)
        assertEquals(5, pick.shown.size)
        assertEquals((20L..24L).toList(), pick.shown.map { it.id })
        assertEquals(10, pick.more)
    }

    @Test fun soloScadenzeLontane() {
        val pick = TodayTaskPicker.pick(listOf(nextWeek), morning, zone)
        assertEquals(emptyList<TaskItem>(), pick.shown)
        assertEquals(1, pick.more)
    }
}
