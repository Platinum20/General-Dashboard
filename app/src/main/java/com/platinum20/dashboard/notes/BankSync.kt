package com.platinum20.dashboard.notes

import com.platinum20.dashboard.bank.BankTx
import java.time.LocalDate
import kotlin.math.abs

/**
 * Le spese lette dal conto (Revolut) dentro il calendario. Il conto ha l'ultima parola sull'importo
 * e sul giorno; nome e categoria cambiati a mano restano. Una spesa già presa da una notifica
 * (stesso importo, stesso giorno o quello accanto) diventa quella del conto invece di raddoppiare,
 * e prende la categoria di Revolut; anche una spesa del conto rimasta "Altro" la prende appena c'è.
 */
object BankSync {
    fun apply(data: NotesData, txs: List<BankTx>, since: LocalDate, firstId: Long): NotesData {
        var expenses = data.expenses
        var nextId = firstId
        for (tx in txs.filter { !it.date.isBefore(since) }.sortedBy { it.date }) {
            val existing = expenses.firstOrNull { it.bankId == tx.id }
            when {
                !tx.counts -> if (existing != null) expenses = expenses.filter { it !== existing }
                existing != null -> {
                    val category = if (existing.category == ExpenseCategory.OTHER) tx.category else existing.category
                    // Anche l'ora: le spese del conto salvate prima della 1.349 la prendono alla prossima lettura.
                    val at = tx.at ?: existing.at
                    if (existing.cents != tx.cents || existing.date != tx.date || existing.category != category || existing.at != at) {
                        expenses = expenses.map { if (it === existing) it.copy(cents = tx.cents, date = tx.date, category = category, at = at) else it }
                    }
                }
                tx.id in data.bankIgnored -> Unit
                else -> {
                    val twin = expenses.firstOrNull {
                        it.bankId == null && it.fixedId == null && it.cents == tx.cents && abs(it.date.toEpochDay() - tx.date.toEpochDay()) <= 1
                    }
                    expenses = if (twin != null) {
                        expenses.map { if (it === twin) it.copy(bankId = tx.id, date = tx.date, at = tx.at ?: it.at, category = if (tx.category != ExpenseCategory.OTHER) tx.category else it.category) else it }
                    } else {
                        listOf(Expense(nextId++, tx.cents, tx.text, tx.category, tx.date, bankId = tx.id, at = tx.at)) + expenses
                    }
                }
            }
        }
        return if (expenses == data.expenses) data else data.copy(expenses = expenses)
    }
}
