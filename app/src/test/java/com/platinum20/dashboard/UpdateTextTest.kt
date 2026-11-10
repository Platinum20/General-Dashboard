package com.platinum20.dashboard

import com.platinum20.dashboard.update.GitHubRelease
import com.platinum20.dashboard.update.UpdateText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** La pagina Aggiornamenti dice lo stato vero e una riga per versione. */
class UpdateTextTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-10T09:00:00Z")

    private fun release(notes: String, title: String = "v1.401.0") = GitHubRelease("v1.401.0", title, notes, "2026-11-09T20:00:00Z", null, 0)

    @Test fun controlloDiOggi() {
        val at = Instant.parse("2026-11-10T01:20:00Z").toEpochMilli()
        assertEquals("Ultimo controllo alle 01:20 · controlla da sola ogni 15 min", UpdateText.checked(at, true, 15, now, zone))
    }

    @Test fun maiControllatoESpento() {
        assertEquals("Controllo automatico spento", UpdateText.checked(0, false, 15, now, zone))
    }

    @Test fun titoloDalGrassetto() {
        val r = release("**Calendario compatto**\n- Niente buchi")
        assertEquals("Calendario compatto", UpdateText.headline(r))
        assertEquals("- Niente buchi", UpdateText.body(r).trim())
    }

    @Test fun senzaGrassettoLaVersione() {
        assertEquals("Versione 1.401.0", UpdateText.headline(release("- solo righe")))
    }

    @Test fun quando() {
        assertEquals("ieri", UpdateText.whenLabel("2026-11-09T20:00:00Z", LocalDate.of(2026, 11, 10), zone))
        assertEquals("oggi", UpdateText.whenLabel("2026-11-10T08:00:00Z", LocalDate.of(2026, 11, 10), zone))
    }
}
