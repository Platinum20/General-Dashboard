package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedExpenseTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val oct = YearMonth.of(2026, 10)

    @Test fun ogniMeseIl5Affitto650() {
        val f = Expenses.parseFixed("ogni mese il 5 affitto 650", today)!!
        assertEquals(650_00L, f.cents)
        assertEquals("Affitto", f.text)
        assertEquals(5, f.day)
        // Il 5 ottobre è passato: si parte da novembre.
        assertEquals(oct.plusMonths(1), f.from)
    }

    @Test fun ancheAllaFine() {
        val f = Expenses.parseFixed("netflix 12,99 il 25 di ogni mese", today)!!
        assertEquals(12_99L, f.cents)
        assertEquals("Netflix", f.text)
        assertEquals(25, f.day)
        assertEquals(oct, f.from)
    }

    @Test fun nonFisse() {
        assertNull(Expenses.parseFixed("12,50 pizza", today))
        assertNull(Expenses.parseFixed("ogni mese il 40 affitto 650", today))
        assertNull(Expenses.parseFixed("ogni mese il 5 affitto", today))
    }

    @Test fun il31InFebbraioDiventaIl28() {
        val f = FixedExpense(1, 100, "Mutuo", ExpenseCategory.OTHER, 31, YearMonth.of(2027, 1))
        assertEquals(
            listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
            f.dueMonths(LocalDate.of(2027, 3, 31)).map { f.dateIn(it) },
        )
    }

    @Test fun siAggiungonoDaSoleSaltandoMesiENonDueVolte() {
        val f = FixedExpense(7, 650_00, "Affitto", ExpenseCategory.OTHER, 5, YearMonth.of(2026, 8))
        var n = 100L
        val once = NotesStore.withFixedApplied(NotesData(fixedExpenses = listOf(f)), today) { n++ }
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 8, 5)), once.expenses.map { it.date })
        assertTrue(once.expenses.all { it.fixedId == 7L })
        assertEquals(oct, once.fixedExpenses.single().lastMonth)
        val twice = NotesStore.withFixedApplied(once, today) { n++ }
        assertEquals(3, twice.expenses.size)
        val nextMonth = NotesStore.withFixedApplied(once, LocalDate.of(2026, 11, 5)) { n++ }
        assertEquals(4, nextMonth.expenses.size)
    }

    @Test fun fisseEVariabili() {
        val list = listOf(
            Expense(1, 650_00, "Affitto", ExpenseCategory.OTHER, today.withDayOfMonth(5), fixedId = 7),
            Expense(2, 12_50, "Pizza", ExpenseCategory.OTHER, today),
            Expense(3, 99_00, "Vecchia", ExpenseCategory.OTHER, today.minusMonths(1)),
        )
        assertEquals(650_00L to 12_50L, Expenses.split(list, oct))
    }

    @Test fun budgetGiornalieroTieneDaParteLeFisseInArrivo() {
        val netflix = FixedExpense(1, 12_99, "Netflix", ExpenseCategory.OTHER, 25, oct)
        val affitto = FixedExpense(2, 650_00, "Affitto", ExpenseCategory.OTHER, 5, oct, lastMonth = oct)
        assertEquals(12_99L, Expenses.fixedStillDue(listOf(netflix, affitto), today))
    }

    @Test fun adessoIlGiornoPrima() {
        val netflix = FixedExpense(1, 12_99, "Netflix", ExpenseCategory.OTHER, 23, oct)
        val lines = Briefing.build(
            today.atTime(10, 0), null, emptyList(), emptyList(), emptyList(), emptyList(),
            fixedExpenses = listOf(netflix),
        )
        assertTrue(lines.any { it.kind == BriefKind.MONEY && it.text.startsWith("Domani addebito: Netflix") })
    }

    @Test fun salvateERilette() {
        val f = FixedExpense(1, 12_99, "Netflix", ExpenseCategory.OTHER, 25, oct, lastMonth = oct)
        val e = Expense(2, 12_99, "Netflix", ExpenseCategory.OTHER, today, fixedId = 1)
        val back = NotesStore.parse(NotesStore.serialize(NotesData(expenses = listOf(e), fixedExpenses = listOf(f))))
        assertEquals(listOf(f), back.fixedExpenses)
        assertEquals(listOf(e), back.expenses)
    }
}
