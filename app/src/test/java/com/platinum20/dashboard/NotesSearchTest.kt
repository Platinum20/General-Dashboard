package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesSearch
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.SearchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotesSearchTest {
    private val day = LocalDate.of(2026, 10, 22)
    private val data = NotesData(
        tasks = listOf(
            TaskItem(1, "Chiamare il meccanico", done = true),
            TaskItem(2, "Comprare il caffè"),
            TaskItem.date(8, "Compleanno di Nicolò", day),
        ),
        notes = listOf(Note(3, "Idee\nportare l'auto dal Meccanico", updatedAt = 10)),
        parcels = listOf(Parcel(7, "Cuffie", code = "1Z999AA10345678")),
        habits = listOf(Habit(9, "Caffè senza zucchero")),
    )

    private fun ids(q: String) = NotesSearch.search(data, q).map { it.id }

    @Test fun meccTrovaPromemoriaENota() {
        val hits = NotesSearch.search(data, "mecc")
        assertEquals(listOf(SearchKind.TASK, SearchKind.NOTE), hits.map { it.kind })
        assertEquals(listOf(1L, 3L), hits.map { it.id })
    }

    @Test fun senzaAccentiNeMaiuscole() {
        assertEquals(listOf(2L, 9L), ids("CAFFE"))
        assertEquals(listOf(8L), ids("nicolo"))
    }

    @Test fun completatiDopoQuelliDaFare() {
        val d = data.copy(tasks = listOf(TaskItem(1, "Pane fatto", done = true), TaskItem(2, "Pane da prendere")))
        assertEquals(listOf(2L, 1L), NotesSearch.search(d, "pane").map { it.id })
    }

    @Test fun codiceDelPacco() = assertEquals(listOf(7L), ids("1z999"))

    @Test fun vuotoNonTrovaNulla() {
        assertEquals(emptyList<Long>(), ids("   "))
        assertEquals(emptyList<Long>(), ids("zzz"))
    }

    @Test fun titoloDellaNota() =
        assertEquals("Idee", NotesSearch.search(data, "auto").single().title)
}
