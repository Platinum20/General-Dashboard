package com.platinum20.dashboard

import com.platinum20.dashboard.widget.WidgetRows
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRowsTest {
    @Test
    fun meteo() {
        assertEquals("Sereno · 22°/10°", WidgetRows.weatherLine("Sereno", "22°", "10°", 10))
        assertEquals("Rovesci · 18°/9° · pioggia 60%", WidgetRows.weatherLine("Rovesci", "18°", "9°", 60))
        assertEquals("Nuvoloso", WidgetRows.weatherLine("Nuvoloso", null, null, null))
    }

    @Test
    fun quanteNotizie() {
        assertEquals(3, WidgetRows.newsCount(0, hasTasks = false))
        assertEquals(2, WidgetRows.newsCount(1, hasTasks = false))
        assertEquals(2, WidgetRows.newsCount(0, hasTasks = true))
    }
}
