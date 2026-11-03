package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtRatings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtRatingsTest {
    @Test
    fun nonMiPiaceSiMetteInCimaESiToglie() {
        val list = YtRatings.toggled(listOf("a"), "b")
        assertEquals(listOf("b", "a"), list)
        assertEquals(listOf("a"), YtRatings.toggled(list, "b"))
    }

    @Test
    fun iscrizioneToccataOraVinceSullaLista() {
        val channels = listOf(YtChannel("c1", "Uno", ""))
        assertTrue(YtRatings.isSubscribed("c1", emptyMap(), channels))
        assertFalse(YtRatings.isSubscribed("c2", emptyMap(), channels))
        assertFalse(YtRatings.isSubscribed("c1", mapOf("c1" to false), channels))
        assertTrue(YtRatings.isSubscribed("c2", mapOf("c2" to true), null))
    }
}
