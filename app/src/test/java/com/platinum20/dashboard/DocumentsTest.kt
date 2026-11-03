package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.capture.PhotoAttach.Found
import com.platinum20.dashboard.docs.DocKind
import com.platinum20.dashboard.docs.DocReader
import com.platinum20.dashboard.docs.DocScan
import com.platinum20.dashboard.docs.DocSide
import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.docs.DocumentsStore
import com.platinum20.dashboard.ui.screens.docLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DocumentsTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val now = LocalDateTime.of(2026, 11, 3, 10, 0)

    private val cieFront = listOf(
        "REPUBBLICA ITALIANA", "CARTA DI IDENTITÀ / IDENTITY CARD", "COMUNE DI MILANO",
        "COGNOME / SURNAME", "ROSSI", "NOME / NAME", "ANTONIO",
        "LUOGO E DATA DI NASCITA", "NAPOLI 14.05.1994",
        "EMISSIONE / ISSUING 02.03.2022", "SCADENZA / EXPIRY 14.05.2032", "CA12345BC",
    )

    @Test fun cartaDiIdentitaFronte() {
        val scan = DocReader.read(cieFront, today)!!
        assertEquals(DocKind.ID_CARD, scan.kind)
        assertEquals(DocSide.FRONT, scan.side)
        assertEquals("CA12345BC", scan.number)
        assertEquals(LocalDate.of(2032, 5, 14), scan.expiry)
    }

    @Test fun retroConLaBandaMrz() {
        val rows = listOf("INDIRIZZO DI RESIDENZA", "VIA ROMA 1 MILANO", "C<ITACA12345BC<<<<<<<<<<<<<<<", "9405142M3205146ITA<<<<<<<<<<<0", "ROSSI<<ANTONIO<<<<<<<<<<<<<<<<")
        val scan = DocReader.read(rows, today)!!
        assertEquals(DocKind.ID_CARD, scan.kind)
        assertEquals(DocSide.BACK, scan.side)
    }

    @Test fun patenteConAnnoACinqueCifreNo() {
        val rows = listOf("PATENTE DI GUIDA", "REPUBBLICA ITALIANA", "1. ROSSI", "2. ANTONIO", "3. 14/05/94 NAPOLI (NA)",
            "4a. 10/06/2019 4c. MIT-UCO", "4b. 14/05/2029", "5. U1A2B3C4D5", "9. AM/B1/B")
        val scan = DocReader.read(rows, today)!!
        assertEquals(DocKind.LICENSE, scan.kind)
        assertEquals(DocSide.FRONT, scan.side)
        assertEquals("U1A2B3C4D5", scan.number)
        assertEquals(LocalDate.of(2029, 5, 14), scan.expiry)
    }

    @Test fun retroDellaPatenteDalleCategorie() {
        val rows = listOf("9. 10. 11. 12.", "AM 10.06.19 14.05.29", "A1", "A2", "A", "B1 10.06.19 14.05.29", "B 10.06.19 14.05.29", "BE", "C1", "C1E", "C", "CE", "D1")
        val scan = DocReader.read(rows, today)!!
        assertEquals(DocKind.LICENSE, scan.kind)
        assertEquals(DocSide.BACK, scan.side)
    }

    @Test fun tesseraSanitaria() {
        val rows = listOf("TESSERA SANITARIA", "Codice fiscale", "RSSNTN94E14F839X", "Cognome ROSSI", "Nome ANTONIO", "Data di scadenza 31/12/2030")
        val scan = DocReader.read(rows, today)!!
        assertEquals(DocKind.HEALTH_CARD, scan.kind)
        assertEquals("RSSNTN94E14F839X", scan.number)
        assertEquals(LocalDate.of(2030, 12, 31), scan.expiry)
    }

    @Test fun scontrinoNonEUnDocumento() {
        assertNull(DocReader.read(listOf("CONAD", "PANE 2,40", "TOTALE 2,40", "03/11/2026"), today))
    }

    @Test fun scadenzaVecchiaNonVaInCalendario() {
        assertNull(DocReader.expiry("SCADENZA 01/01/2019", today))
    }

    @Test fun graffettaFronteEPoiRetro() {
        assertTrue(PhotoAttach.decide(cieFront, now) is Found.Document)
        // Subito dopo un fronte, una foto qualunque è il suo retro (anche se ha date che finirebbero nella barra).
        assertEquals(Found.Back(null), PhotoAttach.decide(listOf("Firma del titolare", "12/06/2027"), now, waitingBack = true))
        // Ma il fronte di un altro documento resta un documento nuovo.
        assertTrue(PhotoAttach.decide(cieFront, now, waitingBack = true) is Found.Document)
        // Senza un documento in attesa, tutto come prima.
        assertEquals(Found.Text("Concerto 12/06/2027"), PhotoAttach.decide(listOf("Concerto", "12/06/2027"), now))
    }

    private fun doc(id: Long, title: String = "Patente", back: String? = null, at: Long = 0L) =
        Document(id, DocKind.LICENSE, title, "/f.jpg", back = back, createdAt = at)

    @Test fun retroAspettatoSoloPerQuindiciMinuti() {
        val docs = listOf(doc(1, at = 1_000L), doc(2, back = "/b.jpg", at = 2_000L))
        assertEquals(1L, DocumentsStore.waitingBack(docs, 1_000L + 60_000L)?.id)
        assertNull(DocumentsStore.waitingBack(docs, 1_000L + DocumentsStore.BACK_WINDOW_MS + 1))
    }

    @Test fun nomiDiversiPerDueDocumentiUguali() {
        assertEquals("Patente", DocumentsStore.uniqueTitle("Patente", emptyList()))
        assertEquals("Patente 2", DocumentsStore.uniqueTitle("Patente", listOf(doc(1))))
        assertEquals("Patente 3", DocumentsStore.uniqueTitle("Patente", listOf(doc(1), doc(2, "Patente 2"))))
    }

    @Test fun salvataggioERilettura() {
        val docs = listOf(
            Document(1, DocKind.ID_CARD, "Carta d'identità", "/a.jpg", "/b.jpg", "CA12345BC", LocalDate.of(2032, 5, 14), 5L, 9L),
            doc(2),
        )
        assertEquals(docs, DocumentsStore.parse(DocumentsStore.serialize(docs)))
    }

    @Test fun rigaSottoIlNome() {
        val d = doc(1).copy(number = "U1A2B3C4D5", expiry = today.plusDays(20))
        assertEquals("U1A2B3C4D5 · scade tra 20 giorni", docLine(d, today))
        assertEquals("Solo fronte", docLine(doc(1), today))
        assertEquals("scaduto", docLine(doc(1).copy(expiry = today.minusDays(1)), today))
    }
}
