package com.platinum20.dashboard

import com.platinum20.dashboard.notes.OcrLine
import com.platinum20.dashboard.notes.OcrRows
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRowsTest {
    @Test fun colonneUniteAllaStessaAltezza() {
        val rows = OcrRows.rows(
            listOf(
                OcrLine("10:30", left = 400, top = 302, bottom = 330),
                OcrLine("PARTENZA", left = 10, top = 300, bottom = 328),
                OcrLine("TRENITALIA", left = 120, top = 20, bottom = 60),
                OcrLine("CARROZZA 5", left = 10, top = 250, bottom = 278),
            )
        )
        assertEquals(listOf("TRENITALIA", "CARROZZA 5", "PARTENZA 10:30"), rows)
    }
}
