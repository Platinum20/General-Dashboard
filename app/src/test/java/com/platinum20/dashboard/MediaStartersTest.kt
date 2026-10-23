package com.platinum20.dashboard

import com.platinum20.dashboard.media.MediaStarters
import com.platinum20.dashboard.media.radio.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStartersTest {
    private fun station(id: String, name: String, url: String = "https://stream.example.it/$id") =
        RadioStation(id = id, name = name, streamUrl = url, logo = "", tags = "pop", country = "IT", bitrate = 128)

    private val popular = listOf(
        station("1", "RTL 102.5"),
        station("2", "Radio Deejay"),
        station("3", "rtl 102.5 "),
        station("4", "Rai Radio 1"),
        station("5", "Senza flusso", url = ""),
        station("6", "Virgin Radio"),
        station("7", "Radio 105"),
    )

    @Test
    fun alPrimoUsoLePiuAscoltateSenzaDoppioni() {
        val picked = MediaStarters.stations(popular, recent = emptyList(), favorites = emptyList())
        assertEquals(listOf("RTL 102.5", "Radio Deejay", "Rai Radio 1", "Virgin Radio"), picked.map { it.name })
    }

    @Test
    fun maiUnaGiaTraRecentiOPreferite() {
        val picked = MediaStarters.stations(
            popular,
            recent = listOf(station("99", "Radio Deejay")),
            favorites = listOf(station("4", "Rai Radio 1")),
        )
        assertEquals(listOf("RTL 102.5", "Virgin Radio", "Radio 105"), picked.map { it.name })
    }

    @Test
    fun senzaStazioniCaricateNienteSezione() {
        assertTrue(MediaStarters.stations(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
