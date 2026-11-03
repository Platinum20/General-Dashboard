package com.platinum20.dashboard

import com.platinum20.dashboard.bank.Pocket
import com.platinum20.dashboard.bank.RevolutPockets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Le tasche della card Revolut: dalle forme vere viste sul telefono (dati inventati). */
@RunWith(AndroidJUnit4::class)
class RevolutPocketsTest {
    private val wallet = """{"PERSONAL":[{"baseCurrency":"EUR","pockets":[
        {"id":"p1","type":"CURRENT","state":"ACTIVE","currency":"EUR","balance":16076,"closed":false},
        {"id":"p2","type":"SAVINGS","name":"MB:b1","state":"ACTIVE","currency":"EUR","balance":1560000,"closed":false},
        {"id":"p3","type":"SAVINGS","name":"Viaggi","state":"ACTIVE","currency":"EUR","balance":2000,"closed":false},
        {"id":"p4","type":"CURRENT","state":"ACTIVE","currency":"USD","balance":700},
        {"id":"p5","type":"SAVINGS","state":"CLOSED","currency":"EUR","balance":0,"closed":true}]}]}"""
    private val boxes = """[{"id":"b1","name":"Risparmi","state":"ACTIVE","balance":{"amount":1560000,"currency":"EUR"},"type":"BASIC",
        "pocket":{"id":"p2","name":"Risparmi","currency":"EUR","balance":1560000,"closed":false}}]"""

    @Test
    fun walletGivesEveryEuroPocketWithRealNames() {
        val w = RevolutPockets.read(wallet)!!
        assertEquals(listOf("p1", "p2", "p3"), w.pockets!!.map { it.id })
        val b = RevolutPockets.read(boxes)!!
        assertNull(b.pockets)
        assertEquals(mapOf("p2" to "Risparmi"), b.names)
        val named = RevolutPockets.named(w.pockets!!, b.names)
        assertEquals(listOf("Conto", "Risparmi", "Viaggi"), named.map { it.name })
        assertEquals(16076L + 1560000 + 2000, RevolutPockets.total(named))
        // Senza money-boxes il salvadanaio ha un nome generico, mai "MB:…".
        assertEquals("Salvadanaio", RevolutPockets.named(w.pockets!!, emptyMap())[1].name)
    }

    @Test
    fun transactionsAreNotPockets() {
        val txs = """[{"id":"t1","legId":"l1","amount":-500,"currency":"EUR","startedDate":1762160000000,
            "account":{"id":"p1","type":"CURRENT","currency":"EUR","balance":99999}}]"""
        assertNull(RevolutPockets.read(txs))
        assertNull(RevolutPockets.read("<html>"))
    }

    @Test
    fun savedPocketsRoundTrip() {
        val list = listOf(Pocket("p1", "Conto", 100, true), Pocket("p2", "Risparmi", 200, false))
        assertEquals(list, RevolutPockets.fromJson(RevolutPockets.toJson(list)))
        val names = mapOf("p2" to "Risparmi")
        assertEquals(names, RevolutPockets.namesFromJson(RevolutPockets.namesToJson(names)))
    }
}
