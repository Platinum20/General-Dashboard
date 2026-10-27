package com.platinum20.dashboard

import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.capture.PhotoAttach.Found
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PhotoAttachTest {
    private val now = LocalDateTime.of(2026, 10, 27, 10, 0)

    @Test fun receiptBecomesExpense() {
        val rows = listOf("CONAD", "Via Roma 1", "PANE 2,40", "LATTE 1,10", "TOTALE COMPLESSIVO 3,50")
        val found = PhotoAttach.decide(rows, now)
        assertTrue(found is Found.Text && found.text.startsWith("3,50 "))
    }

    @Test fun ticketWithDateGoesToTheBarOnOneLine() {
        assertEquals(Found.Text("Concerto Vasco 12/06/2027"), PhotoAttach.decide(listOf(" Concerto  Vasco ", "12/06/2027"), now))
    }

    @Test fun nothingUsefulIsAPhotoNote() {
        assertEquals(Found.Photo("Bar Sport\nAperitivi"), PhotoAttach.decide(listOf("Bar Sport", "", "Aperitivi"), now))
    }

    @Test fun noTextIsAPhotoNote() {
        assertEquals(Found.Photo(""), PhotoAttach.decide(emptyList(), now))
    }

    @Test fun longTextInTheNoteIsCut() {
        val found = PhotoAttach.decide(List(100) { "parola numero" }, now) as Found.Photo
        assertTrue(found.text.length <= 601 && found.text.endsWith("…"))
    }
}
