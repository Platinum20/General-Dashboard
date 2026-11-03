package com.platinum20.dashboard

import com.platinum20.dashboard.autofill.Extractor
import com.platinum20.dashboard.autofill.Found
import com.platinum20.dashboard.mail.ImapLine
import com.platinum20.dashboard.mail.ImapParse
import com.platinum20.dashboard.mail.MailParse
import com.platinum20.dashboard.mail.MimeParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Base64

class GmailImapTest {
    private fun latin(s: String) = String(s.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    private val ricevuta = latin(
        "From: =?UTF-8?Q?Trenitalia?= <noreply@trenitalia.it>\r\n" +
            "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString("Ricevuta di pagamento è qui".toByteArray()) + "?=\r\n" +
            "Content-Type: multipart/mixed; boundary=\"ext\"\r\n\r\n" +
            "--ext\r\nContent-Type: multipart/alternative;\r\n boundary=int\r\n\r\n" +
            "--int\r\nContent-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n" +
            "<p>Totale pagato: 23,90 =E2=82=AC</p>=\r\n<p>Grazie</p>\r\n" +
            "--int\r\nContent-Type: text/plain; charset=\"utf-8\"\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
            Base64.getMimeEncoder().encodeToString("Ricevuta di pagamento\nTotale pagato: 23,90 €\nGrazie, città".toByteArray()) + "\r\n" +
            "--int--\r\n" +
            "--ext\r\nContent-Type: text/plain; name=\"biglietto.txt\"\r\nContent-Disposition: attachment; filename=\"biglietto.txt\"\r\n\r\nALLEGATO\r\n" +
            "--ext--\r\n"
    )

    @Test fun intestazioniDecodificate() {
        val h = MimeParse.headers(ricevuta)
        assertEquals("Ricevuta di pagamento è qui", h["subject"])
        assertEquals("Trenitalia <noreply@trenitalia.it>", h["from"])
        assertEquals("multipart/mixed; boundary=\"ext\"", h["content-type"])
    }

    @Test fun testoSempliceAnnidatoSenzaAllegati() {
        val body = MimeParse.bodyText(ricevuta)
        assertTrue(body, body.startsWith("Ricevuta di pagamento"))
        assertTrue("23,90 €" in body)
        assertTrue("città" in body)
        assertFalse("ALLEGATO" in body)
    }

    @Test fun soloHtmlQuotedPrintable() {
        val raw = "Subject: x\r\nContent-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n" +
            "<div>Totale =E2=82=AC 9,99</div><style>p{}</style>"
        assertEquals("Totale € 9,99", MimeParse.bodyText(raw).trim())
    }

    @Test fun mailTagliataSiLeggeLoStesso() {
        val full = Base64.getEncoder().encodeToString("Il tuo ordine è stato spedito, codice RR123456789IT".toByteArray())
        val raw = "Content-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + full.dropLast(3)
        assertTrue(MimeParse.bodyText(raw).startsWith("Il tuo ordine è stato"))
    }

    @Test fun parolePiuCodificateSiUniscono() {
        assertEquals("Ciao mondo", MimeParse.decodeWords("=?utf-8?q?Ciao_?= =?utf-8?q?mondo?="))
        assertEquals("Già pagato", MimeParse.decodeWords(latin("Già pagato")))
    }

    @Test fun rispostaFetch() {
        val line = ImapLine(
            "* 12 FETCH (UID 345 X-GM-MSGID 1784021234567890123 X-GM-LABELS (\"\\\\Important\" \"\\\\Inbox\") FLAGS () " +
                "INTERNALDATE \" 3-Nov-2026 09:12:00 +0000\" BODY[]<0> ${ImapLine.mark(0)})",
            listOf(ricevuta),
        )
        val f = ImapParse.fetched(listOf(line)).single()
        assertEquals(345L, f.uid)
        // Lo stesso id dell'API Gmail: le mail già lette non si rileggono cambiando strada.
        assertEquals(java.lang.Long.toHexString(1784021234567890123L), f.gmailId)
        assertEquals(setOf("UNREAD", "IMPORTANT", "INBOX"), f.labels)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 9, 12).toInstant(ZoneOffset.UTC).toEpochMilli(), f.at)
        val m = ImapParse.message(f)
        assertNotNull(m)
        assertEquals("Trenitalia", m!!.from)
        assertEquals("noreply@trenitalia.it", m.fromAddress)
        assertTrue(m.unread)
        // E la ricevuta diventa una spesa.
        val spent = Extractor.extract(MailParse.incoming(m, ZoneOffset.UTC)).filterIsInstance<Found.Spent>()
        assertEquals(listOf(2390L), spent.map { it.cents })
    }

    @Test fun letteNonSonoUnread() {
        assertEquals(setOf("SENT"), ImapParse.labels("\\Seen \\Flagged", "\"\\\\Sent\""))
    }

    @Test fun cartellaTuttiIMessaggi() {
        val lines = listOf(
            ImapLine("* LIST (\\HasNoChildren) \"/\" \"INBOX\"", emptyList()),
            ImapLine("* LIST (\\All \\HasNoChildren) \"/\" \"[Gmail]/Tutti i messaggi\"", emptyList()),
        )
        assertEquals("\"[Gmail]/Tutti i messaggi\"", ImapParse.allMailbox(lines))
        assertEquals("INBOX", ImapParse.allMailbox(lines.take(1)))
    }

    @Test fun ricercaEVirgolette() {
        assertEquals(listOf(4L, 9L, 12L), ImapParse.search(listOf(ImapLine("* SEARCH 4 9 12", emptyList()))))
        assertEquals("\"a\\\"b\\\\c\"", ImapParse.quote("a\"b\\c"))
        assertTrue(ImapParse.authFailed("NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)"))
    }
}
