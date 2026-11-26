package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.rowEnd
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il tocco dopo il testo di una riga mette il cursore in fondo a quella riga, non all'inizio della dopo. */
class NoteTapEndTest {
    @Test
    fun cursorGoesBeforeTheDrawnLineBreak() {
        val shown = "Ciao​Seconda"
        assertEquals(4, rowEnd(shown, 5))
    }

    @Test
    fun lastRowEndsAtTheEnd() {
        assertEquals(7, rowEnd("Seconda", 7))
    }

    @Test
    fun softWrappedRowStays() {
        assertEquals(6, rowEnd("Parola lunga", 6))
    }

    @Test
    fun realLineBreakToo() {
        assertEquals(3, rowEnd("uno\ndue", 4))
    }
}
