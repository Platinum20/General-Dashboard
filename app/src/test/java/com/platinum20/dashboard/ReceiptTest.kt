package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.OcrLine
import com.platinum20.dashboard.notes.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Scontrini veri trascritti come li legge il telefono: totale, negozio e data. */
class ReceiptTest {
    private fun parse(text: String) = Receipt.parse(text.trimIndent().lines())

    @Test fun supermercato() {
        val r = parse(
            """
            CONAD SUPERSTORE
            Via Roma 12 - Bologna
            P.IVA 01234567890
            DOCUMENTO COMMERCIALE
            di vendita o prestazione
            DESCRIZIONE IVA PREZZO(€)
            LATTE INTERO 4% 1,29
            PANE COMUNE 4% 2,10
            SCONTO FEDELTA' -1,50
            SUBTOTALE 61,90
            TOTALE COMPLESSIVO 63,40
            di cui IVA 5,77
            PAGAMENTO ELETTRONICO 63,40
            23-10-2026 18:42
            """
        )!!
        assertEquals(6340L, r.cents)
        assertEquals("Conad Superstore", r.store)
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
        assertEquals("63,40 Conad Superstore", r.captureText)
    }

    @Test fun ragioneSocialeSenzaSpa() {
        val r = parse(
            """
            ESSELUNGA S.P.A.
            Viale Piave 3
            BANANE 1,87
            TOTALE EURO 24,15
            CONTANTI 50,00
            RESTO 25,85
            """
        )!!
        assertEquals(2415L, r.cents)
        assertEquals("Esselunga", r.store)
    }

    @Test fun farmaciaColImportoSullaRigaSotto() {
        // Le colonne non si sono unite: l'importo è sulla riga dopo la parola.
        val r = parse(
            """
            Farmacia Centrale
            C.F. 01234567890
            TACHIPIRINA 500 5,90
            TOTALE
            12,40
            12/10/26
            """
        )!!
        assertEquals(1240L, r.cents)
        assertEquals("Farmacia Centrale", r.store)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
    }

    @Test fun benzinaioConImportoPagato() {
        val r = parse(
            """
            ENI STATION
            EROGATORE 3 BENZINA
            LITRI 22,31
            PREZZO 1,789
            IMPORTO PAGATO 39,91
            """
        )!!
        assertEquals(3991L, r.cents)
        assertEquals("Eni Station", r.store)
    }

    @Test fun ristoranteConCopertoEMigliaia() {
        val r = parse(
            """
            TRATTORIA DA MARIO SRL
            COPERTO X4 10,00
            TOTALE 1.234,50
            CARTA DI CREDITO 1.234,50
            """
        )!!
        assertEquals(123450L, r.cents)
        assertEquals("Trattoria Da Mario", r.store)
    }

    @Test fun senzaParolaDelTotaleVinceIlPiuGrande() {
        val r = parse(
            """
            BAR SPORT
            CAFFE 1,20
            BRIOCHE 1,50
            3,90
            CONTANTE 10,00
            """
        )!!
        assertEquals(390L, r.cents)
    }

    @Test fun laDataNonEUnImporto() {
        val r = parse(
            """
            EDICOLA
            23.10.2026
            QUOTIDIANO 1,90
            """
        )!!
        assertEquals(190L, r.cents)
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
    }

    @Test fun senzaImportiNiente() = assertNull(parse("FOTO SFOCATA\nNIENTE QUI"))

    @Test fun colonneUniteAllaStessaAltezza() {
        val rows = Receipt.rows(
            listOf(
                OcrLine("63,40", left = 400, top = 302, bottom = 330),
                OcrLine("TOTALE COMPLESSIVO", left = 10, top = 300, bottom = 328),
                OcrLine("CONAD", left = 120, top = 20, bottom = 60),
                OcrLine("LATTE 1,29", left = 10, top = 250, bottom = 278),
            )
        )
        assertEquals(listOf("CONAD", "LATTE 1,29", "TOTALE COMPLESSIVO 63,40"), rows)
    }

    @Test fun diventaUnaSpesaDellaCategoriaGiusta() {
        val guess = parse("CONAD\nTOTALE 63,40")!!
        val e = Expenses.parse(guess.captureText)!!
        assertEquals(6340L, e.cents)
        assertEquals(ExpenseCategory.FOOD, e.category)
    }
}
