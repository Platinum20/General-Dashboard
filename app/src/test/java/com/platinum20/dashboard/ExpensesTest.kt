package com.platinum20.dashboard

import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Expenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpensesTest {
    private fun check(input: String, cents: Long, text: String, category: ExpenseCategory) {
        val e = Expenses.parse(input)!!
        assertEquals(input, cents, e.cents)
        assertEquals(input, text, e.text)
        assertEquals(input, category, e.category)
    }

    @Test fun virgola() = check("12,50 pizza", 1250, "Pizza", ExpenseCategory.EATING_OUT)
    @Test fun euroDopo() = check("benzina 40€", 4000, "Benzina", ExpenseCategory.TRANSPORT)
    @Test fun euroPrima() = check("€ 9.99 netflix", 999, "Netflix", ExpenseCategory.FUN)
    @Test fun parolaEuro() = check("spesa conad 63 euro", 6300, "Spesa conad", ExpenseCategory.FOOD)
    @Test fun vinceLEuro() = check("pizza 2 persone 30€", 3000, "Pizza 2 persone", ExpenseCategory.EATING_OUT)
    @Test fun bolletta() = check("bolletta luce 85,20", 8520, "Bolletta luce", ExpenseCategory.BILLS)
    @Test fun altro() = check("15 regalo", 1500, "Regalo", ExpenseCategory.SHOPPING)
    @Test fun nienteImporto() = assertNull(Expenses.parse("pizza"))
    @Test fun nuoveCategorie() {
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.guess("Onorato"))
        assertEquals(ExpenseCategory.BILLS, ExpenseCategory.guess("Windtre"))
        assertEquals(ExpenseCategory.HEALTH, ExpenseCategory.guess("Farmacenter Dottor Moresc"))
        // "presso" non è un distributore.
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.guess("pagamento presso Rossi"))
    }

    @Test fun altroSiRisistema() {
        val e = com.platinum20.dashboard.notes.Expense(1, 6587, "Onorato", ExpenseCategory.OTHER)
        assertEquals(ExpenseCategory.TRANSPORT, Expenses.recategorized(listOf(e)).single().category)
    }

    @Test fun nomeTagliatoDallaCarta() {
        val e = com.platinum20.dashboard.notes.Expense(1, 1250, "Farmacenter Dottor Moresc", ExpenseCategory.HEALTH)
        assertEquals("Farmacenter Dottor Moresc…", Expenses.displayName(e))
    }

    @Test fun categorieDellOttobreVero() {
        // Le spese dello screenshot di Antonio (ottobre 2026).
        assertEquals(ExpenseCategory.EATING_OUT, ExpenseCategory.guess("Ticket"))
        assertEquals(ExpenseCategory.EATING_OUT, ExpenseCategory.guess("Adelante Caffe'"))
        assertEquals(ExpenseCategory.EATING_OUT, ExpenseCategory.guess("Antico Caffe' Roma"))
        assertEquals(ExpenseCategory.SHOPPING, ExpenseCategory.guess("Amazon"))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.guess("Spesa"))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.guess("Supermercato Sigma"))
    }

    @Test fun paroleInteroONo() {
        // Le parole corte valgono solo intere, le lunghe anche come inizio.
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.guess("Barbiere Mario"))
        assertEquals(ExpenseCategory.EATING_OUT, ExpenseCategory.guess("Bar Sport"))
        assertEquals(ExpenseCategory.EATING_OUT, ExpenseCategory.guess("Pizzeria da Gino"))
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.guess("Gasolio"))
        assertEquals(ExpenseCategory.BILLS, ExpenseCategory.guess("bolletta gas"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.guess("Prelievo ATM"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.guess(""))
    }

    @Test fun vinceLaParolaPiuPrecisa() {
        assertEquals(ExpenseCategory.HEALTH, ExpenseCategory.guess("ticket sanitario"))
        assertEquals(ExpenseCategory.HEALTH, ExpenseCategory.guess("spesa farmacia"))
        assertEquals(ExpenseCategory.SHOPPING, ExpenseCategory.guess("Acqua e Sapone"))
        assertEquals(ExpenseCategory.HOME, ExpenseCategory.guess("affitto"))
    }

    @Test fun categorieVecchieSiSpostanoUnaVolta() {
        assertEquals(ExpenseCategory.BILLS, Expenses.upgradeCategory(ExpenseCategory.HOME, "Windtre"))
        assertEquals(ExpenseCategory.SHOPPING, Expenses.upgradeCategory(ExpenseCategory.FUN, "Amazon"))
        assertEquals(ExpenseCategory.EATING_OUT, Expenses.upgradeCategory(ExpenseCategory.HEALTH, "Ticket"))
        // Una scelta a mano che non viene dalle vecchie parole resta.
        assertEquals(ExpenseCategory.FOOD, Expenses.upgradeCategory(ExpenseCategory.FOOD, "Amazon"))
        assertEquals(ExpenseCategory.HOME, Expenses.upgradeCategory(ExpenseCategory.HOME, "Ikea"))
        val d = com.platinum20.dashboard.notes.NotesData(
            expenses = listOf(com.platinum20.dashboard.notes.Expense(1, 196, "Windtre", ExpenseCategory.HOME)),
            fixedExpenses = listOf(com.platinum20.dashboard.notes.FixedExpense(2, 999, "Windtre", ExpenseCategory.HOME, 9, java.time.YearMonth.of(2026, 10))),
        )
        val up = com.platinum20.dashboard.notes.NotesStore.withUpgradedCategories(d)
        assertEquals(ExpenseCategory.BILLS, up.expenses.single().category)
        assertEquals(ExpenseCategory.BILLS, up.fixedExpenses.single().category)
    }
}
