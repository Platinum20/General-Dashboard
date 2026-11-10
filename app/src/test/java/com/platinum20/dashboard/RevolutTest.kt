package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.bank.BankTx
import com.platinum20.dashboard.bank.RevolutParse
import com.platinum20.dashboard.bank.RevolutSession
import com.platinum20.dashboard.bank.RevolutStore
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

    private fun tx(id: String, amount: Long, type: String = "CARD_PAYMENT", state: String = "COMPLETED", extra: String = "", account: String = "CURRENT") =
        """{"id":"$id","type":"$type","state":"$state","startedDate":$nov3ms,"currency":"EUR","amount":$amount,"balance":99999,
           "account":{"id":"a","type":"$account"}$extra}"""

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

    /** Le forme vere viste sul telefono (dati inventati): salvadanai, giri fra tasche, bonifici, MCC. */
    @Test
    fun realShapesFromThePhone() {
        // money-boxes: la tasca del salvadanaio non ha tipo, non è il saldo del conto.
        val boxes = """[{"id":"b1","name":"Risparmi","state":"ACTIVE","balance":{"amount":1560000,"currency":"EUR"},"type":"BASIC",
            "pocket":{"id":"p2","name":"Risparmi","currency":"EUR","balance":1560000,"closed":false}}]"""
        assertNull(RevolutParse.read(boxes, zone))
        val wallet = """{"PERSONAL":[{"baseCurrency":"EUR","pockets":[
            {"id":"p1","type":"CURRENT","state":"ACTIVE","currency":"EUR","balance":16076,"blockedAmount":1220,"closed":false},
            {"id":"p2","type":"SAVINGS","name":"MB:b1","state":"ACTIVE","currency":"EUR","balance":1560000,"closed":false}]}]}"""
        assertEquals(16076L, RevolutParse.read(wallet, zone)!!.balanceCents)

        val body = "[" + listOf(
            // Dai risparmi al conto: la gamba SAVINGS in uscita e quella CURRENT in entrata, nessuna è spesa.
            tx("s1", -20000, type = "TRANSFER", extra = ""","description":"To EUR"""", account = "SAVINGS"),
            tx("s2", 20000, type = "TRANSFER", extra = ""","description":"From EUR MB:b1""""),
            tx("s3", -5000, type = "TRANSFER", extra = ""","description":"To EUR Risparmi""""),
            tx("t1", -1400, type = "TRANSFER", extra = ""","description":"To Mario Rossi","tag":"transfers""""),
            tx("c1", -1020, extra = ""","amountWithCharges":-1070,"description":"Droppos","tag":"services",
                "merchant":{"name":"DropPOS","mcc":"5542","category":"services"}"""),
            tx("c2", -1220, state = "PENDING", extra = ""","description":"Kebab","merchant":{"name":"Kebabberia","mcc":"5814","category":"restaurants"}"""),
        ).joinToString(",") + "]"
        val byId = RevolutParse.read(body, zone)!!.txs.associateBy { it.id }
        assertNull(byId["s1"]); assertNull(byId["s2"]); assertNull(byId["s3"])
        assertEquals("A Mario Rossi", byId["t1"]!!.text)
        assertEquals(1400L, byId["t1"]!!.cents)
        assertEquals(1070L, byId["c1"]!!.cents)
        assertEquals(ExpenseCategory.TRANSPORT, byId["c1"]!!.category)
        assertTrue(byId["c2"]!!.counts)
        assertEquals(ExpenseCategory.EATING_OUT, byId["c2"]!!.category)
        assertEquals(ExpenseCategory.FOOD, RevolutParse.mccCategory(5411))
        assertNull(RevolutParse.mccCategory(9311))
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
    fun revolutCategoriesMapOnOurs() {
        assertEquals(ExpenseCategory.FOOD, RevolutParse.revolutCategory("groceries"))
        assertEquals(ExpenseCategory.BILLS, RevolutParse.revolutCategory("Utilities"))
        assertEquals(ExpenseCategory.FUN, RevolutParse.revolutCategory("travel"))
        assertNull(RevolutParse.revolutCategory("services"))
        assertNull(RevolutParse.revolutCategory("transfers"))
        // La categoria scelta nell'app Revolut vince sul tag e sull'esercente.
        val body = "[" + tx("1", -900, extra = ""","category":"health","tag":"shopping","merchant":{"name":"Negozio","category":"shopping"}""") + "]"
        assertEquals(ExpenseCategory.HEALTH, RevolutParse.read(body, zone)!!.txs.single().category)
    }

    @Test
    fun bankCategoryFillsOtherButKeepsManualChoice() {
        val data = NotesData(expenses = listOf(
            Expense(1, 500, "Bar", ExpenseCategory.OTHER, nov3, bankId = "a"),
            Expense(2, 700, "Regalo", ExpenseCategory.FUN, nov3, bankId = "b"),
        ))
        val after = BankSync.apply(data, listOf(
            BankTx("a", 500, "Bar", ExpenseCategory.EATING_OUT, nov3),
            BankTx("b", 700, "Regalo", ExpenseCategory.SHOPPING, nov3),
            // Vecchia di anni: ora entra anche lei, la storia è completa.
            BankTx("c", 1200, "Libri", ExpenseCategory.SHOPPING, LocalDate.of(2023, 5, 2)),
        ), RevolutStore.SINCE, 10)
        assertEquals(ExpenseCategory.EATING_OUT, after.expenses.first { it.bankId == "a" }.category)
        assertEquals(ExpenseCategory.FUN, after.expenses.first { it.bankId == "b" }.category)
        assertTrue(after.expenses.any { it.bankId == "c" })
    }

    @Test
    fun oldestMovementAndCashWithdrawal() {
        val body = "[" + listOf(
            tx("a", -28000, type = "ATM", extra = ""","amountWithCharges":-28160,"description":"Cash at Banca","merchant":{"name":"Banca Centro","mcc":"6011"}"""),
            """{"id":"t","type":"TOPUP","state":"COMPLETED","startedDate":1000,"currency":"EUR","amount":5000}""",
        ).joinToString(",") + "]"
        val read = RevolutParse.read(body, zone)!!
        // La ricarica non è spesa, ma dice fin dove è arrivata la storia.
        assertEquals(1000L, read.oldestMs)
        val atm = read.txs.single()
        assertEquals("Prelievo · Banca Centro", atm.text)
        assertEquals(28160L, atm.cents)
    }

    @Test
    fun sessionCookiesPutBackOnlyWhenMissing() {
        val saved = "rev_session=abc; device=d1; theme=dark"
        assertEquals(listOf("rev_session=abc"), RevolutSession.missing(saved, "device=d2; theme=dark"))
        assertEquals(3, RevolutSession.missing(saved, null).size)
        assertTrue(RevolutSession.missing(saved, saved).isEmpty())
    }

    /** Il lavoro in sottofondo gira ogni 15 minuti: con una soglia più lunga salterebbe dei giri e la sessione scadrebbe. */
    @Test
    fun keepAliveTouchesTheSessionEveryRound() {
        assertTrue(RevolutStore.STALE_MS < 15 * 60 * 1000L)
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
        assertTrue(revolutLine(true, true, 123456)!!.contains("accesso scaduto"))
    }

    @Test
    fun savedFileNameCarriesTime() {
        assertEquals("revolut-dati-letti-20261103-1025.txt", stampedName("revolut-dati-letti", java.time.LocalDateTime.of(2026, 11, 3, 10, 25)))
    }
}
