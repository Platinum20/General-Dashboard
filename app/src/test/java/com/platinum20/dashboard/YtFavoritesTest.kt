package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtFavorites
import com.platinum20.dashboard.media.youtube.YtVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class YtFavoritesTest {
    private fun v(id: String) = YtVideo(id, "Titolo $id", "Canale", "", null)

    @Test
    fun miPiaceSiMetteInCimaESiToglie() {
        val list = YtFavorites.toggled(listOf(v("a")), v("b"))
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(listOf("a"), YtFavorites.toggled(list, v("b")).map { it.id })
    }
}
