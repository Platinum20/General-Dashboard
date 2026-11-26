package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteSmart
import com.platinum20.dashboard.notes.NoteSmart.Kind
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NoteSmartTest {
    private fun kinds(text: String) = NoteSmart.find(text).map { it.kind to text.substring(it.range.first, it.range.last + 1) }

    @Test fun tagsAreFoundAfterSpaceOnly() {
        val k = kinds("Caldaia #casa da sistemare, mail#no e #12")
        assertEquals(listOf(Kind.TAG to "#casa"), k)
        assertEquals(setOf("casa", "lavoro"), NoteSmart.tags("#Casa\n(#lavoro) #casa"))
    }

    @Test fun datesInTheText() {
        val text = "Giovedì alle 15 dentista\nPagare bollo entro il 30"
        val d = NoteSmart.find(text).filter { it.kind == Kind.DATE }
        assertEquals(listOf("Giovedì alle 15", "entro il 30"), d.map { it.value })
        val now = LocalDateTime.of(2026, 11, 23, 12, 0)
        val due = NoteSmart.dateOf("Giovedì alle 15", now)
        assertEquals(LocalDateTime.of(2026, 11, 26, 15, 0), due)
        assertNotNull(NoteSmart.dateOf("entro il 30", now))
        assertEquals("dentista", NoteSmart.titleAround(text, d[0]))
    }

    @Test fun codesAndPasswords() {
        val k = kinds("Wifi: mare2024!\nCancello 4821\nCodice fiscale da rifare")
        assertEquals(listOf(Kind.CODE to "mare2024!", Kind.CODE to "4821"), k)
    }

    @Test fun codeWithoutSentencePunctuation() {
        assertEquals(listOf(Kind.CODE to "4821"), kinds("Il cancello (pin 4821) è a destra"))
        assertEquals(listOf(Kind.CODE to "mare2024"), kinds("La rete è wifi: mare2024."))
    }

    @Test fun parcelCode() {
        val k = kinds("Cuffie Marco, tracking RR123456789IT")
        assertEquals(listOf(Kind.PARCEL to "RR123456789IT"), k)
    }

    @Test fun nothingInsideLinks() {
        val text = "[Casa #mare](nota:3) e https://x.it/#ancora giovedì"
        val links = com.platinum20.dashboard.notes.NoteFormat.linkRanges(text)
        assertTrue(NoteSmart.find(text, links).none { it.kind == Kind.TAG })
    }

    @Test fun citedInMatchesEventsByIdOnly() {
        val ev = "app:" + Routes.calendarEvent(42, 100, 200)
        val notes = listOf(
            Note(1, "Weekend Roma\nTreno [Treno per Roma](app:home/calendar/event?id=42&begin=5&end=6) e cena"),
            Note(2, "Altra\n[Treno](app:home/calendar/event?id=420&begin=5&end=6)"),
            Note(3, "Spesa\nlatte"),
        )
        val cited = NoteSmart.citing(NotesData(notes = notes), listOf(ev))
        assertEquals(listOf(1L), cited.map { it.id })
        assertTrue(NoteSmart.citeLine(cited[0], listOf(ev)).contains("Treno per Roma"))
    }

    @Test fun taggedNotesAndTasks() {
        val data = NotesData(
            notes = listOf(Note(1, "Caldaia\n#casa revisione"), Note(2, "Lavoro\n#ufficio")),
            tasks = listOf(TaskItem(5, "Chiamare idraulico #casa")),
        )
        val (n, t) = NoteSmart.tagged(data, "#Casa")
        assertEquals(listOf(1L), n.map { it.id })
        assertEquals(listOf(5L), t.map { it.id })
        assertEquals("casa", NoteSmart.allTags(data).first())
    }

    @Test fun peopleByFrequency() {
        assertEquals(listOf("Marco Rossi", "Elena"), NoteSmart.people(listOf(listOf("Marco Rossi"), listOf("Elena", "Marco Rossi"))))
        assertEquals("Marco", NoteSmart.firstName("Marco Rossi"))
    }
}
