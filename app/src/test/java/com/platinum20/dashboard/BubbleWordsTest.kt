package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Alert
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.alertChip
import com.platinum20.dashboard.ui.screens.alertChoices
import com.platinum20.dashboard.ui.screens.noteEmojiRow
import com.platinum20.dashboard.ui.screens.repeatChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Le parole e le file dei fumetti dopo la lista di Antonio del 2026-11-24. */
class BubbleWordsTest {
    @Test fun siRipeteDiceSoloIlNome() {
        assertEquals(listOf("Giorno", "Settimana", "Mese", "Anno"), Repeat.entries.map(::repeatChip))
    }

    @Test fun avvisiChiari() {
        assertEquals(listOf("All'inizio", "10 min prima", "1 ora prima", "Il giorno prima"), alertChoices(false).map { alertChip(it, false) })
        assertEquals(listOf("Alle 9", "Il giorno prima"), alertChoices(true).map { alertChip(it, true) })
    }

    @Test fun avvisoPrima() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 11, 24)
        fun ms(h: Int, m: Int) = today.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val t = TaskItem(1, "Dentista", dueAt = ms(18, 30))
        assertEquals(ms(18, 20), t.copy(alert = Alert.MIN_10).alertAt(today, zone))
        assertEquals(ms(17, 30), t.copy(alert = Alert.HOUR_1).alertAt(today, zone))
    }

    @Test fun emojiSempreOttoConQuellaDiAdesso() {
        assertEquals(8, noteEmojiRow(null).size)
        assertEquals(noteEmojiRow(null), noteEmojiRow("🛒"))
        val r = noteEmojiRow("🐶")
        assertEquals(8, r.size)
        assertEquals("🐶", r.last())
    }

    @Test fun coloreGiaMesso() {
        val text = "ciao {r:rosso} e basta"
        assertEquals('r', NoteFormat.colorHere(text, 8, 10))
        assertNull(NoteFormat.colorHere(text, 17, 20))
    }
}

class ChipsPerRowTest {
    @Test fun nomiLunghiSuDueRighe() {
        assertEquals(2, com.platinum20.dashboard.ui.screens.chipsPerRow(listOf("All'inizio", "10 min prima", "1 ora prima", "Il giorno prima")))
        assertEquals(4, com.platinum20.dashboard.ui.screens.chipsPerRow(listOf("Giorno", "Settimana", "Mese", "Anno")))
    }
}
