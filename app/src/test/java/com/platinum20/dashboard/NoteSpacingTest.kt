package com.platinum20.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.ui.screens.inlineMarks
import com.platinum20.dashboard.ui.screens.lineAir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 2026-11-26 (la guida): aria intorno ai pezzi inseriti e ai titoletti, formati dentro colonne e tabelle. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NoteSpacingTest {
    private fun air(text: String, index: Int) = NoteLines.parse(text).let { lineAir(it[index], it.getOrNull(index - 1)).value }

    @Test
    fun piuAriaSopraITitoliESottoIBlocchi() {
        val plain = air("uno\ndue", 1)
        assertTrue(air("uno\n## Titolo", 1) > plain)
        assertTrue(air("!!💡 riquadro\ndopo", 1) > plain)
        assertTrue(air("| a | b |\n| c | d |\ndopo", 2) > plain)
        assertTrue(air("prima\n!!💡 riquadro", 1) > plain)
        assertTrue(air("‖ a ‖ b ‖\ndopo", 1) > plain)
        // Le righe di una lista normale restano strette.
        assertEquals(plain, air("- uno\n- due", 1))
    }

    @Test
    fun grassettoNelleColonneSenzaAsterischi() {
        val t = inlineMarks("**Rotella** in alto", Color.Blue, Color.Yellow)
        assertEquals("Rotella in alto", t.text.text)
        assertTrue(t.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 7 })
        assertEquals(0, t.offsetMapping.originalToTransformed(2))
        assertEquals(7, t.offsetMapping.originalToTransformed(11))
        assertEquals(t.text.length, t.offsetMapping.originalToTransformed(19))
        assertEquals(19, t.offsetMapping.transformedToOriginal(t.text.length))
        assertEquals("senza segni", inlineMarks("senza segni", Color.Blue, Color.Yellow).text.text)
    }
}
