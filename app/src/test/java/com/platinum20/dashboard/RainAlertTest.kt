package com.platinum20.dashboard

import com.platinum20.dashboard.weather.RainAlert
import com.platinum20.dashboard.weather.RainNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RainAlertTest {
    private val now = LocalDateTime.of(2026, 10, 22, 17, 40)

    /** Nowcast a 15 minuti che parte dal quarto d'ora in corso (17:30). */
    private fun cast(vararg mm: Double) = mm.mapIndexed { i, v -> LocalDateTime.of(2026, 10, 22, 17, 30).plusMinutes(15L * i) to v }

    @Test
    fun pioggiaTraVentiMinutiAvvisaConMinutiEDurata() {
        val n = RainAlert.decide(cast(0.0, 0.0, 0.4, 0.5, 0.2, 0.0, 0.0), now, null)!!
        assertEquals(20, n.inMinutes)
        assertEquals(45, n.durationMinutes)
        assertEquals("debole", n.intensity)
        assertEquals("Pioggia tra 20 minuti a Roma, debole per circa 45 minuti", n.text("Roma"))
    }

    @Test
    fun giaPioveNienteAvviso() {
        assertNull(RainAlert.decide(cast(0.3, 0.5, 0.6, 0.0), now, null))
    }

    @Test
    fun avvisoRecenteNienteAvviso() {
        val data = cast(0.0, 0.0, 0.4, 0.0)
        assertNull(RainAlert.decide(data, now, now.minusHours(1)))
        assertNotNull(RainAlert.decide(data, now, now.minusHours(3)))
    }

    @Test
    fun sottoSogliaNienteAvviso() {
        assertNull(RainAlert.decide(cast(0.0, 0.1, 0.2, 0.1, 0.0), now, null))
    }

    @Test
    fun oltreQuarantacinqueMinutiNienteAvviso() {
        // 17:30, 17:45, 18:00, 18:15, 18:30 (50 minuti): troppo lontano.
        assertNull(RainAlert.decide(cast(0.0, 0.0, 0.0, 0.0, 0.8), now, null))
    }

    @Test
    fun fuoriOrarioNienteAvviso() {
        val night = LocalDateTime.of(2026, 10, 22, 23, 5)
        val data = (0..4).map { night.withMinute(0).plusMinutes(15L * it) to if (it == 2) 0.5 else 0.0 }
        assertNull(RainAlert.decide(data, night, null))
        val early = LocalDateTime.of(2026, 10, 22, 6, 40)
        val dataEarly = (0..4).map { early.withMinute(30).plusMinutes(15L * it) to if (it == 2) 0.5 else 0.0 }
        assertNull(RainAlert.decide(dataEarly, early, null))
    }

    @Test
    fun intensitaEDurataLunga() {
        val n = RainAlert.decide(cast(0.0, 2.5, 3.0, 3.0, 3.0, 3.0, 3.0), now, null)!!
        assertEquals("forte", n.intensity)
        assertEquals(true, n.longer)
        assertEquals("Pioggia tra pochi minuti a Roma, forte per più di un'ora", n.text("Roma"))
    }

    @Test
    fun testoConOre() {
        assertEquals(
            "Pioggia tra 30 minuti a Milano, moderata per circa un'ora e mezza",
            RainNotice(30, 90, "moderata", false).text("Milano"),
        )
    }
}
