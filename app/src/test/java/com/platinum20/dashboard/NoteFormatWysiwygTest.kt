package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteFormat.Edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** I formati senza segni a vista (1.577): la tastiera non rompe mai un "**" che non si vede. */
class NoteFormatWysiwygTest {
    private fun backspace(t: String, c: Int): Edit {
        val n = t.removeRange(c - 1, c)
        return NoteFormat.afterEdit(t, c, c, n, c - 1) ?: Edit(n, c - 1)
    }

    private fun type(t: String, c: Int, s: String): Edit {
        val n = t.substring(0, c) + s + t.substring(c)
        return NoteFormat.afterEdit(t, c, c, n, c + s.length) ?: Edit(n, c + s.length)
    }

    @Test
    fun pendingFormatWhileTyping() {
        val on = NoteFormat.toggle("ciao ", 5, 5, NoteFormat.BOLD)
        assertEquals(Edit("ciao ****", 7), on)
        assertEquals("ciao ", NoteFormat.plain(on.text))
        assertEquals(setOf(NoteFormat.Kind.BOLD), NoteFormat.active(on.text, 7, 7))
        val typed = type(on.text, 7, "x")
        assertEquals(Edit("ciao **x**", 8), typed)
        // Lo spazio dopo la parola non fa ricomparire i segni.
        assertEquals("ciao x ", NoteFormat.plain(type(typed.text, 8, " ").text))
        // Il cursore va altrove: il formato vuoto sparisce; G di nuovo lo spegne.
        assertEquals(Edit("ciao ", 0), NoteFormat.tidy(on.text, 0, 0))
        assertNull(NoteFormat.tidy(on.text, 7, 7))
        assertEquals(Edit("ciao ", 5), NoteFormat.toggle(on.text, 7, 7, NoteFormat.BOLD))
        assertEquals("_a_", type(NoteFormat.toggle("", 0, 0, NoteFormat.ITALIC).text, 1, "a").text)
    }

    @Test
    fun toggleKeepsMarksValid() {
        assertEquals("**_ciao_**", NoteFormat.toggle("**ciao**", 2, 6, NoteFormat.ITALIC).text)
        assertEquals("**a**bc**d**", NoteFormat.toggle("**abcd**", 3, 5, NoteFormat.BOLD).text)
        assertEquals("**a b c**", NoteFormat.toggle("a **b** c", 0, 9, NoteFormat.BOLD).text)
        assertEquals("**ab**\n**cd**", NoteFormat.toggle("ab\ncd", 0, 5, NoteFormat.BOLD).text)
        assertEquals(Edit("x ab y", 3), NoteFormat.toggle("x **ab** y", 5, 5, NoteFormat.BOLD))
        // Le note vecchie si leggono come prima.
        assertEquals("x", NoteFormat.plain("***x***"))
        assertEquals("a * b * c 2*3 file_name_x", NoteFormat.plain("a * b * c 2*3 file_name_x"))
    }

    @Test
    fun deletingNeverBreaksMarks() {
        assertEquals(Edit("**a**", 3), backspace("**ab**", 6))
        assertEquals(Edit("x**ab**", 1), backspace("x **ab**", 4))
        assertEquals(Edit("****", 2), backspace("**a**", 5))
        assertEquals(Edit("uno\nTitolo", 4), backspace("uno\n## Titolo", 7))
        assertEquals(Edit("uno\n**T**", 6), backspace("uno\n## **T**", 9))
        val old = "x**ab**y"
        assertEquals(Edit("**b**y", 0), NoteFormat.afterEdit(old, 0, 4, old.removeRange(0, 4), 0))
        assertEquals(Edit("z**b**y", 1), NoteFormat.afterEdit(old, 0, 4, "z" + old.removeRange(0, 4), 1))
    }

    @Test
    fun enterAndSpacesInsideFormats() {
        assertEquals(Edit("**ab**\n**cd**", 9), type("**abcd**", 4, "\n"))
        assertEquals(Edit("- **ab**\n- **cd**", 13), NoteFormat.afterEdit("- **abcd**", 6, 6, "- **ab\n- cd**", 9))
        assertEquals(Edit("\n**ab**", 3), type("**ab**", 2, "\n"))
        assertEquals(Edit("x\n", 2), type("x****", 3, "\n"))
        assertEquals(Edit("x  **ab**", 5), type("x **ab**", 4, " "))
        assertNull(NoteFormat.afterEdit("**ab**", 3, 3, "**azb**", 4))
    }
}
