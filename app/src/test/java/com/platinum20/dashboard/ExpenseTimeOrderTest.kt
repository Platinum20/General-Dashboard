package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.bank.BankTx
import com.platinum20.dashboard.bank.RevolutParse
import com.platinum20.dashboard.notes.BankSync
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.expenseEntries
import com.platinum20.dashboard.ui.screens.pastDays
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 1.349: dentro il giorno le spese vanno in ordine di orario, da qualunque parte arrivino (Antonio). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExpenseTimeOrderTest {
    private val oggi = LocalDate.of(2026, 11, 4)
    private val adesso = oggi.atTime(20, 0)
    private val zone = ZoneId.of("Europe/Rome")
    private fun spesa(id: Long, day: LocalDate, at: LocalTime?) = Expense(id, 100, "s$id", ExpenseCategory.OTHER, day, at = at)

    // Salvate in ordine sparso, come arrivano da mano, notifiche e conto.
    private fun sparse(day: LocalDate) = listOf(
        spesa(1, day, LocalTime.of(18, 30)),
        spesa(2, day, LocalTime.of(8, 5)),
        spesa(3, day, null),
        spesa(4, day, LocalTime.of(12, 0)),
    )

    @Test fun oggiPerOrario() {
        val today = dueDays(emptyList(), emptyList(), emptyList(), emptyList(), now = adesso, zone = zone, withToday = true, expenses = sparse(oggi)).first()
        assertEquals(listOf(3L, 2L, 4L, 1L), today.rows.mapNotNull { it.expense?.id })
    }

    @Test fun giorniPassatiPerOrario() {
        val ieri = oggi.minusDays(1)
        val past = pastDays(emptyList(), emptyList(), emptyList(), now = adesso, zone = zone, expenses = sparse(ieri), withToday = true).single()
        assertEquals(listOf(3L, 2L, 4L, 1L), past.rows.mapNotNull { it.expense?.id })
    }

    @Test fun meseDelleSpesePerOrario() {
        val rows = expenseEntries(sparse(oggi) + spesa(5, oggi.minusDays(1), LocalTime.of(23, 0)), oggi)
        assertEquals(listOf(5L, 3L, 2L, 4L, 1L), rows.map { it.second.id })
    }

    @Test fun ilContoPortaLOraAncheAlleVecchie() {
        val vecchia = Expense(1, 480, "Bar", ExpenseCategory.FOOD, oggi, bankId = "tx1")
        val synced = BankSync.apply(NotesData(expenses = listOf(vecchia)), listOf(BankTx("tx1", 480, "Bar", ExpenseCategory.FOOD, oggi, at = LocalTime.of(9, 15))), oggi.minusDays(30), 100)
        assertEquals(LocalTime.of(9, 15), synced.expenses.single().at)
        val nuova = BankSync.apply(NotesData(), listOf(BankTx("tx2", 700, "Pizza", ExpenseCategory.FOOD, oggi, at = LocalTime.of(21, 40))), oggi.minusDays(30), 100)
        assertEquals(LocalTime.of(21, 40), nuova.expenses.single().at)
    }

    @Test fun revolutLeggeLOra() {
        val ms = oggi.atTime(13, 47, 22).atZone(zone).toInstant().toEpochMilli()
        val body = """[{"id":"a","legId":"a","amount":-1250,"currency":"EUR","type":"CARD_PAYMENT","state":"COMPLETED","startedDate":$ms,"merchant":{"name":"Lidl"}}]"""
        assertEquals(LocalTime.of(13, 47), RevolutParse.read(body, zone)!!.txs.single().at)
    }
}
