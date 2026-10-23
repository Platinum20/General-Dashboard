package com.platinum20.dashboard

import com.platinum20.dashboard.weather.Freshness
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreshnessTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 10, 23, 14, 30)
    private fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test fun appenaAggiornatoNonDiceNiente() = assertNull(Freshness.label(at(now.minusSeconds(90)), now, zone))
    @Test fun minutiFa() = assertEquals("5 min fa", Freshness.label(at(now.minusMinutes(5)), now, zone))
    @Test fun oggiPiuDiUnOraFa() = assertEquals("alle 9:05", Freshness.label(at(now.withHour(9).withMinute(5)), now, zone))
    @Test fun ieri() = assertEquals("ieri 18:20", Freshness.label(at(LocalDateTime.of(2026, 10, 22, 18, 20)), now, zone))
    @Test fun piuVecchio() = assertEquals("20/10", Freshness.label(at(LocalDateTime.of(2026, 10, 20, 8, 0)), now, zone))
    @Test fun maiScaricato() = assertNull(Freshness.label(0L, now, zone))
}
