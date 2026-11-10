package com.platinum20.dashboard

import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.weather.HourlyPoint
import com.platinum20.dashboard.widget.NewsWidgetRows
import com.platinum20.dashboard.widget.SavedStory
import com.platinum20.dashboard.widget.SimWidgetRows
import com.platinum20.dashboard.widget.WidgetRows
import com.platinum20.dashboard.widget.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** Le parti pure dei widget (org.json vuole Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WidgetRowsTest {
    @Test
    fun meteo() {
        assertEquals("Sereno · 22°/10°", WidgetRows.weatherLine("Sereno", "22°", "10°", 10))
        assertEquals("Rovesci · 18°/9° · pioggia 60%", WidgetRows.weatherLine("Rovesci", "18°", "9°", 60))
        assertEquals("Nuvoloso", WidgetRows.weatherLine("Nuvoloso", null, null, null))
    }

    @Test
    fun dateDelWidget() {
        val day = LocalDate.of(2026, 11, 9)
        assertEquals("LUNEDÌ 9 NOVEMBRE", WidgetRows.dateLabel(day))
        assertEquals("Lunedì 9", WidgetRows.dayTitle(day))
    }

    @Test
    fun leProssimeOreUnaOgniDue() {
        val now = LocalDateTime.of(2026, 11, 9, 10, 20)
        val hourly = (0..12).map { HourlyPoint(now.withMinute(0).plusHours(it.toLong()), 10.0 + it, 1, 0, true) }
        val hours = WidgetRows.hours(hourly, now, count = 3)
        assertEquals(listOf(11, 13, 15), hours.map { it.time.hour })
        assertEquals(0, WidgetRows.hourColumns(WidgetSize(110, 110)))
        assertEquals(4, WidgetRows.hourColumns(WidgetSize(250, 200)))
    }

    @Test
    fun notizie() {
        val now = 10_000_000_000L
        val s = SavedStory("Titolo", "https://x", "ANSA", sources = 4, at = now - 2 * 3_600_000)
        assertEquals("ANSA · 4 fonti · 2 h fa", NewsWidgetRows.meta(s, now))
        assertEquals("ANSA", NewsWidgetRows.meta(s.copy(sources = 1, at = 0), now))
        assertEquals(listOf(s), NewsWidgetRows.decode(NewsWidgetRows.encode(listOf(s))))
        // Le vecchie storie salvate (prima della 1.362) avevano solo titolo, link e fonte.
        assertEquals(listOf(SavedStory("T", "l", "Post")), NewsWidgetRows.decode("""[{"title":"T","link":"l","source":"Post"}]"""))
        assertEquals(emptyList<SavedStory>(), NewsWidgetRows.decode("non json"))
        assertEquals("3 NUOVE", NewsWidgetRows.countLabel(3))
        assertEquals("1 NUOVA", NewsWidgetRows.countLabel(1))
        assertNull(NewsWidgetRows.countLabel(0))
        assertNull(NewsWidgetRows.countLabel(null))
        assertEquals(2, NewsWidgetRows.storiesForHeight(200))
    }

    @Test
    fun simGigaEBarra() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Collega la SIM", SimWidgetRows.model(null, linked = false, today).line1)
        val m = SimWidgetRows.model(
            SimStatus(data = Allowance(25.0, 100.0), credit = 3.2, renewal = today.plusDays(5)),
            linked = true,
            today,
        )
        assertEquals("25 GB", m.big)
        assertEquals("di 100 GB", m.of)
        assertEquals(250, m.progress)
        assertTrue(m.line1, m.line1.endsWith("tra 5 giorni"))
        assertTrue(m.line2, m.line2.contains("3,20"))
        assertEquals("∞", SimWidgetRows.model(SimStatus(data = Allowance(0.0, unlimited = true)), true, today).big)
    }
}
