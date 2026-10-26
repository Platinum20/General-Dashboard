package com.platinum20.dashboard

import com.platinum20.dashboard.capture.PhotoAttach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAttachTest {
    @Test fun receiptBecomesExpense() {
        val rows = listOf("CONAD", "Via Roma 1", "PANE 2,40", "LATTE 1,10", "TOTALE COMPLESSIVO 3,50")
        assertTrue(PhotoAttach.captureText(rows)!!.startsWith("3,50 "))
    }

    @Test fun fewRowsGoOnOneLine() {
        assertEquals("Concerto Vasco 12/06/2027 ore 21", PhotoAttach.captureText(listOf(" Concerto  Vasco ", "12/06/2027", "ore 21")))
    }

    @Test fun manyRowsStayLines() {
        val text = PhotoAttach.captureText(listOf("a", "b", "", "c", "d"))
        assertEquals("a\nb\nc\nd", text)
    }

    @Test fun emptyPhotoIsNull() {
        assertNull(PhotoAttach.captureText(listOf(" ", "")))
    }

    @Test fun longTextIsCut() {
        val text = PhotoAttach.captureText(List(100) { "parola numero $it" })!!
        assertTrue(text.length <= 601 && text.endsWith("…"))
    }
}
