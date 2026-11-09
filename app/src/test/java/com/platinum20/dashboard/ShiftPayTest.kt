package com.platinum20.dashboard

import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftPay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** La busta paga di Creators Pro, col formato vero (file di Antonio, 9/11/2026, vendite tolte). */
class ShiftPayTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val listUrl = "https://api.creatorspro.com/v1/sellers-wage/wages?startedAt=2026-01-01T00:00:00.000Z&endedAt=2026-12-31T23:59:59.999Z&memberId=m1"
    private val detailUrl = "https://api.creatorspro.com/v1/sellers-wage/wages/8e372039-5a3d-4fba-b490-8c85ff9b1c0f"

    private val list = """{"message":"success","statusCode":200,"data":[{"status":"NEEDS_REVIEW","chargebacks":[],"info":{"id":"8e372039-5a3d-4fba-b490-8c85ff9b1c0f",
        "startedAt":"2026-11-01T00:00:00.000Z","status":"OPEN","totalAttributedFromTakes":5214.73,"totalEarningsFromTakes":747.04,"memberName":"Antonio Nesca",
        "endedAt":"2026-11-30T23:59:59.999Z","totalWorkedShifts":7,"totalWorkedHours":36.93,"totalEarningsFromHours":0,"totalWage":747.04},
        "settings":{"guaranteed":604,"guaranteedShiftThreshold":19}}]}"""

    private val detail = """{"message":"success","statusCode":200,"data":{"shifts":[
        {"id":"c2d02b8a","startedAt":"2026-11-01T16:00:00.000Z","endedAt":"2026-11-01T21:00:00.000Z","isActive":false,"isCurrent":false,
         "totalAttributed":504.69,"totalEarnings":60.56,"workedHours":5.41,"hourlyEarnings":0,
         "thresholds":[{"threshold":0,"percentage":0.1,"isActive":false},{"threshold":400,"percentage":0.12,"isActive":true},{"threshold":650,"percentage":0.15,"isActive":false}],
         "takes":[]},
        {"id":"d9","startedAt":"2026-11-09T11:00:00.000Z","endedAt":"2026-11-09T16:00:00.000Z","isCurrent":true,
         "totalAttributed":1486.6,"totalEarnings":222.99,"workedHours":5.3,"thresholds":[{"threshold":650,"percentage":0.15,"isActive":true}]}]}}"""

    @Test
    fun turniDellaBustaPaga() {
        val pay = ShiftPay.shifts(detail, detailUrl)!!
        assertEquals(2, pay.size)
        assertEquals(60.56, pay[0].earned, 0.001)
        assertEquals(0.12, pay[0].percent, 0.0001)
        assertEquals("$60,56 · 12%", ShiftPay.line(pay[0]))
        assertEquals("$222,99 · 15% finora", ShiftPay.line(pay[1]))
        assertNull(ShiftPay.shifts(detail, listUrl))
    }

    @Test
    fun meseDellaBustaPaga() {
        val m = ShiftPay.months(list, listUrl)!!.single()
        assertEquals(747.04, m.total, 0.001)
        assertEquals(7, m.shifts)
        assertEquals("Novembre: $747,04 in 7 turni", ShiftPay.monthLine(m, zone))
        assertEquals(m, ShiftPay.monthOf(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), listOf(m)))
        assertNull(ShiftPay.months(list, detailUrl))
    }

    @Test
    fun turnoDelCalendarioTrovaLaSuaBustaPaga() {
        val pay = ShiftPay.shifts(detail, detailUrl)!!
        val start = Instant.parse("2026-11-01T16:00:00Z").toEpochMilli()
        assertEquals("c2d02b8a", ShiftPay.of(Shift("altro-id", start, start + 5 * 3_600_000L), pay)?.id)
        assertEquals("d9", ShiftPay.of(Shift("d9", 0, 1), pay)?.id)
        assertNull(ShiftPay.of(Shift("x", start + 6 * 3_600_000L, start + 11 * 3_600_000L), pay))
    }

    @Test
    fun bustaPagaSalvataERiletta() {
        val pay = ShiftPay.shifts(detail, detailUrl)!!
        assertEquals(pay, ShiftPay.fromJson(ShiftPay.toJson(pay)))
        val months = ShiftPay.months(list, listUrl)!!
        assertEquals(months, ShiftPay.monthsFromJson(ShiftPay.monthsToJson(months)))
        assertEquals(pay, ShiftPay.mergeShifts(pay.take(1), pay))
    }
}
