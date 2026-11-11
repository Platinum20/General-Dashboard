package com.platinum20.dashboard

import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.ui.screens.RowDot
import com.platinum20.dashboard.ui.screens.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Il pallino colorato per tipo al posto dell'icona nelle righe del calendario (Antonio, 2026-11-11). */
class RowDotTest {
    @Test fun eventKeepsItsCalendarColor() {
        val e = TimelineEntry("e", BriefKind.EVENT, "Cena", null, color = 0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), e.dotColor())
    }

    @Test fun eventWithoutColorUsesTheEventDot() {
        assertEquals(RowDot.EVENT.argb(0), TimelineEntry("e", BriefKind.EVENT, "Cena", null).dotColor())
    }

    @Test fun otherKindsIgnoreTheCalendarColor() {
        val train = TimelineEntry("t", BriefKind.EVENT, "Treno", null, color = 0xFF112233.toInt(), dot = RowDot.TRAIN)
        assertEquals(RowDot.TRAIN.argb(0), train.dotColor())
    }

    @Test fun kindsMapToTheirDot() {
        assertEquals(RowDot.TASK, RowDot.of(BriefKind.TASK))
        assertEquals(RowDot.DATE, RowDot.of(BriefKind.DATE))
        assertEquals(RowDot.HABIT, RowDot.of(BriefKind.HABIT))
        assertEquals(RowDot.NOTE, RowDot.of(BriefKind.FREE))
    }

    @Test fun everyTypeHasItsOwnColor() {
        val colors = RowDot.entries.map { it.argb(0) }
        assertEquals(colors.size, colors.toSet().size)
        assertNotEquals(RowDot.TASK.argb(0), RowDot.NOTE.argb(0))
    }
}
