package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureComposer
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerDraftTest {
    @Test
    fun riaprendoTornaLaBozza() {
        assertEquals("comprare il pane", CaptureComposer.draftOnOpen("comprare il pane", ""))
        assertEquals("dal widget", CaptureComposer.draftOnOpen("comprare il pane", "dal widget"))
        assertEquals("", CaptureComposer.draftOnOpen("", ""))
    }

    @Test
    fun laXSvuotaTutto() {
        CaptureComposer.text = androidx.compose.ui.text.input.TextFieldValue("comprare il pane domani")
        CaptureComposer.chosen = com.platinum20.dashboard.capture.CaptureDestination.entries.first()
        CaptureComposer.clear()
        assertEquals("", CaptureComposer.text.text)
        assertEquals(null, CaptureComposer.chosen)
    }
}
