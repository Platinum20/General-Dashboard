package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.LineDrag
import com.platinum20.dashboard.ui.screens.SAVE_PAUSE_MS
import com.platinum20.dashboard.ui.screens.lineDragStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'editor più fluido (2026-11-24, /mnt/project-files/editor-fluido/): il trascinamento delle righe e il salvataggio. */
class NoteEditorFluidTest {
    private val row = 60f
    private val indent = 80f

    @Test
    fun sidewaysDragIndentsOnlyWhenClearlySideways() {
        // Di lato più di metà rientro: dentro o fuori.
        assertEquals(LineDrag.IN, lineDragStep(70f, 5f, row, indent))
        assertEquals(LineDrag.OUT, lineDragStep(-70f, -5f, row, indent))
        // Un po' di lato ma soprattutto in giù: sposta, non rientra.
        assertEquals(LineDrag.DOWN, lineDragStep(70f, 60f, row, indent))
        assertEquals(LineDrag.UP, lineDragStep(-20f, -50f, row, indent))
        // Poco movimento: niente.
        assertEquals(LineDrag.NONE, lineDragStep(30f, 20f, row, indent))
    }

    @Test
    fun savesAtTheFirstPauseNotAtEveryLetter() {
        // Abbastanza corta da non perdere niente, abbastanza lunga da non salvare fra una lettera e l'altra.
        assertTrue(SAVE_PAUSE_MS in 300L..1_000L)
    }
}
