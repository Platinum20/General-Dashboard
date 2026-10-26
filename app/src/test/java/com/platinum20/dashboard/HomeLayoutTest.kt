package com.platinum20.dashboard

import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.settings.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val oldOrder = "WEATHER,RESUME,TOMORROW,WEEK,MAIL,FOUND,DAY,RECAP"

    @Test fun ordineNuovo() {
        assertEquals(
            listOf("Adesso", "Media", "Prossime cose", "Spese", "Dalla posta", "La tua settimana", "Il punto del giorno"),
            HomeLayout.DEFAULT.order.map { it.label },
        )
    }

    @Test fun daVersioneVecchiaOrdinePredefinito() {
        val layout = HomeLayout.read("RECAP,DAY,WEATHER,RESUME,TOMORROW,WEEK,MAIL,FOUND", "TOMORROW,WEEK", version = 1)
        assertEquals(HomeLayout.DEFAULT.order, layout.order)
        assertEquals(setOf(HomeCard.WEEK), layout.hidden)
    }

    @Test fun dallaPostaNascostaSoloSeLoeranoEntrambe() {
        assertEquals(emptySet<HomeCard>(), HomeLayout.read(oldOrder, "MAIL", 1).hidden)
        assertEquals(emptySet<HomeCard>(), HomeLayout.read(oldOrder, "FOUND", 1).hidden)
        assertEquals(setOf(HomeCard.MAIL), HomeLayout.read(oldOrder, "MAIL,FOUND", 1).hidden)
    }

    @Test fun primaInstallazione() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.read(null, null, 1))
    }

    @Test fun ordineScelto() {
        val layout = HomeLayout.read("RECAP,WEATHER", "DAY", HomeLayout.VERSION)
        assertEquals(HomeCard.RECAP, layout.order.first())
        assertEquals(HomeCard.entries.size, layout.order.size)
        assertEquals(setOf(HomeCard.DAY), layout.hidden)
    }

    @Test fun descrizioniCorte() {
        HomeCard.entries.forEach { assertTrue(it.description, it.description.length <= 36) }
    }

    @Test fun prossimeCoseTornaVisibile() {
        // "La giornata" nascosta prima della 1.186 diventa "Prossime cose": si rivede, le altre restano come erano.
        val layout = HomeLayout.read("WEATHER,DAY,RECAP", "DAY,RECAP", version = 2)
        assertEquals(setOf(HomeCard.RECAP), layout.hidden)
        assertEquals(HomeCard.MONEY, layout.order[layout.order.indexOf(HomeCard.DAY) + 1])
    }
}
