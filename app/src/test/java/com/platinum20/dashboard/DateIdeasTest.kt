package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.DateIdeas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Gli esempi in Date compaiono solo con poche voci e mai per una data che c'è già. */
class DateIdeasTest {
    private fun date(title: String) = Countdown(title.hashCode().toLong(), title, LocalDate.of(2027, 1, 1))

    @Test fun senzaDateQuattroEsempi() {
        val ideas = DateIdeas.suggest(emptyList())
        assertEquals(4, ideas.size)
        assertTrue(ideas.first().yearly)
    }

    @Test fun nessunEsempioDaTreDateInSu() =
        assertEquals(emptyList<Any>(), DateIdeas.suggest(listOf(date("A"), date("B"), date("C"))))

    @Test fun nonProponeQuelloCheCeGia() {
        val titles = DateIdeas.suggest(listOf(date("Compleanno di Marco"), date("scadenza PASSAPORTO"))).map { it.title.trim() }
        assertEquals(listOf("Anniversario", "Revisione auto", "Vacanze"), titles)
    }

    @Test fun ignoraAccentiEMaiuscole() {
        val titles = DateIdeas.suggest(listOf(date("Vacanzè"))).map { it.title.trim() }
        assertTrue("Vacanze" !in titles)
    }
}
