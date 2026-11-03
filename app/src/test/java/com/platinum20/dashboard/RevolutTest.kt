package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.bank.BankTx
import com.platinum20.dashboard.bank.RevolutParse
import com.platinum20.dashboard.notes.BankSync
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.ui.screens.revolutLine
import com.platinum20.dashboard.ui.screens.stampedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Revolut non ha un formato pubblico: la lettura riconosce movimenti e saldo dalla forma del JSON. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RevolutTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val nov3ms = nov3.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3_600_000L

    private fun tx(id: String, amount: Long, type: String = "CARD_PAYMENT", state: String = "COMPLETED", extra: String = "") =
        """{"id":"$id","type":"$type","state":"$state","startedDate":$nov3ms,"currency":"EUR","amount":$amount,"balance":99999,
           "account":{"id":"a","type":"CURRENT"}$extra}"""

    @Test
    fun cardPaymentsBecomeExpenses() {
        val body = "[" + listOf(
            tx("1", -1250, extra = ""","merchant":{"name":"Lidl","category":"supermarkets"},"tag":"groceries""""),
            tx("2", -480, extra = ""","merchant":{"name":"Bar Centrale"},"tag":"restaurants""""),
            tx("3", 5000, type = "TOPUP"),
            tx("4", -2000, type = "EXCHANGE"),
            tx("5", -300, type = "TRANSFER", extra = ""","description":"To pocket EUR Vacanze""""),
            tx("6", -1999, state = "DECLINED", extra = ""","description":"Amazon""""),
        ).joinToString(",") + "]"
        val read = RevolutParse.read(body, zone)!!
        val byId = read.txs.associateBy { it.id }
        assertEquals(1250L, byId["1"]!!.cents)
        assertEquals("Lidl", byId["1"]!!.text)
        assertEquals(ExpenseCategory.FOOD, byId["1"]!!.category)
        assertEquals(ExpenseCategory.EATING_OUT, byId["2"]!!.category)
        assertEquals(nov3, byId["1"]!!.date)
        assertNull(byId["3"]); assertNull(byId["4"]); assertNull(byId["5"])
        // Rifiutata: arriva lo stesso, per togliere la spesa se era già stata presa mentre era in attesa.
        assertFalse(byId["6"]!!.counts)
        // Il "balance" dentro i movimenti è quello dopo il movimento, non il saldo del conto.
        assertNull(read.balanceCents)
    }

    @Test
    fun walletGivesCurrentEuroBalance() {
        val body = """{"baseCurrency":"EUR","pockets":[
            {"id":"p1","type":"CURRENT","state":"ACTIVE","currency":"EUR","balance":123456},
            {"id":"p2","type":"SAVINGS","state":"ACTIVE","currency":"EUR","balance":50000},
            {"id":"p3","type":"CURRENT","state":"ACTIVE","currency":"USD","balance":700}]}"""
        assertEquals(123456L, RevolutParse.read(body, zone)!!.balanceCents)
        assertNull(RevolutParse.read("""{"user":{"name":"x"}}""", zone))
        assertNull(RevolutParse.read("<html>", zone))
    }

    @Test
    fun syncAddsUpdatesRemovesAndMergesNotificationTwin() {
        val fromNotification = Expense(10, 1250, "Revolut", ExpenseCategory.OTHER, nov3)
        val start = NotesData(expenses = listOf(fromNotification))
        val since = LocalDate.of(2026, 10, 1)
        val first = BankSync.apply(start, listOf(
            BankTx("1", 1250, "Lidl", ExpenseCategory.FOOD, nov3),
            BankTx("2", 480, "Bar", ExpenseCategory.EATING_OUT, nov3),
            BankTx("old", 900, "Vecchia", ExpenseCategory.OTHER, LocalDate.of(2026, 9, 2)),
        ), since, 100)
        assertEquals(2, first.expenses.size)
        // Quella della notifica diventa quella del conto (niente doppione), il nome scelto resta.
        assertEquals("1", first.expenses.first { it.id == 10L }.bankId)
        assertEquals("Bar", first.expenses.first { it.bankId == "2" }.text)

        // Da in attesa a contabilizzata con importo diverso, poi annullata.
        val second = BankSync.apply(first, listOf(BankTx("2", 500, "Bar", ExpenseCategory.EATING_OUT, nov3)), since, 200)
        assertEquals(500L, second.expenses.first { it.bankId == "2" }.cents)
        val third = BankSync.apply(second, listOf(BankTx("2", 500, "Bar", ExpenseCategory.EATING_OUT, nov3, counts = false)), since, 300)
        assertTrue(third.expenses.none { it.bankId == "2" })
        // Rilette uguali: nessun cambiamento.
        assertTrue(BankSync.apply(third, listOf(BankTx("1", 1250, "Lidl", ExpenseCategory.FOOD, nov3)), since, 400) === third)
    }

    @Test
    fun deletedBankExpenseStaysDeleted() {
        val data = NotesData(bankIgnored = setOf("9"))
        val after = BankSync.apply(data, listOf(BankTx("9", 100, "X", ExpenseCategory.OTHER, nov3)), nov3, 1)
        assertTrue(after.expenses.isEmpty())
    }

    @Test
    fun balanceLine() {
        assertNull(revolutLine(linked = false, expired = false, balance = 100))
        assertTrue(revolutLine(true, false, 123456)!!.startsWith("Saldo Revolut"))
        assertTrue(revolutLine(true, true, 123456)!!.contains("rifai l'accesso"))
    }

    @Test
    fun savedFileNameCarriesTime() {
        assertEquals("revolut-dati-letti-20261103-1025.txt", stampedName("revolut-dati-letti", java.time.LocalDateTime.of(2026, 11, 3, 10, 25)))
    }
}
