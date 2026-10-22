package com.platinum20.dashboard

import com.platinum20.dashboard.autofill.AutoFillRules
import com.platinum20.dashboard.autofill.Found
import com.platinum20.dashboard.autofill.Suggestion
import com.platinum20.dashboard.autofill.SuggestionState
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AutoFillRulesTest {
    private val now = LocalDateTime.of(2026, 10, 22, 10, 30)
    private val nowMs = 1_800_000_000_000L
    private val day = 24 * 3600_000L
    private val lidl = Found.Spent(1250, "Lidl", ExpenseCategory.FOOD, now.toLocalDate(), now)
    private val pacco = Found.NewParcel("Pacco Poste", "RR123456789IT", null)

    @Test fun giaVistaNonTorna() {
        val seen = listOf(Suggestion(1, lidl, "Revolut", nowMs, SuggestionState.DISMISSED))
        assertFalse(AutoFillRules.isNew(lidl, seen, NotesData()))
        assertTrue(AutoFillRules.isNew(lidl.copy(at = now.plusMinutes(5)), seen, NotesData()))
    }

    @Test fun pacchoGiaInNote() {
        val notes = NotesData(parcels = listOf(Parcel(1, "Scarpe", "rr123456789it")))
        assertFalse(AutoFillRules.isNew(pacco, emptyList(), notes))
    }

    @Test fun spesaGiaScrittaAMano() {
        val notes = NotesData(expenses = listOf(Expense(1, 1250, "spesa", ExpenseCategory.FOOD, now.toLocalDate())))
        assertFalse(AutoFillRules.isNew(lidl, emptyList(), notes))
        assertTrue(AutoFillRules.isNew(lidl.copy(cents = 1300), emptyList(), notes))
    }

    @Test fun promemoriaGiaPresente() {
        val d = Found.Deadline("Scadenza: bolletta luce", now.plusDays(5))
        val notes = NotesData(tasks = listOf(TaskItem(1, "scadenza: Bolletta luce")))
        assertFalse(AutoFillRules.isNew(d, emptyList(), notes))
    }

    @Test fun cosePassateNo() {
        assertFalse(AutoFillRules.isFresh(Found.Deadline("Visita", now.minusDays(1)), now.minusDays(20), now))
        assertTrue(AutoFillRules.isFresh(Found.Deadline("Visita", now.plusDays(1)), now.minusDays(20), now))
        assertFalse(AutoFillRules.isFresh(pacco, now.minusDays(20), now))
        assertFalse(AutoFillRules.isFresh(pacco.copy(expected = LocalDate.of(2026, 10, 20)), now.minusDays(3), now))
        assertTrue(AutoFillRules.isFresh(pacco, now.minusDays(3), now))
    }

    @Test fun inOggi() {
        val list = listOf(
            Suggestion(1, lidl, "Revolut", nowMs - 2 * day, SuggestionState.PENDING),
            Suggestion(2, pacco, "Poste", nowMs - 1000, SuggestionState.ADDED, ref = 9, auto = true),
            Suggestion(3, pacco.copy(code = "X1"), "Poste", nowMs - 2 * day, SuggestionState.ADDED, ref = 9, auto = true),
            Suggestion(4, pacco.copy(code = "X2"), "Poste", nowMs, SuggestionState.DISMISSED),
        )
        assertEquals(listOf(2L, 1L), AutoFillRules.visible(list, nowMs).map { it.id })
    }

    @Test fun pulizia() {
        val list = listOf(
            Suggestion(1, lidl, "Revolut", nowMs - 90 * day, SuggestionState.DISMISSED),
            Suggestion(2, pacco, "Poste", nowMs - 90 * day, SuggestionState.PENDING),
        )
        assertEquals(listOf(2L), AutoFillRules.prune(list, nowMs).map { it.id })
    }
}
