package com.platinum20.dashboard

import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.GigaVerdict
import com.platinum20.dashboard.sim.SimReading
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.SimUsage
import com.platinum20.dashboard.ui.screens.gigaVerdictText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/** Robolectric per org.json (lo storico si salva in JSON). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SimUsageTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 12) = LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(zone).toEpochMilli()
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun unaLetturaAlGiornoLUltima() {
        var h = emptyList<SimReading>()
        h = SimUsage.record(h, SimReading(at(9, 8), 50.0), zone)
        h = SimUsage.record(h, SimReading(at(9, 20), 49.0), zone)
        h = SimUsage.record(h, SimReading(at(10, 9), 48.0), zone)
        assertEquals(listOf(SimReading(at(9, 20), 49.0), SimReading(at(10, 9), 48.0)), h)
    }

    @Test
    fun siTengonoCinqueSettimane() {
        val old = SimReading(LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(zone).toEpochMilli(), 90.0)
        val h = SimUsage.record(listOf(old), SimReading(at(10), 40.0), zone)
        assertEquals(1, h.size)
    }

    @Test
    fun ilRinnovoNonContaComeConsumo() {
        // 3 GB al giorno, poi il rinnovo li riporta a 100, poi di nuovo 3 al giorno.
        val h = listOf(SimReading(at(1), 10.0), SimReading(at(2), 7.0), SimReading(at(3), 100.0), SimReading(at(4), 97.0))
        assertEquals(3.0, SimUsage.perDay(h)!!, 1e-9)
    }

    @Test
    fun pocheLettureNonSiIndovina() {
        val s = SimStatus(data = Allowance(40.0, 200.0), renewal = LocalDate.of(2026, 11, 20))
        val h = listOf(SimReading(at(9), 41.0), SimReading(at(10), 40.0))
        assertEquals(GigaVerdict.Learning, SimUsage.verdict(s, h, today))
        assertEquals("Ti dico presto se bastano" to "Sto imparando quanto usi al giorno", gigaVerdictText(GigaVerdict.Learning))
    }

    @Test
    fun bastanoENonBastano() {
        val h = listOf(SimReading(at(6), 50.0), SimReading(at(8), 46.0), SimReading(at(10), 42.0))
        // 2 GB al giorno, 10 giorni al rinnovo: servono 20 di 42.
        val ok = SimUsage.verdict(SimStatus(data = Allowance(42.0, 200.0), renewal = LocalDate.of(2026, 11, 20)), h, today)
        assertEquals(GigaVerdict.Enough(2.0, 22.0), ok)
        assertEquals("Ti bastano fino al rinnovo" to "2,0 GB al giorno · ne avanzano 22 GB", gigaVerdictText(ok!!))
        // Con 9 GB rimasti finiscono tra 4 giorni.
        val ko = SimUsage.verdict(SimStatus(data = Allowance(9.0, 200.0), renewal = LocalDate.of(2026, 11, 20)), h, today)
        assertEquals(GigaVerdict.NotEnough(2.0, LocalDate.of(2026, 11, 14)), ko)
        assertEquals("Non ti bastano" to "2,0 GB al giorno · finiscono verso il 14/11", gigaVerdictText(ko!!))
    }

    @Test
    fun senzaDomandaQuandoNonServe() {
        val h = listOf(SimReading(at(6), 50.0), SimReading(at(10), 42.0))
        assertNull(SimUsage.verdict(SimStatus(data = Allowance(0.0, unlimited = true), renewal = LocalDate.of(2026, 11, 20)), h, today))
        assertNull(SimUsage.verdict(SimStatus(data = Allowance(42.0, 200.0)), h, today))
        assertNull(SimUsage.verdict(SimStatus(data = Allowance(42.0, 200.0), renewal = today), h, today))
    }

    @Test
    fun storicoInJson() {
        val h = listOf(SimReading(at(9), 41.5), SimReading(at(10), 40.0))
        assertEquals(h, SimUsage.fromJson(SimUsage.toJson(h)))
        assertEquals(emptyList<SimReading>(), SimUsage.fromJson(null))
    }
}
