package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.Alert
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.WhenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Promemoria e date da ricordare sono una cosa sola (1.361), con le stesse righe degli eventi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UnifiedDatesTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 9)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test fun ilCompleannoPassatoVaAllAnnoDopoDaSolo() {
        val marta = TaskItem.date(1, "Compleanno di Marta", LocalDate.of(1996, 3, 12), yearly = true, zone = zone)
        assertEquals(LocalDate.of(2027, 3, 12), marta.nextDay(today, zone))
        assertEquals(31, marta.yearsAtNext(today, zone))
        assertEquals("Compleanno di Marta (31 anni)", marta.titleWithYears(today, zone))
        // Oggi resta oggi.
        val oggi = TaskItem.date(2, "Anniversario", today.withYear(2020), yearly = true, zone = zone)
        assertEquals(today, oggi.nextDay(today, zone))
    }

    @Test fun siSpuntaSoloQuelloSenzaData() {
        val passata = TaskItem.date(1, "Viaggio", today.minusDays(3), zone = zone)
        assertFalse(passata.tickable)
        assertEquals(today.minusDays(3), passata.nextDay(today, zone))
        assertFalse(TaskItem(2, "Bolletta", dueAt = ms(today.atTime(9, 0))).tickable)
        assertTrue(TaskItem(3, "Latte").tickable)
    }

    @Test fun unPromemoriaCheSiRipetePassaDaSoloAllaVoltaDopo() {
        val affitto = TaskItem(1, "Affitto", dueAt = ms(LocalDate.of(2026, 10, 31).atTime(9, 0)), repeat = Repeat.MONTHLY)
        assertEquals(LocalDate.of(2026, 11, 30).atTime(9, 0), affitto.nextAt(today, zone))
        val palestra = TaskItem(2, "Palestra", dueAt = ms(today.minusDays(7).atTime(18, 0)), repeat = Repeat.WEEKLY)
        assertEquals(today.atTime(18, 0), palestra.nextAt(today, zone))
    }

    @Test fun ilPassatoNonVaFraGliScaduti() {
        val now = today.atTime(12, 0)
        val groups = com.platinum20.dashboard.ui.screens.dueDays(
            listOf(TaskItem(1, "Bolletta", dueAt = ms(today.minusDays(1).atTime(9, 0)))), emptyList(), emptyList(), now = now, zone = zone,
        )
        assertTrue(groups.none { it.late })
        val past = com.platinum20.dashboard.ui.screens.pastDays(
            listOf(TaskItem(1, "Bolletta", dueAt = ms(today.minusDays(1).atTime(9, 0)))), emptyList(), emptyList(), now = now, zone = zone, withToday = true,
        )
        assertEquals(listOf("task-1"), past.flatMap { it.rows }.map { it.key })
    }

    @Test fun avvisoUgualePerTutti() {
        val bolletta = TaskItem(1, "Bolletta", dueAt = ms(today.atTime(18, 30)))
        assertEquals(ms(today.atTime(18, 30)), bolletta.alertAt(today, zone))
        assertEquals(ms(today.minusDays(1).atTime(18, 30)), bolletta.copy(alert = Alert.DAY_BEFORE).alertAt(today, zone))
        assertNull(bolletta.copy(alert = Alert.NONE).alertAt(today, zone))
        assertNull(bolletta.copy(done = true).alertAt(today, zone))
        // Tutto il giorno: alle 9, della prossima volta.
        val marta = TaskItem.date(2, "Compleanno di Marta", LocalDate.of(1996, 3, 12), yearly = true, zone = zone)
        assertEquals(ms(LocalDate.of(2027, 3, 12).atTime(9, 0)), marta.alertAt(today, zone))
        assertEquals(ms(LocalDate.of(2027, 3, 11).atTime(9, 0)), marta.copy(alert = Alert.DAY_BEFORE).alertAt(today, zone))
        assertNull(TaskItem(3, "Latte").alertAt(today, zone))
    }

    @Test fun leDatePassateSiSpostanoAllaVoltaDopo() {
        val marta = TaskItem.date(1, "Compleanno di Marta", LocalDate.of(2026, 3, 12), yearly = true, zone = zone)
        val viaggio = TaskItem.date(2, "Viaggio", today.minusDays(3), zone = zone)
        val bolletta = TaskItem(3, "Bolletta", dueAt = ms(today.minusDays(5).atTime(9, 0)))
        val d = NotesStore.withDatesRolled(NotesData(tasks = listOf(marta, viaggio, bolletta)), today, zone)
        assertEquals(LocalDate.of(2027, 3, 12), d.tasks[0].day(zone))
        assertEquals(2026, d.tasks[0].firstYear)
        // Non si ripete: resta nel suo giorno, come un evento passato.
        assertEquals(viaggio, d.tasks[1])
        assertEquals(bolletta, d.tasks[2])
        assertEquals(d, NotesStore.withDatesRolled(d, today, zone))
    }

    @Test fun leVecchieDateDiventanoPromemoriaDiTuttoIlGiorno() {
        val raw = """{"tasks":[{"id":1,"text":"Latte","createdAt":5}],
            "countdowns":[{"id":2,"title":"Compleanno di Marco","date":"1990-03-14","yearly":true},{"id":3,"title":"Viaggio","date":"2026-12-01","yearly":false}]}"""
        val d = NotesStore.parse(raw)
        assertEquals(listOf(1L, 2L, 3L), d.tasks.map { it.id })
        val marco = d.tasks[1]
        assertTrue(marco.allDay)
        assertEquals(Repeat.YEARLY, marco.repeat)
        assertEquals(1990, marco.firstYear)
        assertEquals("Viaggio", d.tasks[2].text)
        assertNull(d.tasks[2].repeat)
        // Le cose da fare dei widget non contano compleanni e date.
        assertEquals(listOf(1L), d.openTasks.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), d.openItems.map { it.id })
        // Salvate di nuovo, non si raddoppiano.
        assertEquals(d, NotesStore.parse(NotesStore.serialize(d)))
    }

    @Test fun leParoleDiEventiEPromemoriaSonoLeStesse() {
        assertEquals("Si ripete ogni anno", WhenText.repeat(Repeat.YEARLY))
        assertEquals(WhenText.repeat(Repeat.WEEKLY), WhenText.repeatOfRule("FREQ=WEEKLY;BYDAY=MO"))
        assertEquals("Si ripete", WhenText.repeatOfRule("FREQ=WEEKLY;INTERVAL=2"))
        assertNull(WhenText.repeatOfRule(""))
        assertEquals("Nessun avviso", WhenText.alert(emptyList()))
        assertEquals("Avviso all'ora", WhenText.alert(listOf(0)))
        assertEquals(WhenText.alert(TaskItem(1, "x", dueAt = 1)), WhenText.alert(listOf(0)))
        assertEquals("Avviso 10 minuti prima e 1 ora prima", WhenText.alert(listOf(60, 10)))
        assertEquals("Avviso il giorno prima", WhenText.alert(listOf(1440)))
    }
}
