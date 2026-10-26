package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.autofill.Extractor
import com.platinum20.dashboard.autofill.Found
import com.platinum20.dashboard.autofill.Incoming
import com.platinum20.dashboard.autofill.SourceKind
import com.platinum20.dashboard.mail.MailMessage
import com.platinum20.dashboard.mail.MailParse
import com.platinum20.dashboard.mail.MailStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MailParseTest {
    // Giovedì 22 ottobre 2026, a metà mattina.
    private val now = LocalDateTime.of(2026, 10, 22, 10, 30)
    private val nowMs = now.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    private fun gmail(from: String, subject: String, parts: String, labels: String = "\"INBOX\",\"UNREAD\",\"IMPORTANT\"") = JSONObject(
        """{"id":"m1","snippet":"Ciao &amp; grazie","internalDate":"$nowMs","labelIds":[$labels],
           "payload":{"mimeType":"multipart/alternative","headers":[{"name":"From","value":${JSONObject.quote(from)}},{"name":"Subject","value":${JSONObject.quote(subject)}}],
           "parts":[$parts]}}"""
    )

    private fun part(mime: String, text: String) = """{"mimeType":"$mime","filename":"","body":{"data":"${b64(text)}"}}"""

    @Test
    fun testoSempliceEMittente() {
        val m = MailParse.message(
            gmail("\"Amazon.it\" <conferma-spedizione@amazon.it>", "Il tuo pacco è stato spedito", part("text/html", "<b>no</b>") + "," + part("text/plain", "Ciao Antonio,\n\n\n\nSpedito: Cuffie Sony"))
        )
        assertEquals("Amazon.it", m.from)
        assertEquals("conferma-spedizione@amazon.it", m.fromAddress)
        assertEquals("Il tuo pacco è stato spedito", m.subject)
        assertEquals("Ciao Antonio,\n\nSpedito: Cuffie Sony", m.body)
        assertEquals("Ciao & grazie", m.snippet)
        assertTrue(m.unread)
        assertTrue(MailParse.worthScanning(m))
    }

    @Test
    fun soloHtml() {
        val html = "<html><head><style>p{color:red}</style></head><body><p>Importo&nbsp;85,20&euro;</p><p>Scade il 15/11/2026</p><!-- x --></body></html>"
        val m = MailParse.message(gmail("bollette@enel.it", "Bolletta", part("text/html", html)))
        assertEquals("Importo 85,20€\nScade il 15/11/2026", m.body)
        assertEquals("bollette", m.from)
    }

    @Test
    fun rispostaCitataTagliata() {
        val body = MailParse.tidyBody("Va bene, a domani\n> vecchio testo\nIl giorno 20 ott 2026, Mario <m@x.it> ha scritto:\nmessaggio vecchio")
        assertEquals("Va bene, a domani", body)
    }

    @Test
    fun promozioniNonSiLeggono() {
        val m = MailParse.message(gmail("Shop <a@b.it>", "Sconti", part("text/plain", "Scade domani!"), "\"CATEGORY_PROMOTIONS\",\"INBOX\""))
        assertFalse(MailParse.worthScanning(m))
    }

    @Test
    fun mailAmazonDiventaPacco() {
        val m = MailMessage("m1", "Amazon.it", "", "Il tuo pacco è stato spedito", "", nowMs, emptySet(),
            "Ciao Antonio,\nSpedito: Cuffie Sony\nConsegna prevista: domani")
        val found = Extractor.extract(MailParse.incoming(m, ZoneOffset.UTC))
        val p = found.single() as Found.NewParcel
        assertEquals("Cuffie Sony", p.title)
        assertEquals(LocalDate.of(2026, 10, 23), p.expected)
    }

    @Test
    fun rinnovoAbbonamento() {
        val d = Extractor.extract(
            Incoming("Spotify", SourceKind.MAIL, "Spotify Premium", "Spotify Premium\nIl tuo abbonamento si rinnoverà il 3 novembre.", now)
        ).single() as Found.Deadline
        assertEquals("Rinnovo: Spotify Premium", d.title)
        assertEquals(LocalDate.of(2026, 11, 3), d.due.toLocalDate())
    }

    @Test
    fun rinnovoNonEUnaSpesa() {
        val found = Extractor.extract(Incoming("Netflix", SourceKind.OTHER, "Netflix", "Il tuo abbonamento si rinnoverà il 3/11, addebito di 12,99 €", now))
        assertTrue(found.toString(), found.none { it is Found.Spent })
    }

    @Test
    fun fileDellaPosta() {
        val list = listOf(MailMessage("a", "Banca", "x@banca.it", "Avviso", "breve", 5L, setOf("UNREAD", "IMPORTANT"), "testo\n\nfine"))
        assertEquals(list, MailStore.parse(MailStore.serialize(list)))
    }

    @Test
    fun query() {
        assertTrue(MailParse.scanQuery(1_700_000_000_000L).startsWith("after:1700000000 "))
        assertEquals(listOf("a", "b"), MailParse.ids(JSONObject("""{"messages":[{"id":"a"},{"id":"b"}]}""")))
        assertEquals(emptyList<String>(), MailParse.ids(JSONObject("{}")))
    }
}
