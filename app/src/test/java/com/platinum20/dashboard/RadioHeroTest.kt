package com.platinum20.dashboard

import com.platinum20.dashboard.media.radio.RadioHero
import com.platinum20.dashboard.media.radio.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioHeroTest {
    private fun s(id: String, tags: String = "") = RadioStation(id, id, "http://x/$id", "", tags, "IT", 128)

    @Test fun playingWinsOverEverything() {
        val pick = RadioHero.pick(s("a"), listOf(s("b")), listOf(s("c")), listOf(s("d")))!!
        assertEquals("a", pick.station.id)
        assertEquals(RadioHero.Why.ON_AIR, pick.why)
    }

    @Test fun thenFavoriteRecentPopular() {
        assertEquals(RadioHero.Why.FAVORITE, RadioHero.pick(null, listOf(s("b")), listOf(s("c")), listOf(s("d")))!!.why)
        assertEquals(RadioHero.Why.RECENT, RadioHero.pick(null, emptyList(), listOf(s("c")), listOf(s("d")))!!.why)
        assertEquals("d", RadioHero.pick(null, emptyList(), emptyList(), listOf(s("d")))!!.station.id)
        assertNull(RadioHero.pick(null, emptyList(), emptyList(), emptyList()))
    }

    @Test fun detailShowsTrackOnlyWhileOnAir() {
        assertEquals("Vasco - Albachiara", RadioHero.detail(RadioHero.Why.ON_AIR, "Vasco - Albachiara", ""))
        assertEquals("Radio", RadioHero.detail(RadioHero.Why.FAVORITE, "Vasco - Albachiara", ""))
        assertEquals("Radio", RadioHero.detail(RadioHero.Why.ON_AIR, " ", ""))
    }
}
