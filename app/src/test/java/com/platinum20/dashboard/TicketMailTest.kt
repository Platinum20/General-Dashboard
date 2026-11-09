package com.platinum20.dashboard

import com.platinum20.dashboard.mail.MimeParse
import com.platinum20.dashboard.tickets.TicketMail
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/** Dall'evento alla mail col biglietto, e dalla mail alle immagini/PDF dove sta il QR. */
class TicketMailTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val begin = LocalDate.of(2026, 12, 20).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
    private val window = "after:2026/04/24 before:2026/12/22"

    @Test fun conIlPnrBastaIlPnr() {
        val q = TicketMail.query("Viaggio Taranto-Pescara Centrale, Treno Intercity 612, Carr 8, Posti, 11D, PNR BG6GF5,", "", begin, zone)
        assertEquals("\"BG6GF5\" $window", q)
    }

    @Test fun senzaPnrNumeroDelTrenoOppureDestinazione() {
        assertEquals("612 ${TicketMail.SENDERS} $window", TicketMail.query("Viaggio Taranto-Pescara Centrale, Treno Intercity 612", "", begin, zone))
        assertEquals("\"Pescara Centrale\" ${TicketMail.SENDERS} $window", TicketMail.query("Train to Pescara Centrale", "", begin, zone))
    }

    @Test fun altriEventiSoloSeNatiDaUnaMail() {
        assertNull(TicketMail.query("Cena da Danilo", "porta il vino", begin, zone))
        val desc = "Questo evento è stato creato a partire da un'email che hai ricevuto in Gmail."
        assertEquals("\"Concerto Vasco\" $window", TicketMail.query("Concerto \"Vasco\"", desc, begin, zone))
    }

    @Test fun immaginiRemoteColCodice() {
        val html = """<img src="https://x.it/logo.png"><img alt="qr" src="https://trenitalia.it/qrcode?id=1&amp;s=2"><img src="http://a.it/qr.png">"""
        assertEquals(listOf("https://trenitalia.it/qrcode?id=1&s=2"), TicketMail.codeImageUrls(html))
    }

    @Test fun ritaglioConBordoDentroLImmagine() {
        assertArrayEquals(intArrayOf(86, 0, 210, 160), TicketMail.crop(100, 5, 200, 150, 205 + 5, 160))
    }

    @Test fun allegatiEImmaginiDellaMail() {
        val pdf = "%PDF-1.4 biglietto".toByteArray()
        val png = byteArrayOf(-119, 80, 78, 71, 1, 2, 3)
        val b64 = { b: ByteArray -> Base64.getMimeEncoder().encodeToString(b) }
        val raw = """
            |Content-Type: multipart/mixed; boundary="B1"
            |
            |--B1
            |Content-Type: multipart/related; boundary="B2"
            |
            |--B2
            |Content-Type: text/html; charset=utf-8
            |
            |<p>Il tuo biglietto</p>
            |--B2
            |Content-Type: image/png
            |Content-Transfer-Encoding: base64
            |Content-ID: <qr>
            |
            |${b64(png)}
            |--B2--
            |--B1
            |Content-Type: application/octet-stream; name="Biglietto_BG6GF5.pdf"
            |Content-Disposition: attachment; filename="Biglietto_BG6GF5.pdf"
            |Content-Transfer-Encoding: base64
            |
            |${b64(pdf)}
            |--B1--
        """.trimMargin()
        val parts = MimeParse.binaryParts(raw)
        assertEquals(listOf("image/png", "application/pdf"), parts.map { it.type })
        assertArrayEquals(png, parts[0].bytes)
        assertArrayEquals(pdf, parts[1].bytes)
        assertEquals("Biglietto_BG6GF5.pdf", parts[1].name)
        assertTrue(MimeParse.htmlBody(raw)!!.contains("Il tuo biglietto"))
    }
}
