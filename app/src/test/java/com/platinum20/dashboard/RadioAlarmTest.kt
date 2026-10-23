package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.radio.AlarmSettings
import com.platinum20.dashboard.media.radio.RadioAlarm
import com.platinum20.dashboard.media.radio.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** Sveglia con la radio: quando suona, il volume che sale, le impostazioni salvate. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RadioAlarmTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int, m: Int = 0, month: Int = 10) = ZonedDateTime.of(2026, month, d, h, m, 0, 0, rome)

    @Test fun oggiSeNonEAncoraPassata() {
        // Venerdì 23 ottobre 2026 alle 6: la sveglia delle 7 suona oggi.
        assertEquals(at(23, 7), RadioAlarm.next(at(23, 6), 7, 0, weekdaysOnly = true))
    }

    @Test fun venerdiSeraConLunVenSuonaLunedi() {
        assertEquals(at(26, 7, 30), RadioAlarm.next(at(23, 22), 7, 30, weekdaysOnly = true))
        // Tutti i giorni: sabato.
        assertEquals(at(24, 7, 30), RadioAlarm.next(at(23, 22), 7, 30, weekdaysOnly = false))
    }

    @Test fun allOraEsattaPassaAlGiornoDopo() {
        assertEquals(at(27, 7), RadioAlarm.next(at(26, 7), 7, 0, weekdaysOnly = true))
    }

    @Test fun cambioDellOraLegaleTieneLOraDiSveglia() {
        // Domenica 25 ottobre 2026 si torna all'ora solare: lunedì alle 7 sono sempre le 7 di Roma.
        val next = RadioAlarm.next(at(24, 23), 7, 0, weekdaysOnly = true)
        assertEquals(at(26, 7), next)
        assertEquals(7, next.hour)
        assertEquals("+01:00", next.offset.id)
    }

    @Test fun volumeCheSale() {
        assertEquals(0.1f, RadioAlarm.rampVolume(0), 0.001f)
        assertEquals(0.55f, RadioAlarm.rampVolume(15_000), 0.001f)
        assertEquals(1f, RadioAlarm.rampVolume(30_000), 0.001f)
        assertEquals(1f, RadioAlarm.rampVolume(90_000), 0.001f)
    }

    @Test fun impostazioniSalvateERilette() {
        val station = RadioStation("abc", "Radio Deejay", "https://example.org/deejay", "", "pop", "Italy", 128)
        val settings = AlarmSettings(enabled = true, hour = 6, minute = 45, weekdaysOnly = false, station = station)
        assertEquals(settings, RadioAlarm.fromJson(RadioAlarm.toJson(settings)))
        assertEquals("6:45", settings.timeLabel)
        assertEquals("tutti i giorni", settings.daysLabel)
        // Impostazioni vecchie o rovinate: valori di partenza sensati.
        val empty = RadioAlarm.fromJson(org.json.JSONObject("{\"hour\": 99}"))
        assertEquals(23, empty.hour)
        assertTrue(empty.weekdaysOnly)
    }
}
