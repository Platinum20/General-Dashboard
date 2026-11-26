package com.platinum20.dashboard

import com.platinum20.dashboard.share.ShareTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Cosa esce da Copia e Manda a…, tipo per tipo (Antonio 2026-11-26, PDF "Condividi"). */
class ShareTextsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 26)
    private fun at(d: Int, h: Int, m: Int, month: Int = 11) = LocalDateTime.of(2026, month, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun eventHasDayTimesAndPlace() {
        assertEquals(
            "Cena da Luca\nVenerdì 27 novembre, 20:30–22:30\n📍 Via Roma 3, Milano",
            ShareTexts.event("Cena da Luca", at(27, 20, 30), at(27, 22, 30), false, "Via Roma 3, Milano", zone, today),
        )
        assertEquals("Cena\nVenerdì 27 novembre, 20:30", ShareTexts.event("Cena", at(27, 20, 30), at(27, 20, 30), false, "", zone, today))
    }

    @Test
    fun allDayEventsUseTheirDaysOnly() {
        val day = 86_400_000L
        val begin = LocalDate.of(2026, 11, 27).toEpochDay() * day
        assertEquals("Compleanno\nVenerdì 27 novembre", ShareTexts.event("Compleanno", begin, begin + day, true, "", zone, today))
        assertEquals(
            "Ferie\nDa venerdì 27 novembre a domenica 29 novembre",
            ShareTexts.event("Ferie", begin, begin + 3 * day, true, "", zone, today),
        )
    }

    @Test
    fun shiftShowsFirstNamesAndNeverPay() {
        assertEquals(
            "Turno Creators Pro\nGiovedì 26 novembre, 14:00–22:00\nCon Marco e Giulia",
            ShareTexts.shift(at(26, 14, 0), at(26, 22, 0), listOf("Marco Rossi", "Giulia Bianchi"), zone, today),
        )
        assertEquals("Turno Creators Pro\nGiovedì 26 novembre, 14:00–22:00", ShareTexts.shift(at(26, 14, 0), at(26, 22, 0), emptyList(), zone, today))
    }

    @Test
    fun trainHasRouteTimesAndSeat() {
        assertEquals(
            "Treno Milano → Roma\nGiovedì 26 novembre, 8:10 → 11:20\nFrecciarossa 9512 · Carrozza 8 · Posto 11D",
            ShareTexts.train("Milano", "Roma", "Frecciarossa 9512", "8", "11D", at(26, 8, 10), at(26, 11, 20), zone, today),
        )
        assertEquals("Treno per Roma\nGiovedì 26 novembre, 8:10 → 11:20", ShareTexts.train(null, "Roma", null, null, null, at(26, 8, 10), at(26, 11, 20), zone, today))
    }

    @Test
    fun taskAddsDateAndPlaceOnlyWhenDated() {
        assertEquals(
            "Ritirare la giacca\nSabato 28 novembre, 10:00\n📍 Lavanderia",
            ShareTexts.task("Ritirare la giacca", at(28, 10, 0), false, "Lavanderia", zone, today),
        )
        assertEquals("Pane", ShareTexts.task("Pane", null, false, "", zone, today))
        assertEquals("Mutuo\nLunedì 4 gennaio 2027", ShareTexts.task("Mutuo", LocalDate.of(2027, 1, 4).atStartOfDay(zone).toInstant().toEpochMilli(), true, "", zone, today))
    }

    @Test
    fun parcelHasCodeAndLink() {
        assertEquals("Scarpe\nBRT · 0123\nhttps://x/0123", ShareTexts.parcel("Scarpe", "BRT", "0123", "https://x/0123"))
        assertEquals("Scarpe", ShareTexts.parcel("Scarpe", "", "", null))
    }

    @Test
    fun noteTurnsChecksIntoBoxes() {
        assertEquals("Spesa\n☑ Latte\n☐ Pane", ShareTexts.note("Spesa\n[x] Latte\n[ ] Pane"))
        assertEquals("Titolo\nSezione\n- punto", ShareTexts.note("Titolo\n## Sezione\n- punto"))
    }

    @Test
    fun mailPutsHeaderAboveBody() {
        assertEquals(
            "Da: Amazon\nOggetto: Conferma ordine\nMercoledì 25 novembre\n\nGrazie",
            ShareTexts.mail("Amazon", "Conferma ordine", at(25, 9, 0), "Grazie", zone, today),
        )
    }

    @Test
    fun spotifyLinksOpenOutside() {
        assertEquals("https://open.spotify.com/album/abc", ShareTexts.spotifyUrl("spotify:album:abc"))
        assertEquals("https://open.spotify.com/track/xyz", ShareTexts.spotifyUrl("spotify:track:xyz"))
        assertNull(ShareTexts.spotifyUrl("mix:daily-1"))
        assertEquals("Brano\nArtista\nhttps://l", ShareTexts.media("Brano", "Artista", "https://l"))
        assertEquals("Radio 105", ShareTexts.media("Radio 105", "", null))
    }

    @Test
    fun previewIsOneShortLine() {
        assertEquals("Cena da Luca · Venerdì 27 novembre, 20:30–22:30", ShareTexts.preview("Cena da Luca\nVenerdì 27 novembre, 20:30–22:30\n📍 Via Roma", max = 80))
        assertTrue(ShareTexts.preview("a".repeat(100)).length <= 46)
    }

    @Test
    fun icsIsAValidInvite() {
        val ics = ShareTexts.ics("u1", "Cena, amici", at(27, 20, 30), at(27, 22, 30), false, "Via Roma", 0L)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue("DTSTART:20261127T193000Z" in ics)
        assertTrue("DTEND:20261127T213000Z" in ics)
        assertTrue("SUMMARY:Cena\\, amici" in ics)
        assertTrue("LOCATION:Via Roma" in ics)
        val day = 86_400_000L
        val allDay = ShareTexts.ics("u2", "Ferie", LocalDate.of(2026, 11, 27).toEpochDay() * day, 0L, true, "", 0L)
        assertTrue("DTSTART;VALUE=DATE:20261127" in allDay)
        assertTrue("DTEND;VALUE=DATE:20261128" in allDay)
    }
}
