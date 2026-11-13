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

    @Test fun versioneCorta() {
        assertEquals("1.507", UpdateText.short("1.507.0"))
        assertEquals("1.507.1", UpdateText.short("1.507.1"))
    }

    @Test fun dataComeNelCalendario() {
        assertEquals("oggi 04:17", UpdateText.releaseWhen("2026-11-10T04:17:00Z", now, zone))
        assertEquals("ieri 23:51", UpdateText.releaseWhen("2026-11-09T23:51:00Z", now, zone))
        assertEquals("3 nov", UpdateText.releaseWhen("2026-11-03T10:00:00Z", now, zone))
    }

    @Test fun segnoInstallata() {
        val v = listOf("1.507.0", "1.503.0", "1.502.0", "1.501.0", "1.500.0")
        assertEquals(3, UpdateText.newCount(v, "1.501.0"))
        assertEquals(3, UpdateText.installedIndex(v, "1.501.0"))
        assertEquals(0, UpdateText.installedIndex(v, "1.507.0"))
        // Una build di prova non pubblicata: il segno va prima della prima più vecchia.
        assertEquals(1, UpdateText.installedIndex(v, "1.504.0"))
        assertEquals(0, UpdateText.installedIndex(v, "1.509.0"))
    }
}
