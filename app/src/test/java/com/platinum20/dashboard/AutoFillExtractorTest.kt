package com.platinum20.dashboard

import com.platinum20.dashboard.autofill.Extractor
import com.platinum20.dashboard.autofill.Found
import com.platinum20.dashboard.autofill.Incoming
import com.platinum20.dashboard.autofill.SourceKind
import com.platinum20.dashboard.notes.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AutoFillExtractorTest {
    // Giovedì 22 ottobre 2026, a metà mattina.
    private val now = LocalDateTime.of(2026, 10, 22, 10, 30)

    private fun find(app: String, kind: SourceKind, title: String, text: String) =
        Extractor.extract(Incoming(app, kind, title, text, now))

    private fun spent(app: String, kind: SourceKind, title: String, text: String): Found.Spent {
        val found = find(app, kind, title, text)
        assertEquals("$title / $text -> $found", 1, found.size)
        return found.single() as Found.Spent
    }

    private fun nothing(app: String, kind: SourceKind, title: String, text: String) {
        val found = find(app, kind, title, text)
        assertTrue("$title / $text -> $found", found.isEmpty())
    }

    // --- Spese ---

    @Test fun revolutItaliano() {
        val e = spent("Revolut", SourceKind.PAYMENT, "Revolut", "Hai pagato 12,50 € a Lidl")
        assertEquals(1250, e.cents)
        assertEquals("Lidl", e.merchant)
        assertEquals(ExpenseCategory.FOOD, e.category)
        assertEquals(LocalDate.of(2026, 10, 22), e.date)
    }

    @Test fun revolutInglese() {
        val e = spent("Revolut", SourceKind.PAYMENT, "Revolut", "Paid €4.80 at Starbucks")
        assertEquals(480, e.cents)
        assertEquals("Starbucks", e.merchant)
    }

    @Test fun esercenteNelTitolo() {
        val e = spent("Revolut", SourceKind.PAYMENT, "Esselunga", "Pagamento di €63,20")
        assertEquals(6320, e.cents)
        assertEquals("Esselunga", e.merchant)
        assertEquals(ExpenseCategory.FOOD, e.category)
    }

    @Test fun smsBancaMaiuscolo() {
        val e = spent(
            "SMS", SourceKind.SMS, "IntesaSP",
            "Pagamento con carta *1234 di EUR 1.234,56 presso MEDIAWORLD SPA il 22/10/26 ore 10:12",
        )
        assertEquals(123456, e.cents)
        assertEquals("Mediaworld", e.merchant)
    }

    @Test fun satispay() {
        val e = spent("Satispay", SourceKind.PAYMENT, "Satispay", "Hai pagato 1,20 € da Bar Centrale")
        assertEquals(120, e.cents)
        assertEquals("Bar Centrale", e.merchant)
        assertEquals(ExpenseCategory.EATING_OUT, e.category)
    }

    @Test fun ricevutaMail() {
        val e = spent("Gmail", SourceKind.MAIL, "Netflix", "La tua ricevuta\nAbbonamento mensile: 13,99 €")
        assertEquals(1399, e.cents)
        assertEquals("Netflix", e.merchant)
    }

    @Test fun soldiInEntrataNo() = nothing("Revolut", SourceKind.PAYMENT, "Revolut", "Hai ricevuto 20,00 € da Mario Rossi")
    @Test fun rimborsoNo() = nothing("Revolut", SourceKind.PAYMENT, "Revolut", "Rimborso di 9,99 € da Amazon")
    @Test fun rifiutatoNo() = nothing("Revolut", SourceKind.PAYMENT, "Revolut", "Pagamento di 50 € rifiutato: fondi insufficienti")
    @Test fun daAutorizzareNo() = nothing("Intesa", SourceKind.PAYMENT, "Intesa Sanpaolo", "Autorizza il pagamento di 30,00 € su Zalando")
    @Test fun codiceNo() = nothing("SMS", SourceKind.SMS, "Poste", "Il codice OTP per il pagamento di 12,00 € è 123456")
    @Test fun chatIgnorate() = nothing("WhatsApp", SourceKind.CHAT, "Luca", "Ho pagato 30€ la pizza, mi dai 15 €?")
    @Test fun newsletterSenzaRicevutaNo() = nothing("Gmail", SourceKind.MAIL, "Zalando", "Saldi! Tutto a 19,99 €")

    // --- Pacchi ---

    @Test fun pacoPosteConData() {
        val p = find("Poste Italiane", SourceKind.SHOP, "La tua spedizione", "La spedizione RR123456789IT è in arrivo. Consegna prevista il 24 ottobre")
            .single() as Found.NewParcel
        assertEquals("RR123456789IT", p.code)
        assertEquals("Pacco Poste", p.title)
        assertEquals(LocalDate.of(2026, 10, 24), p.expected)
    }

    @Test fun codiceDopoEtichetta() {
        val p = find("SMS", SourceKind.SMS, "BRT", "BRT: il tuo pacco con numero di spedizione 084512345678 sarà consegnato domani")
            .single() as Found.NewParcel
        assertEquals("084512345678", p.code)
        assertEquals(LocalDate.of(2026, 10, 23), p.expected)
    }

    @Test fun amazonSenzaCodice() {
        val p = find("Amazon Shopping", SourceKind.SHOP, "Amazon", "Spedito: Cuffie Bluetooth Sony WH-1000XM5")
            .single() as Found.NewParcel
        assertEquals("Cuffie Bluetooth Sony WH-1000XM5", p.title)
        assertEquals("", p.code)
    }

    @Test fun consegnato() {
        val d = find("Gmail", SourceKind.MAIL, "Amazon.it", "Il pacco TBA123456789012 è stato consegnato").single()
        assertEquals(Found.Delivered("TBA123456789012"), d)
    }

    @Test fun codiceSenzaContestoNo() = nothing("Gmail", SourceKind.MAIL, "Ufficio", "Riferimento pratica RR123456789IT allegato")

    // --- Scadenze ---

    @Test fun bolletta() {
        val d = find("Gmail", SourceKind.MAIL, "Enel Energia", "Nuova bolletta disponibile\nImporto 85,20 €, scade il 15/11/2026")
            .single() as Found.Deadline
        assertEquals("Scadenza: Nuova bolletta disponibile", d.title)
        assertEquals(LocalDate.of(2026, 11, 15), d.due.toLocalDate())
    }

    @Test fun appuntamento() {
        val d = find("SMS", SourceKind.SMS, "ASL", "Promemoria: visita cardiologica il 3 novembre alle 9:30, presentarsi 15 minuti prima")
            .single() as Found.Deadline
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 30), d.due)
    }

    @Test fun dataPassataNo() = nothing("Gmail", SourceKind.MAIL, "Trenitalia", "Il tuo biglietto per il 10/10 è stato utilizzato")

    @Test fun bollettaNonEUnaSpesa() {
        val found = find("Gmail", SourceKind.MAIL, "A2A", "Pagamento bolletta di 40,00 € da pagare entro il 30/10")
        assertTrue(found.toString(), found.single() is Found.Deadline)
    }

    // --- Utilità ---

    @Test fun importi() {
        assertEquals(1250L, Extractor.parseAmount("12,50"))
        assertEquals(1250L, Extractor.parseAmount("12.50"))
        assertEquals(123456L, Extractor.parseAmount("1.234,56"))
        assertEquals(123456L, Extractor.parseAmount("1,234.56"))
        assertEquals(120000L, Extractor.parseAmount("1.200"))
        assertEquals(500L, Extractor.parseAmount("5"))
    }

    @Test fun tipiDiApp() {
        assertEquals(SourceKind.PAYMENT, Extractor.kindOf("com.revolut.revolut"))
        assertEquals(SourceKind.PAYMENT, Extractor.kindOf("com.satispay.customer"))
        assertEquals(SourceKind.MAIL, Extractor.kindOf("com.google.android.gm"))
        assertEquals(SourceKind.CHAT, Extractor.kindOf("com.whatsapp"))
        assertEquals(SourceKind.SMS, Extractor.kindOf("com.google.android.apps.messaging"))
        assertEquals(SourceKind.SHOP, Extractor.kindOf("com.amazon.mShop.android.shopping"))
        assertEquals(SourceKind.OTHER, Extractor.kindOf("com.booking"))
    }
}
