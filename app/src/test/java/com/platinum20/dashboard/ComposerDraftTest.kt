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
}
