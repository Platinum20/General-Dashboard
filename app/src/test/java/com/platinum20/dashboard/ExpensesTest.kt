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
    @Test fun bolletta() = check("bolletta luce 85,20", 8520, "Bolletta luce", ExpenseCategory.HOME)
    @Test fun altro() = check("15 regalo", 1500, "Regalo", ExpenseCategory.FUN)
    @Test fun nienteImporto() = assertNull(Expenses.parse("pizza"))
    @Test fun nuoveCategorie() {
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.guess("Onorato"))
        assertEquals(ExpenseCategory.HOME, ExpenseCategory.guess("Windtre"))
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
}
