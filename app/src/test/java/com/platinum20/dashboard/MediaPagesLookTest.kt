package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyArtist
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
import com.platinum20.dashboard.ui.screens.HistoryDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Le pagine di Spotify e YouTube come il resto dell'app (1.563): giorni, anni, di chi è la playlist. */
class MediaPagesLookTest {
    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun cronologiaPerGiorno() {
        assertEquals("Oggi", HistoryDays.label(today, today))
        assertEquals("Ieri", HistoryDays.label(today.minusDays(1), today))
        assertEquals("Mer 18 nov", HistoryDays.label(LocalDate.of(2026, 11, 18), today))
        assertEquals("Mer 31 dic 2025", HistoryDays.label(LocalDate.of(2025, 12, 31), today))
        val ms = { d: LocalDate, h: Int -> d.atStartOfDay().plusHours(h.toLong()).toInstant(ZoneOffset.UTC).toEpochMilli() }
        val items = listOf(ms(today.minusDays(1), 9), ms(today, 8), ms(today, 20))
        val groups = HistoryDays.group(items, { it }, ZoneOffset.UTC, today)
        assertEquals(listOf("Oggi", "Ieri"), groups.map { it.first })
        assertEquals(listOf(ms(today, 20), ms(today, 8)), groups.first().second)
    }

    @Test
    fun discografiaPerAnno() {
        val rows = listOf("a" to 2021, "b" to null, "c" to 2024, "d" to 2021)
        val years = SpotifyArtist.byYear(rows) { it.second }
        assertEquals(listOf("2024", "2021", "Senza anno"), years.map { it.first })
        assertEquals(listOf("a", "d"), years[1].second.map { it.first })
    }

    @Test
    fun diChiELaPlaylist() {
        assertEquals("Tua · privata", SpotifyPlaylists.ownerLine(true, false, false, "x"))
        assertEquals("Tua · condivisa", SpotifyPlaylists.ownerLine(true, true, true, "x"))
        assertEquals("Condivisa", SpotifyPlaylists.ownerLine(false, true, true, "x"))
        assertEquals("Madame", SpotifyPlaylists.ownerLine(false, true, false, "Madame"))
    }
}
