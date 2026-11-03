package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.ui.screens.MonthSwipe
import com.platinum20.dashboard.ui.screens.monthSubline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Il riepilogo del mese conta solo quel mese (Antonio): niente mese prima, si apre su quello di oggi. */
class MonthOnlyTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val november = YearMonth.of(2026, 11)
    private val october = YearMonth.of(2026, 10)

    // Come arrivano da Revolut con tutta la storia (1.331): molto ottobre, poco novembre.
    private val list = listOf(
        Expense(1, 120_00, "Affitto", ExpenseCategory.HOME, LocalDate.of(2026, 10, 1), fixedId = 9),
        Expense(2, 45_30, "Conad", ExpenseCategory.FOOD, LocalDate.of(2026, 10, 31), bankId = "a"),
        Expense(3, 12_20, "Bar", ExpenseCategory.EATING_OUT, LocalDate.of(2026, 11, 1), bankId = "b"),
        Expense(4, 8_00, "Panino", ExpenseCategory.EATING_OUT, today),
    )

    @Test fun totaleSoloDelMese() {
        assertEquals(20_20, Expenses.total(list, november))
        assertEquals(165_30, Expenses.total(list, october))
    }

    @Test fun categorieSoloDelMese() {
        assertEquals(listOf(ExpenseCategory.EATING_OUT to 20_20L), Expenses.byCategory(list, november))
    }

    @Test fun rigaSottoIlTotaleSenzaMesePrima() {
        // Novembre non ha fisse: nessuna riga, e mai il totale di ottobre.
        assertNull(monthSubline(list, november))
        val line = monthSubline(list, october)!!
        assertFalse(line.contains("settembre"))
        assertEquals("fisse ${Expenses.format(120_00)} · variabili ${Expenses.format(45_30)}", line)
    }

    @Test fun ilMesePrimaSoloTrascinando() {
        val now = YearMonth.from(today)
        assertEquals(october, MonthSwipe.target(now, 200f, 56f, now))
        assertNull(MonthSwipe.target(now, -200f, 56f, now))
        assertEquals(now, MonthSwipe.target(october, -200f, 56f, now))
    }
}
