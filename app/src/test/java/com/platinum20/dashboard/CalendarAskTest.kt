package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarAsk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAskTest {
    @Test fun ilPrimoNoLasciaLaCard() = assertFalse(CalendarAsk.shouldHide(1))

    @Test fun alSecondoNoLaCardSiNasconde() = assertTrue(CalendarAsk.shouldHide(2))

    @Test fun ancheDopo() = assertTrue(CalendarAsk.shouldHide(5))
}
