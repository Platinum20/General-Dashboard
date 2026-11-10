package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Alert
import com.platinum20.dashboard.notes.ReminderText
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.weather.RainNotice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReminderTextTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 10)
    private val nine = today.atTime(9, 0).toInstant(zone).toEpochMilli()

    @Test fun ilTitoloEDiceLaCosa() {
        val task = TaskItem(1, "Chiamare il commercialista", dueAt = nine)
        assertEquals("Chiamare il commercialista", ReminderText.title(task))
        assertEquals("Adesso", ReminderText.detail(task, today, zone))
    }

    @Test fun ilGiornoPrimaDiceDomaniEOra() {
        val task = TaskItem(1, "Dentista", dueAt = nine, alert = Alert.DAY_BEFORE, place = "Via Roma 3")
        assertEquals("Domani alle 09:00 · Via Roma 3", ReminderText.detail(task, today, zone))
    }

    @Test fun unaDataTuttoIlGiornoDiceOggi() {
        val task = TaskItem(1, "Scade la carta d'identità", dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true)
        assertEquals("Oggi", ReminderText.detail(task, today, zone))
    }

    @Test fun laPioggiaSenzaEmoji() {
        val n = RainNotice(10, 25, "debole", false)
        assertEquals("Pioggia tra 10 minuti", n.title())
        assertEquals("Debole per circa 25 minuti · Roma", n.detail("Roma"))
    }
}
