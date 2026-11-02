package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.weather.AirQuality
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.work.MorningGroup
import com.platinum20.dashboard.work.MorningText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MorningTextTest {
    private val day = DailyPoint(LocalDate.of(2026, 10, 27), 0, 18.0, 12.0, 10, 0.0, null, null, 10.0, 3.0)
    private val endOfDay = 1_000_000L

    @Test
    fun gruppiStaccatiSenzaNotizie() {
        val groups = MorningText.groups(
            today = day,
            air = AirQuality(30, 5.0, 5.0, 5.0),
            adesso = listOf(
                BriefLine(BriefKind.EVENT, "Alle 15:00: Dentista"),
                BriefLine(BriefKind.RAIN, "Pioggia dalle 16:00"),
                BriefLine(BriefKind.TASK, "Scaduto: Bolletta"),
            ),
            tasks = listOf(
                TaskItem(1, "Bolletta", dueAt = endOfDay - 10),
                TaskItem(2, "Sessi"),
                TaskItem(3, "Prova", dueAt = endOfDay + 10),
            ),
            endOfDay = endOfDay,
        )
        assertEquals(
            listOf(
                // Aria discreta: non vale una riga. La pioggia è meteo.
                MorningGroup(null, listOf("Max 18° · min 12°", "Pioggia dalle 16:00")),
                MorningGroup("Oggi", listOf("Alle 15:00: Dentista", "Scaduto: Bolletta")),
                // Il promemoria di oggi è già in "Oggi".
                MorningGroup("Da fare", listOf("Sessi", "Prova")),
            ),
            groups,
        )
    }

    @Test
    fun ariaCattivaEPioggiaProbabile() {
        val groups = MorningText.groups(
            today = day.copy(precipitationProbability = 60), air = AirQuality(65, 5.0, 5.0, 5.0),
            adesso = emptyList(), tasks = emptyList(), endOfDay = endOfDay,
        )
        assertEquals(listOf(MorningGroup(null, listOf("Max 18° · min 12° · pioggia 60%", "Aria scadente"))), groups)
    }

    @Test
    fun troppeCoseDaFare() {
        val tasks = (1..6L).map { TaskItem(it, "Cosa $it") }
        val todo = MorningText.groups(null, null, emptyList(), tasks, endOfDay).single()
        assertEquals("Da fare", todo.header)
        assertEquals(listOf("Cosa 1", "Cosa 2", "Cosa 3", "…e altre 3"), todo.lines)
        assertEquals("…e un'altra", MorningText.more(4))
    }
}
